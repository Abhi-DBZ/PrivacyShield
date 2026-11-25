package com.privacyshield.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Small app preferences, exposed as StateFlows for Compose and the shield service. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun int(key: String, def: Int) = MutableStateFlow(prefs.getInt(key, def))
    private fun bool(key: String, def: Boolean) = MutableStateFlow(prefs.getBoolean(key, def))
    private fun put(key: String, v: Int) = prefs.edit().putInt(key, v).apply()
    private fun put(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    // Appearance (DESIGN-GUIDE.md)
    private val _themeMode = int("themeMode", MODE_SYSTEM)
    private val _pureBlack = bool("pureBlack", false)
    private val _dynamicColor = bool("dynamicColor", true)
    private val _seedColor = int("seedColor", DEFAULT_SEED)
    val themeMode: StateFlow<Int> = _themeMode
    val pureBlack: StateFlow<Boolean> = _pureBlack
    val dynamicColor: StateFlow<Boolean> = _dynamicColor
    /** App colour (ARGB) used when [dynamicColor] is off or not available (Android < 12). */
    val seedColor: StateFlow<Int> = _seedColor
    fun setThemeMode(v: Int) { _themeMode.value = v; put("themeMode", v) }
    fun setPureBlack(v: Boolean) { _pureBlack.value = v; put("pureBlack", v) }
    fun setDynamicColor(v: Boolean) { _dynamicColor.value = v; put("dynamicColor", v) }
    /** Picks a fixed app colour (turns wallpaper colours off). */
    fun setSeedColor(v: Int) { _seedColor.value = v; put("seedColor", v); setDynamicColor(false) }

    // Protection
    private val _tilt = bool("tilt", true)
    private val _tiltAngle = int("tiltAngle", 30)
    private val _peek = bool("peek", false)
    private val _lookAway = bool("lookAway", false)
    private val _vibrate = bool("vibrate", true)
    private val _style = int("style", STYLE_BLACK)
    private val _dim = int("dim", 0)

    /** Hide the screen when the phone turns away from the owner's holding position. */
    val tilt: StateFlow<Boolean> = _tilt
    /** Degrees away from the owner's position before the screen hides (15…60). */
    val tiltAngle: StateFlow<Int> = _tiltAngle
    /** Front camera: hide when a second face is in view. */
    val peek: StateFlow<Boolean> = _peek
    /** Front camera: hide when no face is in view for a few seconds. */
    val lookAway: StateFlow<Boolean> = _lookAway
    /** Short buzz when someone else is seen. */
    val vibrate: StateFlow<Boolean> = _vibrate
    /** How the hidden screen looks: [STYLE_BLACK] or [STYLE_BLUR]. */
    val style: StateFlow<Int> = _style
    /** Always-on dim filter, percent (0…[MAX_DIM]). */
    val dim: StateFlow<Int> = _dim

    fun setTilt(v: Boolean) { _tilt.value = v; put("tilt", v) }
    fun setTiltAngle(v: Int) { _tiltAngle.value = v; put("tiltAngle", v) }
    fun setPeek(v: Boolean) { _peek.value = v; put("peek", v) }
    fun setLookAway(v: Boolean) { _lookAway.value = v; put("lookAway", v) }
    fun setVibrate(v: Boolean) { _vibrate.value = v; put("vibrate", v) }
    fun setStyle(v: Int) { _style.value = v; put("style", v) }
    fun setDim(v: Int) { _dim.value = v.coerceIn(0, MAX_DIM); put("dim", _dim.value) }

    val usesCamera get() = _peek.value || _lookAway.value

    // Where (feature 1: only in chosen apps)
    private val _onlyChosen = bool("onlyChosen", false)
    private val _chosenApps = MutableStateFlow(prefs.getStringSet("chosenApps", emptySet())!!.toSet())
    /** Shield works only while one of [chosenApps] is on screen. */
    val onlyChosen: StateFlow<Boolean> = _onlyChosen
    val chosenApps: StateFlow<Set<String>> = _chosenApps
    fun setOnlyChosen(v: Boolean) { _onlyChosen.value = v; put("onlyChosen", v) }
    fun setApp(pkg: String, on: Boolean) {
        val next = if (on) _chosenApps.value + pkg else _chosenApps.value - pkg
        _chosenApps.value = next
        prefs.edit().putStringSet("chosenApps", next).apply()
    }

    // Reading window (feature 2)
    private val _reading = bool("reading", false)
    private val _bandHeight = int("bandHeight", 30)
    private val _bandCenter = MutableStateFlow(prefs.getFloat("bandCenter", 0.45f))
    /** Darken everything except a strip the user can drag. */
    val reading: StateFlow<Boolean> = _reading
    /** Strip height, percent of the screen (15…50). */
    val bandHeight: StateFlow<Int> = _bandHeight
    /** Strip centre, fraction of the screen height from the top. */
    val bandCenter: StateFlow<Float> = _bandCenter
    fun setReading(v: Boolean) { _reading.value = v; put("reading", v) }
    fun setBandHeight(v: Int) { _bandHeight.value = v; put("bandHeight", v) }
    fun setBandCenter(v: Float) { _bandCenter.value = v; prefs.edit().putFloat("bandCenter", v).apply() }

    // Fake screen (feature 3): STYLE_CLOCK / STYLE_PICTURE; the picture is a private copy.
    val pictureFile = File(context.filesDir, "fake_screen.jpg")
    private val _pictureVersion = int("pictureVersion", 0)
    /** Bumped whenever [pictureFile] is replaced, so the overlay reloads it. */
    val pictureVersion: StateFlow<Int> = _pictureVersion
    fun pictureChanged() { _pictureVersion.value += 1; put("pictureVersion", _pictureVersion.value) }

    // Landscape (feature 4)
    private val _landscapePause = bool("landscapePause", true)
    /** Tilt guard rests while the screen is in landscape (videos, games). */
    val landscapePause: StateFlow<Boolean> = _landscapePause
    fun setLandscapePause(v: Boolean) { _landscapePause.value = v; put("landscapePause", v) }

    companion object {
        /** Indigo, matches the icon. */
        const val DEFAULT_SEED = 0xFF3D4FC4.toInt()
        const val MODE_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
        const val STYLE_BLACK = 0
        const val STYLE_BLUR = 1
        /** Fake screen: a big clock + date, like an always-on display. */
        const val STYLE_CLOCK = 2
        /** Fake screen: a picture the user chose. */
        const val STYLE_PICTURE = 3
        /** Android 12+ blocks touches through overlays more opaque than 80%. */
        const val MAX_DIM = 75
    }
}
