package com.privacyshield.app.shield

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.privacyshield.app.data.Settings
import java.io.File

/** Everything the overlay needs to draw one state. */
data class Look(
    /** Always-on dim filter, percent. */
    val dim: Int = 0,
    /** Null = screen visible. */
    val hidden: Reason? = null,
    val style: Int = Settings.STYLE_BLACK,
    /** Reading window: centre and height as fractions of the screen; null = off. */
    val band: Pair<Float, Float>? = null,
    val pictureVersion: Int = 0,
)

/**
 * Full-screen window(s) drawn over every app.
 * - Visible: see-through, the dim filter, or the reading window (dark except a strip); never
 *   touchable. Android 12+ ignores taps through overlays more than 80% opaque, so all of these use
 *   the window alpha (what Android checks) and stay at or below [READ_ALPHA].
 * - Hidden: black / blur / clock / picture, touchable. Double-tap = show for 30 s,
 *   long-press = "this is how I hold it" (tilt only).
 * The reading window also gets a small draggable handle in a second window at the right edge.
 */
class ShieldOverlay(
    private val context: Context,
    private val pictureFile: File,
    private val onSnooze: () -> Unit,
    private val onRecenter: () -> Unit,
    private val onBandMoved: (Float) -> Unit,
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val root = MaskView(context)
    private val panel = LinearLayout(context)
    private val message = TextView(context)
    private val recenterButton: TextView
    private val clock = LinearLayout(context)
    private val picture = ImageView(context)
    private val handle = TextView(context)
    private var attached = false
    private var handleAttached = false
    private var last: Look? = null
    private var screenH = 1
    private var loadedPicture = -1
    private var canRecenter = false

    private val params = overlayParams(BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
        gravity = Gravity.TOP or Gravity.START
        alpha = 0f
    }
    private val handleParams = overlayParams(BASE_FLAGS).apply {
        gravity = Gravity.TOP or Gravity.END
        width = dp(30); height = dp(72); x = dp(2)
    }

    init {
        // Hidden screen: message + buttons (black / blur).
        panel.orientation = LinearLayout.VERTICAL
        panel.gravity = Gravity.CENTER_HORIZONTAL
        message.setTextColor(Color.WHITE)
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        message.gravity = Gravity.CENTER
        panel.addView(message, LinearLayout.LayoutParams(dp(300), LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(button("Show for 30 s") { onSnooze() })
        recenterButton = button("This is how I hold it") { onRecenter() }
        panel.addView(recenterButton)
        panel.addView(TextView(context).apply {
            text = "Tip: double-tap anywhere to show for 30 s"
            setTextColor(0x99FFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22) })

        // Fake screen: clock + date on a dark gradient, like an always-on display.
        clock.orientation = LinearLayout.VERTICAL
        clock.gravity = Gravity.CENTER
        clock.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF0B0F1A.toInt(), 0xFF1C2740.toInt()))
        clock.addView(TextClock(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 76f)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        })
        clock.addView(TextClock(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"
            setTextColor(0xCCFFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        })
        clock.setPadding(0, 0, 0, dp(120))

        // Fake screen: the user's picture.
        picture.scaleType = ImageView.ScaleType.CENTER_CROP

        root.addView(picture, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(clock, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        panel.visibility = View.GONE; clock.visibility = View.GONE; picture.visibility = View.GONE

        val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean { onSnooze(); return true }
            override fun onLongPress(e: MotionEvent) { if (canRecenter) onRecenter() }
        })
        root.setOnTouchListener { _, e -> gestures.onTouchEvent(e); true }

        setupHandle()
    }

    fun show() {
        if (attached) return
        fitScreen()
        runCatching { wm.addView(root, params); attached = true }
    }

    fun hide() {
        if (attached) runCatching { wm.removeView(root) }
        if (handleAttached) runCatching { wm.removeView(handle) }
        attached = false; handleAttached = false; last = null
    }

    /** Re-measure after rotation. */
    fun fitScreen() {
        val (w, h) = screenSize()
        params.width = w; params.height = h; screenH = h
        if (attached) runCatching { wm.updateViewLayout(root, params) }
        last?.let { val l = it; last = null; render(l, canRecenter) }
    }

    fun render(look: Look, recenterAllowed: Boolean) {
        canRecenter = recenterAllowed
        if (!attached || look == last) return
        last = look
        val p = params
        val hidden = look.hidden
        if (hidden == null) {
            panel.visibility = View.GONE; clock.visibility = View.GONE; picture.visibility = View.GONE
            p.flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (Build.VERSION.SDK_INT >= 31) p.blurBehindRadius = 0
            val band = look.band
            if (band != null) {
                p.alpha = READ_ALPHA
                root.setBand(band.first, band.second, insideAlpha = (look.dim / 100f / READ_ALPHA).coerceIn(0f, 1f))
            } else {
                p.alpha = look.dim / 100f
                root.setFull(Color.BLACK)
            }
            showHandle(band)
        } else {
            showHandle(null)
            p.alpha = 1f
            p.flags = BASE_FLAGS
            if (Build.VERSION.SDK_INT >= 31) p.blurBehindRadius = 0
            var style = look.style
            if (style == Settings.STYLE_PICTURE && !loadPicture(look.pictureVersion)) style = Settings.STYLE_CLOCK
            val canBlur = Build.VERSION.SDK_INT >= 31 && wm.isCrossWindowBlurEnabled
            if (style == Settings.STYLE_BLUR && !canBlur) style = Settings.STYLE_BLACK
            panel.visibility = if (style == Settings.STYLE_BLACK || style == Settings.STYLE_BLUR) View.VISIBLE else View.GONE
            clock.visibility = if (style == Settings.STYLE_CLOCK) View.VISIBLE else View.GONE
            picture.visibility = if (style == Settings.STYLE_PICTURE) View.VISIBLE else View.GONE
            when (style) {
                Settings.STYLE_BLUR -> {
                    p.flags = p.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    if (Build.VERSION.SDK_INT >= 31) p.blurBehindRadius = 150
                    root.setFull(0xB8000000.toInt())
                }
                else -> root.setFull(Color.BLACK)
            }
            message.text = when (hidden) {
                Reason.TILT -> "Screen hidden\nThe phone turned away from you"
                Reason.PEEK -> "Screen hidden\nSomeone else is looking"
                Reason.AWAY -> "Screen hidden\nYou looked away"
            }
            recenterButton.visibility = if (hidden == Reason.TILT) View.VISIBLE else View.GONE
        }
        runCatching { wm.updateViewLayout(root, p) }
    }

    // ---------------------------------------------------------------- reading-window handle

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHandle() {
        handle.text = "≡"
        handle.setTextColor(Color.WHITE)
        handle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        handle.gravity = Gravity.CENTER
        handle.contentDescription = "Drag to move the reading window"
        handle.background = GradientDrawable().apply {
            cornerRadius = dp(15).toFloat(); setColor(0x99202436.toInt()); setStroke(dp(1), 0x66FFFFFF)
        }
        var downY = 0f; var startCentre = 0f
        handle.setOnTouchListener { _, e ->
            val band = last?.band ?: return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; startCentre = band.first }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val half = band.second / 2
                    val c = (startCentre + (e.rawY - downY) / screenH).coerceIn(half, 1f - half)
                    root.moveBand(c)
                    placeHandle(c)
                    if (e.actionMasked == MotionEvent.ACTION_UP) onBandMoved(c)
                }
            }
            true
        }
    }

    private fun showHandle(band: Pair<Float, Float>?) {
        if (band == null) {
            if (handleAttached) { runCatching { wm.removeView(handle) }; handleAttached = false }
            return
        }
        placeHandle(band.first)
        if (!handleAttached) runCatching { wm.addView(handle, handleParams); handleAttached = true }
    }

    private fun placeHandle(centre: Float) {
        handleParams.y = (centre * screenH - handleParams.height / 2f).toInt()
        if (handleAttached) runCatching { wm.updateViewLayout(handle, handleParams) }
    }

    // ---------------------------------------------------------------- helpers

    private fun loadPicture(version: Int): Boolean {
        if (version == loadedPicture && picture.drawable != null) return true
        if (!pictureFile.exists()) return false
        val bmp = runCatching { decodeScaled(pictureFile, screenSize()) }.getOrNull() ?: return false
        picture.setImageBitmap(bmp)
        loadedPicture = version
        return true
    }

    private fun decodeScaled(f: File, size: Pair<Int, Int>): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size.first && bounds.outHeight / (sample * 2) >= size.second) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun overlayParams(flags: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT,
    ).apply {
        title = "Privacy Shield"
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode =
            if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
    }

    private fun button(text: String, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        setPadding(dp(22), dp(12), dp(22), dp(12))
        background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0x33FFFFFF) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(18) }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds; b.width() to b.height()
        } else {
            val m = DisplayMetrics(); wm.defaultDisplay.getRealMetrics(m); m.widthPixels to m.heightPixels
        }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    /** Paints either the whole window, or everything except a horizontal strip. */
    private class MaskView(ctx: Context) : FrameLayout(ctx) {
        private val paint = Paint()
        private var fullColor: Int? = Color.BLACK
        private var bandC = 0.5f
        private var bandH = 0.3f
        private var inside = 0f

        init { setWillNotDraw(false) }

        fun setFull(color: Int) { fullColor = color; invalidate() }

        fun setBand(c: Float, h: Float, insideAlpha: Float) {
            fullColor = null; bandC = c; bandH = h; inside = insideAlpha; invalidate()
        }

        fun moveBand(c: Float) { bandC = c; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val full = fullColor
            if (full != null) { canvas.drawColor(full); return }
            val w = width.toFloat(); val h = height.toFloat()
            val top = (bandC - bandH / 2) * h; val bottom = (bandC + bandH / 2) * h
            paint.color = Color.BLACK
            canvas.drawRect(0f, 0f, w, top, paint)
            canvas.drawRect(0f, bottom, w, h, paint)
            // Soft edge so the strip doesn't look like a hard cut.
            paint.color = 0x55000000
            canvas.drawRect(0f, top, w, top + 6f, paint)
            canvas.drawRect(0f, bottom - 6f, w, bottom, paint)
            if (inside > 0f) {
                paint.color = Color.argb((inside * 255).toInt(), 0, 0, 0)
                canvas.drawRect(0f, top, w, bottom, paint)
            }
        }
    }

    private companion object {
        /** Darkest a see-through overlay may be (Android blocks touches above 0.8). */
        const val READ_ALPHA = 0.78f
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }
}
