package com.privacyshield.app

import android.app.Application
import android.content.Context
import com.privacyshield.app.data.CrashLog
import com.privacyshield.app.data.Settings
import com.privacyshield.app.shield.Live
import kotlinx.coroutines.flow.MutableStateFlow

class ShieldApp : Application() {
    lateinit var settings: Settings
        private set

    /** What the running shield sees right now (shown live on the home screen). */
    val live = MutableStateFlow(Live())

    /** Package of the app on screen, reported by [com.privacyshield.app.shield.AppWatcher]. */
    val foregroundApp = MutableStateFlow<String?>(null)

    /** True while the app-detection accessibility service is connected. */
    val watcherOn = MutableStateFlow(false)

    /** Last crash / start-up failure report, shown on the home screen until dismissed. */
    val problem = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        problem.value = CrashLog.read(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { CrashLog.write(this, e, "Privacy Shield stopped (thread ${thread.name})") }
            previous?.uncaughtException(thread, e)
        }
    }
}

val Context.app: ShieldApp get() = applicationContext as ShieldApp
