package dev.snapseek.core.download

import dev.snapseek.core.model.HistoryEntry
import dev.snapseek.core.model.OutputFormat
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Where to get the bytes. Candidates are tried in order; the first HTTP 2xx image wins. */
data class ImageSource(
    val candidates: List<String>,
    val referer: String? = null,
) {
    init {
        require(candidates.isNotEmpty()) { "ImageSource needs at least one candidate URL" }
    }
}

data class DownloadRequest(
    val imageUrl: String,
    val pageUrl: String,
    /** null means "use the default format from settings". */
    val format: OutputFormat? = null,
    val serviceId: String? = null,
    /** Extra template tokens (id, tags, rating, …). Booru downloads fill this; web downloads leave it empty. */
    val metadata: Map<String, String> = emptyMap(),
    /** File name template override; null uses the settings default for this kind of download. */
    val template: String? = null,
    /** Run site resolvers to upgrade the URL. Off when the caller already chose exactly what to fetch. */
    val resolve: Boolean = true,
    val referer: String? = null,
    val id: String = UUID.randomUUID().toString(),
)

sealed interface DownloadJob {
    val request: DownloadRequest
    val startedAt: Instant

    val isActive: Boolean
        get() = this is Queued || this is Fetching || this is Converting

    data class Queued(override val request: DownloadRequest, override val startedAt: Instant = Instant.now()) : DownloadJob
    data class Fetching(override val request: DownloadRequest, override val startedAt: Instant, val url: String) : DownloadJob
    data class Converting(override val request: DownloadRequest, override val startedAt: Instant) : DownloadJob
    data class Saved(
        override val request: DownloadRequest,
        override val startedAt: Instant,
        val file: Path,
        val bytes: Long,
        val copiedThrough: Boolean,
    ) : DownloadJob
    data class Duplicate(override val request: DownloadRequest, override val startedAt: Instant, val existing: HistoryEntry) : DownloadJob
    data class Failed(override val request: DownloadRequest, override val startedAt: Instant, val message: String) : DownloadJob
}
