package com.privacyshield.app.shield

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.privacyshield.app.app

/**
 * Tells the shield which app is on screen, so it can work only in chosen apps.
 *
 * Uses Usage access (the system's app-usage history), polled about twice a second while the
 * shield needs it. An accessibility service would be instant, but Play Protect blocks sideloaded
 * apps that declare one ("App blocked to protect your device"), so v1.1.1 switched to this.
 */
class AppWatcher(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastQuery = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            poll()
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastQuery = 0L
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun poll() {
        val granted = granted(context)
        context.app.watcherOn.value = granted
        if (!granted) return
        val now = System.currentTimeMillis()
        // First look: the last hour, to learn which app is open right now. After that, only new events.
        val from = if (lastQuery == 0L) now - 60 * 60_000L else lastQuery - 1_000L
        lastQuery = now
        val events = runCatching { usm.queryEvents(from, now) }.getOrNull() ?: return
        var latest: String? = null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == RESUMED) latest = e.packageName
        }
        if (latest != null) context.app.foregroundApp.value = latest
    }

    companion object {
        private const val POLL_MS = 500L
        /** ACTIVITY_RESUMED (API 29+); same value as the older MOVE_TO_FOREGROUND. */
        private const val RESUMED = 1

        /** Whether the user has given Privacy Shield Usage access. */
        fun granted(ctx: Context): Boolean {
            val ops = ctx.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= 29)
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            return if (mode == AppOpsManager.MODE_DEFAULT)
                ctx.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
