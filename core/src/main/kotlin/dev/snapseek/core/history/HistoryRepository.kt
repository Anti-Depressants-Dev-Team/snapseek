package dev.snapseek.core.history

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
    suspend fun remove(id: Long)
    suspend fun clear()
}

/** Phase 0 stand-in. The SQLite implementation replaces it in phase 1 without touching callers. */
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
            serviceId = entry.serviceId,
            bytes = entry.bytes,
            savedAt = Instant.now(),
        )
        _entries.update { listOf(saved) + it }
        return saved
    }

    override suspend fun findBySha(sha256: String): HistoryEntry? = _entries.value.firstOrNull { it.sha256 == sha256 }

    override suspend fun remove(id: Long) = _entries.update { list -> list.filterNot { it.id == id } }

    override suspend fun clear() = _entries.update { emptyList() }
}
