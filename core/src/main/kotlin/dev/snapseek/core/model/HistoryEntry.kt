package dev.snapseek.core.model

import java.nio.file.Path
import java.time.Instant

data class HistoryEntry(
    val id: Long,
    val file: Path,
    val sourceUrl: String,
    val pageUrl: String,
    val sha256: String,
    /** md5 of the downloaded bytes; lets booru posts be recognised before they are fetched again. */
    val md5: String,
    val serviceId: String?,
    val bytes: Long,
    val savedAt: Instant,
) {
    val fileName: String get() = file.fileName.toString()
    val sourceHost: String
        get() = runCatching { java.net.URI(sourceUrl).host ?: "" }.getOrDefault("")
}

data class NewHistoryEntry(
    val file: Path,
    val sourceUrl: String,
    val pageUrl: String,
    val sha256: String,
    val md5: String,
    val serviceId: String?,
    val bytes: Long,
)

/** A post kept for later without downloading it. Enough is stored to show and save it after the site changes. */
data class Bookmark(
    val id: Long,
    val serviceId: String,
    val serviceName: String,
    val postId: Long,
    val hash: String,
    val previewUrl: String,
    val sampleUrl: String,
    val fileUrl: String,
    val pageUrl: String,
    val width: Int,
    val height: Int,
    val tags: List<String>,
    val rating: String,
    val source: String?,
    val createdAt: Instant,
) {
    val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
    val extension: String get() = fileUrl.substringBefore('?').substringAfterLast('.', "").lowercase().ifEmpty { "jpg" }
}
