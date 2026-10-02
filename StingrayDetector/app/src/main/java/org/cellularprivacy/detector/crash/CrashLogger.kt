package org.cellularprivacy.detector.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Debug-only crash sensor. Installs a default uncaught-exception handler that
 * writes the stack trace (any thread) to a file, then chains to the previous
 * handler so the OS still terminates the app normally. The next launch can read
 * and show the trace. Gated by BuildConfig.CRASH_LOGGER so it is compiled out of
 * the real release by flipping that flag to false.
 */
object CrashLogger {

    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val report = buildString {
                    appendLine("== Stingray Fuzz crash ==")
                    appendLine("time   : ${Date()}")
                    appendLine("thread : ${thread.name}")
                    appendLine(
                        "device : ${Build.MANUFACTURER} ${Build.MODEL}, " +
                            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    )
                    appendLine()
                    append(sw.toString())
                }
                File(app.filesDir, FILE).writeText(report)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readLast(context: Context): String? {
        val f = File(context.applicationContext.filesDir, FILE)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }
}
