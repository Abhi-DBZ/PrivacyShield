package com.privacyshield.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.privacyshield.app.shield.Shield

/** Quick Settings tile: turns the shield on/off. */
class ShieldTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val on = app.live.value.running
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = if (on) "On" else "Off"
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (app.live.value.running) {
            Shield.stop(this)
            qsTile?.apply { state = Tile.STATE_INACTIVE; if (Build.VERSION.SDK_INT >= 29) subtitle = "Off"; updateTile() }
            return
        }
        // Starting goes through an invisible activity: Android only lets a visible app start
        // a service that uses the camera.
        val intent = Intent(this, ToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }
}
