package com.privacyshield.app.shield

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.privacyshield.app.ShieldTile
import com.privacyshield.app.data.CrashLog

/** Why the screen is hidden. */
enum class Reason { TILT, PEEK, AWAY }

/** Snapshot of the running shield. */
data class Live(
    val running: Boolean = false,
    /** Degrees the phone is turned away from the owner's position (null = not measured yet / off). */
    val tiltDeg: Float? = null,
    /** Faces in the last camera frame (null = camera off). */
    val faces: Int? = null,
    val hiddenBy: Reason? = null,
    val snoozedUntil: Long = 0L,
    /** Set when the camera features are on but the camera can't be used. */
    val cameraProblem: String? = null,
    /** False while "only in chosen apps" is on and another app is open. */
    val activeHere: Boolean = true,
    /** Tilt guard resting because the screen is in landscape. */
    val tiltResting: Boolean = false,
)

/** Start/stop helpers used by the UI, the tile and the notification. */
object Shield {
    const val ACTION_START = "com.privacyshield.START"
    const val ACTION_RECENTER = "com.privacyshield.RECENTER"
    const val ACTION_SNOOZE = "com.privacyshield.SNOOZE"
    const val ACTION_STOP = "com.privacyshield.STOP"

    fun canDraw(ctx: Context) = Settings.canDrawOverlays(ctx)

    /** Call only while the app is in the foreground (activity visible). */
    fun start(ctx: Context) {
        if (!canDraw(ctx)) return
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ShieldService::class.java).setAction(ACTION_START))
        } catch (e: Exception) {
            CrashLog.write(ctx, e, "Android refused to start the shield")
        }
    }

    fun stop(ctx: Context) {
        ctx.stopService(Intent(ctx, ShieldService::class.java))
    }

    fun send(ctx: Context, action: String) {
        ctx.startService(Intent(ctx, ShieldService::class.java).setAction(action))
    }

    fun refreshTile(ctx: Context) {
        runCatching { TileService.requestListeningState(ctx, ComponentName(ctx, ShieldTile::class.java)) }
    }
}
