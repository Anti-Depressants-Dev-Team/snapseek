package dev.snapseek.core.history

import dev.snapseek.core.model.Bookmark
import dev.snapseek.core.model.HistoryEntry
import dev.snapseek.core.model.NewHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

interface HistoryRepository {
    /** Newest first. */
    val entries: StateFlow<List<HistoryEntry>>
    suspend fun record(entry: NewHistoryEntry): HistoryEntry
    suspend fun findBySha(sha256: String): HistoryEntry?
    suspend fun findByMd5(md5: String): HistoryEntry?
    suspend fun remove(id: Long)
    suspend fun clear()
}

interface BookmarkRepository {
    /** Newest first. */
    val entries: StateFlow<List<Bookmark>>
    suspend fun add(bookmark: Bookmark): Bookmark
    suspend fun remove(serviceId: String, postId: Long)
    suspend fun clear()

    fun isBookmarked(serviceId: String, postId: Long): Boolean = entries.value.any { it.serviceId == serviceId && it.postId == postId }
}

/** Used by tests and as a fallback when the database can't be opened. */
class InMemoryHistoryRepository : HistoryRepository {
    private val ids = AtomicLong(1)
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    override val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    override suspend fun record(entry: NewHistoryEntry): HistoryEntry {
        val saved = HistoryEntry(
            id = ids.getAndIncrement(),
            file = entry.file,
            sourceUrl = entry.sourceUrl,
            pageUrl = entry.pageUrl,
            sha256 = entry.sha256,
            md5 = entry.md5,
            serviceId = entry.serviceId,
            bytes = entry.bytes,
            savedAt = Instant.now(),
        )
        _entries.update { listOf(saved) + it }
        return saved
    }

    override suspend fun findBySha(sha256: String): HistoryEntry? = _entries.value.firstOrNull { it.sha256 == sha256 }
    override suspend fun findByMd5(md5: String): HistoryEntry? = _entries.value.firstOrNull { it.md5.equals(md5, ignoreCase = true) }
    override suspend fun remove(id: Long) = _entries.update { list -> list.filterNot { it.id == id } }
    override suspend fun clear() = _entries.update { emptyList() }
}

class InMemoryBookmarkRepository : BookmarkRepository {
    private val ids = AtomicLong(1)
    private val _entries = MutableStateFlow<List<Bookmark>>(emptyList())
    override val entries: StateFlow<List<Bookmark>> = _entries.asStateFlow()

    override suspend fun add(bookmark: Bookmark): Bookmark {
        val saved = bookmark.copy(id = ids.getAndIncrement())
        _entries.update { list -> listOf(saved) + list.filterNot { it.serviceId == saved.serviceId && it.postId == saved.postId } }
        return saved
    }

    override suspend fun remove(serviceId: String, postId: Long) =
        _entries.update { list -> list.filterNot { it.serviceId == serviceId && it.postId == postId } }

    override suspend fun clear() = _entries.update { emptyList() }
}
