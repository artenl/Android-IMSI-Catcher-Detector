package org.cellularprivacy.detector

import android.app.Application
import org.cellularprivacy.detector.crash.CrashLogger

/** Application entry point. Installs the debug crash sensor when enabled. */
class StingrayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.CRASH_LOGGER) CrashLogger.install(this)
    }
}
