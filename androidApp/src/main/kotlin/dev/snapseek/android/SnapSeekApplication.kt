package dev.snapseek.android

import android.app.Application
import dev.snapseek.android.platform.CrashLog

/** Installed before anything else runs, so a crash anywhere in the app leaves a report behind. */
class SnapSeekApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }

    /** When the system is short of memory, the thumbnails are the first thing worth giving back. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) AndroidGraph.of(this).images.trim(aggressive = level >= TRIM_MEMORY_COMPLETE)
    }
}
