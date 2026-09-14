package dev.snapseek.core.settings

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where SnapSeek keeps its files. Data (settings, history) is separate from cache (Chromium profile, CEF natives)
 * so users can wipe the cache without losing their setup.
 */
class AppPaths(val dataDir: Path, val cacheDir: Path) {
    val settingsFile: Path get() = dataDir.resolve("settings.json")
    val historyDb: Path get() = dataDir.resolve("snapseek.db")
    val cefInstallDir: Path get() = cacheDir.resolve("jcef")
    val cefCacheDir: Path get() = cacheDir.resolve("cef")
    val logDir: Path get() = cacheDir.resolve("logs")

    companion object {
        private val home: Path get() = Paths.get(System.getProperty("user.home"))

        val systemDownloads: Path get() = home.resolve("Downloads")

        val isWindows: Boolean get() = System.getProperty("os.name").lowercase().contains("win")
        val isMac: Boolean get() = System.getProperty("os.name").lowercase().contains("mac")

        fun forCurrentOs(appName: String = "SnapSeek"): AppPaths = when {
            isWindows -> {
                val roaming = System.getenv("APPDATA")?.let(Paths::get) ?: home.resolve("AppData/Roaming")
                val local = System.getenv("LOCALAPPDATA")?.let(Paths::get) ?: home.resolve("AppData/Local")
                AppPaths(roaming.resolve(appName), local.resolve(appName))
            }
            isMac -> AppPaths(
                home.resolve("Library/Application Support").resolve(appName),
                home.resolve("Library/Caches").resolve(appName),
            )
            else -> {
                val data = System.getenv("XDG_DATA_HOME")?.let(Paths::get) ?: home.resolve(".local/share")
                val cache = System.getenv("XDG_CACHE_HOME")?.let(Paths::get) ?: home.resolve(".cache")
                AppPaths(data.resolve(appName.lowercase()), cache.resolve(appName.lowercase()))
            }
        }
    }
}
