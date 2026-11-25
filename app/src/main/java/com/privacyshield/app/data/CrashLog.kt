package com.privacyshield.app.data

import android.content.Context
import android.os.Build
import com.privacyshield.app.app
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash / start-up failure in a small file so the home screen can show it
 * (with Copy / Share) even when no PC is connected to read logcat.
 */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_problem.txt")

    fun write(ctx: Context, e: Throwable, title: String) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
        val text = buildString {
            appendLine(title)
            appendLine("Privacy Shield $version · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            appendLine()
            append(sw.toString().lineSequence().take(60).joinToString("\n"))
        }
        runCatching { file(ctx).writeText(text) }
        runCatching { ctx.app.problem.value = text }
    }

    fun read(ctx: Context): String? = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
        ctx.app.problem.value = null
    }
}
