package dev.snapseek.android

import android.app.Application
import dev.snapseek.android.platform.CrashLog

/** Installed before anything else runs, so a crash anywhere in the app leaves a report behind. */
class SnapSeekApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
