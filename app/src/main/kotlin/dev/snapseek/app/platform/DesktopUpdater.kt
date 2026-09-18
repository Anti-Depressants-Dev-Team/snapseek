package dev.snapseek.app.platform

import dev.snapseek.core.settings.AppPaths
import dev.snapseek.core.settings.SettingsStore
import dev.snapseek.core.update.DownloadState
import dev.snapseek.core.update.Update
import dev.snapseek.core.update.UpdateChecker
import dev.snapseek.core.update.UpdateDownloader
import dev.snapseek.core.update.UpdateTarget
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Keeps the desktop app up to date without ever doing it behind someone's back: it looks once a day, says what it
 * found, and only downloads when asked. The file it fetches is checked against the checksum published with the
 * release before it is handed to the system installer, because that file is about to be run.
 */
class DesktopUpdater(
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val paths: AppPaths,
    private val currentVersion: String,
    private val checker: UpdateChecker = UpdateChecker(),
    private val downloader: UpdateDownloader = UpdateDownloader(),
) {
    private val log = KotlinLogging.logger {}

    private val _available = MutableStateFlow<Update?>(null)
    /** The update worth telling the user about, or null when there is nothing to say. */
    val available: StateFlow<Update?> = _available.asStateFlow()

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    /** What this machine can install, or null on a Linux flavour we ship no package for. */
    val target: UpdateTarget? = currentTarget()

    /** Called on start. Quiet: the user only hears about it if something newer exists. */
    fun checkOnStart() {
        if (!settings.current.checkForUpdates) return
        val since = System.currentTimeMillis() - settings.current.lastUpdateCheck
        if (since < ONCE_A_DAY) return
        check(userAsked = false)
    }

    /** Called from the settings screen, where silence would look broken, so this one always reports back. */
    fun check(userAsked: Boolean) {
        val where = target ?: run {
            if (userAsked) log.info { "No package is published for this system; updates are manual here" }
            return
        }
        scope.launch {
            val found = runCatching { checker.check(currentVersion, where) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            _available.value = when {
                found == null -> null
                !userAsked && found.version == settings.current.skippedVersion -> null
                else -> found
            }
        }
    }

    /** "Not this one." Asked again only when a later version appears. */
    fun skip(update: Update) {
        settings.update { it.copy(skippedVersion = update.version) }
        _available.value = null
    }

    fun dismiss() {
        _available.value = null
    }

    /**
     * Downloads the update and opens it with whatever the system uses to install such a file, then leaves: an
     * installer cannot replace files the running app still holds.
     */
    fun install(update: Update, quit: () -> Unit) {
        if (_download.value is DownloadState.Running) return
        scope.launch {
            _download.value = DownloadState.Running(0, update.sizeBytes)
            val result = runCatching {
                downloader.download(update, updatesDir().toFile()) { bytes, total ->
                    _download.value = DownloadState.Running(bytes, total)
                }
            }.onFailure { if (it is CancellationException) throw it }

            result.onSuccess { downloaded ->
                _download.value = DownloadState.Done(downloaded.file)
                log.info { "Downloaded ${downloaded.file} (checksum ${if (downloaded.checksumVerified) "verified" else "not published"})" }
                val opened = openWithSystem(downloaded.file)
                if (opened) {
                    // Give the installer a moment to take over before the window disappears under it.
                    delay(1200)
                    quit()
                } else {
                    _download.value = DownloadState.Failed("Downloaded to ${downloaded.file.parent}, but it couldn't be started from here")
                }
            }.onFailure {
                log.warn(it) { "The update could not be installed" }
                _download.value = DownloadState.Failed(it.message ?: "The download failed")
            }
        }
    }

    private suspend fun openWithSystem(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
                true
            } else {
                false
            }
        }.onFailure { log.warn(it) { "Couldn't hand $file to the system" } }.getOrDefault(false)
    }

    private fun updatesDir(): Path = paths.cacheDir.resolve("updates").also { if (!it.exists()) Files.createDirectories(it) }

    private companion object {
        const val ONCE_A_DAY = 24L * 60 * 60 * 1000

        /**
         * Which package this machine installs. Linux has no single answer, so the question is which packager the
         * distribution actually uses; anything else gets no offer rather than the wrong one.
         */
        fun currentTarget(): UpdateTarget? = when {
            AppPaths.isWindows -> UpdateTarget.WINDOWS_INSTALLER
            AppPaths.isMac -> UpdateTarget.MACOS
            File("/etc/debian_version").exists() -> UpdateTarget.DEBIAN
            File("/etc/redhat-release").exists() || File("/etc/fedora-release").exists() -> UpdateTarget.FEDORA
            else -> null
        }
    }
}
