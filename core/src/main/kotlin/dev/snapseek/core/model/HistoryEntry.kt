package dev.snapseek.core.model

import java.nio.file.Path
import java.time.Instant

data class HistoryEntry(
    val id: Long,
    val file: Path,
    val sourceUrl: String,
    val pageUrl: String,
    val sha256: String,
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
    val serviceId: String?,
    val bytes: Long,
)
