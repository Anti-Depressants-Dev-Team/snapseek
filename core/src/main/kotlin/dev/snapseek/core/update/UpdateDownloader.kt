package dev.snapseek.core.update

import dev.snapseek.core.net.Http
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** How a download is going, for whatever is showing it. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val bytes: Long, val total: Long) : DownloadState {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data class Done(val file: File) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/**
 * Fetches the file a release points at and checks it before anyone is asked to run it.
 *
 * The checksum matters more here than anywhere else in the app: this is the one download that ends up being
 * executed. When the release publishes SHA256SUMS.txt the file is compared against it and thrown away on a
 * mismatch; when it doesn't, the download still happens but says so.
 */
class UpdateDownloader(private val userAgent: String = "SnapSeek") {
    private val log = KotlinLogging.logger {}

    class Result(val file: File, val checksumVerified: Boolean)

    suspend fun download(update: Update, into: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): Result =
        withContext(Dispatchers.IO) {
            into.mkdirs()
            val target = File(into, update.fileName)

            onProgress(0, update.sizeBytes)
            val response = Http.request(update.downloadUrl, headers = mapOf("User-Agent" to userAgent), readTimeoutMs = 300_000)
            if (!response.ok) error("The download answered HTTP ${response.status}")
            if (response.body.isEmpty()) error("The download was empty")
            onProgress(response.body.size.toLong(), response.body.size.toLong())

            val expected = update.checksumsUrl?.let { url ->
                runCatching {
                    val sums = Http.request(url, headers = mapOf("User-Agent" to userAgent)).text()
                    UpdateChecker.checksumFor(sums, update.fileName)
                }.onFailure { log.info(it) { "Couldn't read the release's checksums" } }.getOrNull()
            }
            if (expected != null) {
                val actual = sha256(response.body)
                if (!actual.equals(expected, ignoreCase = true)) {
                    error("The download doesn't match the checksum published with the release")
                }
                log.info { "${update.fileName} matches its published checksum" }
            } else {
                log.warn { "${update.fileName} has no published checksum to check against" }
            }

            target.writeBytes(response.body)
            Result(target, checksumVerified = expected != null)
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
