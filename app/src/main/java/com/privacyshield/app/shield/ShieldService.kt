package com.privacyshield.app.shield

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.privacyshield.app.MainActivity
import com.privacyshield.app.R
import com.privacyshield.app.app
import com.privacyshield.app.data.CrashLog
import com.privacyshield.app.data.Settings
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Runs while the shield is on: keeps the overlay window, the tilt sensor and (optionally) the
 * front camera going, and decides from them whether the screen should be hidden.
 * Sensors and camera pause while the screen is off, while an app that isn't on the chosen list is
 * open (if "only in chosen apps" is on), and the tilt guard rests in landscape.
 */
class ShieldService : LifecycleService() {
    private val s: Settings by lazy { app.settings }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: ShieldOverlay
    private lateinit var tilt: TiltGuard
    private lateinit var watcher: AppWatcher
    /** Created only when a camera feature is on (loads CameraX + ML Kit). */
    private var peek: PeekDetector? = null

    private var started = false
    private var cameraTypeOn = false
    private var screenOn = true
    private var landscape = false

    private var angle: Float? = null
    private var tiltAway = false
    private var faces: Int? = null
    private var peekHits = 0
    private var peekMisses = 0
    private var peeking = false
    private var noFaceSince = 0L
    private var away = false
    private var snoozeUntil = 0L
    private var lastHidden: Reason? = null
    private var cameraProblem: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; safeApply() }
                Intent.ACTION_SCREEN_ON -> { screenOn = true; safeApply() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        overlay = ShieldOverlay(this, s.pictureFile, onSnooze = ::snooze, onRecenter = ::recenter,
            onBandMoved = { s.setBandCenter(it) })
        tilt = TiltGuard(this) { a -> angle = a; evaluate() }
        watcher = AppWatcher(this)
        ContextCompat.registerReceiver(this, screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) },
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            Shield.ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            Shield.ACTION_RECENTER -> if (started) recenter()
            Shield.ACTION_SNOOZE -> if (started) snooze()
            else -> try {
                goForeground()
                if (!Shield.canDraw(this)) { stopSelf(); return START_NOT_STICKY }
                if (!started) {
                    started = true
                    overlay.show()
                    lifecycleScope.launch {
                        merge(
                            s.tilt, s.tiltAngle, s.peek, s.lookAway, s.style, s.dim, s.vibrate,
                            s.onlyChosen, s.chosenApps, s.reading, s.bandHeight, s.bandCenter,
                            s.pictureVersion, s.landscapePause, app.foregroundApp, app.watcherOn,
                        ).collect { safeApply() }
                    }
                    Shield.refreshTile(this)
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::overlay.isInitialized) return
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        overlay.fitScreen()
        safeApply()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        tilt.stop()
        watcher.stop()
        runCatching { peek?.close() }
        overlay.hide()
        app.live.value = Live()
        Shield.refreshTile(this)
        super.onDestroy()
    }

    /** Something went wrong while starting: keep the report for the home screen and stop cleanly. */
    private fun fail(e: Throwable) {
        CrashLog.write(this, e, "The shield could not start")
        stopSelf()
    }

    // ---------------------------------------------------------------- foreground

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** (Re)declares the foreground service, with the camera type only when it is needed. */
    private fun goForeground() {
        val wantCam = s.usesCamera && hasCameraPermission()
        var base = 0
        if (Build.VERSION.SDK_INT >= 34) base = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        val cam = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        try {
            ServiceCompat.startForeground(this, NOTE_ID, notification(), if (wantCam) base or cam else base)
            cameraTypeOn = wantCam
        } catch (e: Exception) {
            if (!wantCam) throw e
            // Android refuses camera access to services started from the background.
            ServiceCompat.startForeground(this, NOTE_ID, notification(), base)
            cameraTypeOn = false
            cameraProblem = "Open Privacy Shield once to start the camera"
        }
    }

    // ---------------------------------------------------------------- inputs

    /** Only-in-chosen-apps: is a chosen app on screen? (Everywhere when detection is off.) */
    private fun activeHere(): Boolean =
        !s.onlyChosen.value || !app.watcherOn.value || app.foregroundApp.value in s.chosenApps.value

    private fun safeApply() {
        try { applySettings() } catch (e: Exception) { fail(e) }
    }

    private fun applySettings() {
        if (!started) return
        // Watch which app is open only when it matters (and not with the screen off).
        if (s.onlyChosen.value && screenOn) watcher.start() else watcher.stop()
        val active = activeHere() && screenOn
        val tiltResting = landscape && s.landscapePause.value
        // Starting again (new app, back to portrait, screen on) takes the current hold as "yours".
        if (s.tilt.value && active && !tiltResting) tilt.start() else { tilt.stop(); tiltAway = false }

        when {
            !s.usesCamera -> { stopCamera(); cameraProblem = null }
            !hasCameraPermission() -> { stopCamera(); cameraProblem = "Camera permission is off" }
            else -> {
                if (!cameraTypeOn) goForeground()
                if (cameraTypeOn && active) {
                    val p = peek ?: try {
                        PeekDetector(this, this, ::onFaces) { msg -> cameraProblem = msg; evaluate() }.also { peek = it }
                    } catch (e: Throwable) {
                        CrashLog.write(this, e, "Face detection could not start")
                        cameraProblem = "Face detection could not start (see report)"
                        null
                    }
                    p?.start()
                } else stopCamera()
            }
        }
        // Keep the notification's feature list current.
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTE_ID, notification()) }
        evaluate()
    }

    private fun stopCamera() {
        peek?.stop()
        faces = null; peeking = false; away = false; peekHits = 0; peekMisses = 0; noFaceSince = 0L
    }

    private fun onFaces(n: Int) {
        faces = n
        val now = SystemClock.elapsedRealtime()
        // Two frames in a row with 2+ faces → someone is looking; three without → gone again.
        if (n >= 2) { peekHits++; peekMisses = 0; if (peekHits >= 2) peeking = true }
        else { peekMisses++; peekHits = 0; if (peekMisses >= 3) peeking = false }
        if (n == 0) {
            if (noFaceSince == 0L) noFaceSince = now
            away = now - noFaceSince > AWAY_MS
            if (!away && s.lookAway.value) handler.postDelayed({ evaluate() }, AWAY_MS + 50)
        } else { noFaceSince = 0L; away = false }
        evaluate()
    }

    private fun snooze() {
        snoozeUntil = SystemClock.elapsedRealtime() + SNOOZE_MS
        handler.postDelayed({ evaluate() }, SNOOZE_MS + 50)
        evaluate()
    }

    private fun recenter() {
        tilt.recenter()
        tiltAway = false
        evaluate()
    }

    // ---------------------------------------------------------------- decision

    private fun evaluate() {
        if (!started) return
        val now = SystemClock.elapsedRealtime()
        val active = activeHere() && screenOn
        val a = angle
        val limit = s.tiltAngle.value
        tiltAway = when {
            !s.tilt.value || a == null -> false
            tiltAway -> a > limit - HYSTERESIS   // stay hidden until clearly back
            else -> a > limit
        }
        val awayNow = away && noFaceSince != 0L && now - noFaceSince > AWAY_MS
        val reason = when {
            !active || now < snoozeUntil -> null
            peeking && s.peek.value -> Reason.PEEK
            tiltAway -> Reason.TILT
            awayNow && s.lookAway.value -> Reason.AWAY
            else -> null
        }
        if (reason == Reason.PEEK && lastHidden != Reason.PEEK && s.vibrate.value) buzz()
        lastHidden = reason

        val look = if (!active) Look() else Look(
            dim = s.dim.value,
            hidden = reason,
            style = s.style.value,
            band = if (s.reading.value) s.bandCenter.value to s.bandHeight.value / 100f else null,
            pictureVersion = s.pictureVersion.value,
        )
        overlay.render(look, recenterAllowed = reason == Reason.TILT)
        app.live.update {
            Live(
                running = true,
                tiltDeg = if (s.tilt.value) a?.roundToInt()?.toFloat() else null,
                faces = faces,
                hiddenBy = reason,
                snoozedUntil = if (now < snoozeUntil) snoozeUntil else 0L,
                cameraProblem = if (s.usesCamera) cameraProblem else null,
                activeHere = activeHere(),
                tiltResting = s.tilt.value && landscape && s.landscapePause.value,
            )
        }
    }

    private fun buzz() {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        runCatching { v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 90, 60), -1)) }
    }

    // ---------------------------------------------------------------- notification

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Shield running", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Privacy Shield is on"
                setShowBadge(false)
            },
        )
    }

    private fun notification(): android.app.Notification {
        val n = s.chosenApps.value.size
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (s.onlyChosen.value) "Privacy Shield is on in $n app${if (n == 1) "" else "s"}" else "Privacy Shield is on")
            .setContentText(
                listOfNotNull(
                    "Tilt guard".takeIf { s.tilt.value },
                    "Peek alert".takeIf { s.peek.value },
                    "Look-away".takeIf { s.lookAway.value },
                    "Reading window".takeIf { s.reading.value },
                    "Dim ${s.dim.value}%".takeIf { s.dim.value > 0 },
                ).joinToString(" · ").ifEmpty { "Nothing switched on" },
            )
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, "Recenter", action(Shield.ACTION_RECENTER, 1))
            .addAction(0, "Turn off", action(Shield.ACTION_STOP, 2))
            .build()
    }

    private fun action(a: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, ShieldService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL = "shield"
        const val NOTE_ID = 7
        const val HYSTERESIS = 8
        const val SNOOZE_MS = 30_000L
        const val AWAY_MS = 3_000L
    }
}
