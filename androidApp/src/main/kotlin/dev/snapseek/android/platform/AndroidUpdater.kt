package dev.snapseek.android.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dev.snapseek.core.settings.SettingsStore
import dev.snapseek.core.update.DownloadState
import dev.snapseek.core.update.Update
import dev.snapseek.core.update.UpdateChecker
import dev.snapseek.core.update.UpdateDownloader
import dev.snapseek.core.update.UpdateTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The phone build is sideloaded, so nothing updates it on its own. This looks for a newer release once a day,
 * says so, and on request downloads the APK and hands it to Android's own installer, which is the only thing
 * allowed to replace an app. The download is checked against the release's published checksum first.
 *
 * Android will not install anything until this app is allowed to: the first attempt sends the user to the system
 * screen that grants that, then they come back and press it again.
 */
class AndroidUpdater(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val currentVersion: String,
    private val checker: UpdateChecker = UpdateChecker(),
    private val downloader: UpdateDownloader = UpdateDownloader(),
) {
    private val _available = MutableStateFlow<Update?>(null)
    val available: StateFlow<Update?> = _available.asStateFlow()

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    fun checkOnStart() {
        if (!settings.current.checkForUpdates) return
        if (System.currentTimeMillis() - settings.current.lastUpdateCheck < ONCE_A_DAY) return
        check()
    }

    fun check() {
        scope.launch {
            val found = runCatching { checker.check(currentVersion, UpdateTarget.ANDROID) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            _available.value = found?.takeIf { it.version != settings.current.skippedVersion }
        }
    }

    fun skip(update: Update) {
        settings.update { it.copy(skippedVersion = update.version) }
        _available.value = null
    }

    fun dismiss() {
        _available.value = null
    }

    /** Downloads the APK and opens it with the system installer. Returns the user to Settings first if it must. */
    fun install(update: Update) {
        if (_download.value is DownloadState.Running) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _download.value = DownloadState.Failed("Allow SnapSeek to install apps, then press Update again")
            askForInstallPermission()
            return
        }
        scope.launch {
            _download.value = DownloadState.Running(0, update.sizeBytes)
            runCatching {
                downloader.download(update, File(context.cacheDir, "updates")) { bytes, total ->
                    _download.value = DownloadState.Running(bytes, total)
                }
            }.onSuccess { result ->
                _download.value = DownloadState.Done(result.file)
                Log.i(TAG, "Downloaded ${result.file.name}, checksum ${if (result.checksumVerified) "verified" else "not published"}")
                handToInstaller(result.file)
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "The update failed", it)
                _download.value = DownloadState.Failed(it.message ?: "The download failed")
            }
        }
    }

    private fun handToInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Log.w(TAG, "No installer would take the APK", it)
                _download.value = DownloadState.Failed("Downloaded, but nothing on this phone would open it")
            }
    }

    private fun askForInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val ONCE_A_DAY = 24L * 60 * 60 * 1000
        const val TAG = "SnapSeek"
    }
}
