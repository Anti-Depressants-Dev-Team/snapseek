package dev.snapseek.app.platform

import dev.snapseek.core.history.BookmarkRepository
import dev.snapseek.core.history.HistoryRepository

import dev.snapseek.core.model.Bookmark
import dev.snapseek.core.model.HistoryEntry
import dev.snapseek.core.model.NewHistoryEntry
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant
import kotlin.io.path.createDirectories

/**
 * Download history and bookmarks in one SQLite file. Plain JDBC: two tables, a handful of statements,
 * nothing to generate. Reads are served from memory (the tables are small); writes go to disk first.
 */
class SqliteStore(path: Path) : AutoCloseable {
    private val log = KotlinLogging.logger {}
    private val connection: Connection

    init {
        path.parent?.createDirectories()
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
        connection.createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            st.execute(
                """CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file TEXT NOT NULL, source_url TEXT NOT NULL, page_url TEXT NOT NULL,
                    sha256 TEXT NOT NULL, md5 TEXT NOT NULL DEFAULT '', service_id TEXT,
                    bytes INTEGER NOT NULL, saved_at INTEGER NOT NULL)""",
            )
            st.execute("CREATE INDEX IF NOT EXISTS history_sha ON history(sha256)")
            st.execute("CREATE INDEX IF NOT EXISTS history_md5 ON history(md5)")
            st.execute(
                """CREATE TABLE IF NOT EXISTS bookmarks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    service_id TEXT NOT NULL, service_name TEXT NOT NULL, post_id INTEGER NOT NULL,
                    hash TEXT NOT NULL, preview_url TEXT NOT NULL, sample_url TEXT NOT NULL, file_url TEXT NOT NULL,
                    page_url TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, tags TEXT NOT NULL,
                    rating TEXT NOT NULL, source TEXT, created_at INTEGER NOT NULL,
                    UNIQUE(service_id, post_id))""",
            )
        }
    }

    val history: HistoryRepository = History()
    val bookmarks: BookmarkRepository = Bookmarks()

    private suspend fun <T> db(block: (Connection) -> T): T = withContext(Dispatchers.IO) { synchronized(connection) { block(connection) } }

    override fun close() = runCatching { connection.close() }.onFailure { log.warn(it) { "Closing database failed" } }.let { }

    private inner class History : HistoryRepository {
        private val _entries = MutableStateFlow(loadAll())
        override val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

        private fun loadAll(): List<HistoryEntry> = synchronized(connection) {
            connection.createStatement().use { st ->
                st.executeQuery("SELECT * FROM history ORDER BY saved_at DESC, id DESC LIMIT 2000").use { rs ->
                    generateSequence { if (rs.next()) rs.toHistory() else null }.toList()
                }
            }
        }

        override suspend fun record(entry: NewHistoryEntry): HistoryEntry {
            val now = Instant.now()
            val id = db { c ->
                c.prepareStatement(
                    "INSERT INTO history(file, source_url, page_url, sha256, md5, service_id, bytes, saved_at) VALUES (?,?,?,?,?,?,?,?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS,
                ).use { ps ->
                    ps.setString(1, entry.file.toString()); ps.setString(2, entry.sourceUrl); ps.setString(3, entry.pageUrl)
                    ps.setString(4, entry.sha256); ps.setString(5, entry.md5); ps.setString(6, entry.serviceId)
                    ps.setLong(7, entry.bytes); ps.setLong(8, now.toEpochMilli())
                    ps.executeUpdate()
                    ps.generatedKeys.use { k -> if (k.next()) k.getLong(1) else 0L }
                }
            }
            val saved = HistoryEntry(id, entry.file, entry.sourceUrl, entry.pageUrl, entry.sha256, entry.md5, entry.serviceId, entry.bytes, now)
            _entries.update { listOf(saved) + it }
            return saved
        }

        override suspend fun findBySha(sha256: String): HistoryEntry? = _entries.value.firstOrNull { it.sha256 == sha256 }
            ?: db { c -> c.prepareStatement("SELECT * FROM history WHERE sha256 = ? LIMIT 1").use { ps -> ps.setString(1, sha256); ps.executeQuery().use { if (it.next()) it.toHistory() else null } } }

        override suspend fun findByMd5(md5: String): HistoryEntry? = _entries.value.firstOrNull { it.md5.equals(md5, ignoreCase = true) }
            ?: db { c -> c.prepareStatement("SELECT * FROM history WHERE md5 = ? COLLATE NOCASE LIMIT 1").use { ps -> ps.setString(1, md5); ps.executeQuery().use { if (it.next()) it.toHistory() else null } } }

        override suspend fun remove(id: Long) {
            db { c -> c.prepareStatement("DELETE FROM history WHERE id = ?").use { it.setLong(1, id); it.executeUpdate() } }
            _entries.update { list -> list.filterNot { it.id == id } }
        }

        override suspend fun clear() {
            db { c -> c.createStatement().use { it.execute("DELETE FROM history") } }
            _entries.value = emptyList()
        }

        private fun ResultSet.toHistory() = HistoryEntry(
            id = getLong("id"),
            file = Path.of(getString("file")),
            sourceUrl = getString("source_url"),
            pageUrl = getString("page_url"),
            sha256 = getString("sha256"),
            md5 = getString("md5") ?: "",
            serviceId = getString("service_id"),
            bytes = getLong("bytes"),
            savedAt = Instant.ofEpochMilli(getLong("saved_at")),
        )
    }

    private inner class Bookmarks : BookmarkRepository {
        private val _entries = MutableStateFlow(loadAll())
        override val entries: StateFlow<List<Bookmark>> = _entries.asStateFlow()

        private fun loadAll(): List<Bookmark> = synchronized(connection) {
            connection.createStatement().use { st ->
                st.executeQuery("SELECT * FROM bookmarks ORDER BY created_at DESC, id DESC").use { rs ->
                    generateSequence { if (rs.next()) rs.toBookmark() else null }.toList()
                }
            }
        }

        override suspend fun add(bookmark: Bookmark): Bookmark {
            val now = Instant.now()
            val id = db { c ->
                c.prepareStatement(
                    """INSERT OR REPLACE INTO bookmarks(service_id, service_name, post_id, hash, preview_url, sample_url, file_url, page_url,
                       width, height, tags, rating, source, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                    java.sql.Statement.RETURN_GENERATED_KEYS,
                ).use { ps ->
                    ps.setString(1, bookmark.serviceId); ps.setString(2, bookmark.serviceName); ps.setLong(3, bookmark.postId)
                    ps.setString(4, bookmark.hash); ps.setString(5, bookmark.previewUrl); ps.setString(6, bookmark.sampleUrl)
                    ps.setString(7, bookmark.fileUrl); ps.setString(8, bookmark.pageUrl); ps.setInt(9, bookmark.width); ps.setInt(10, bookmark.height)
                    ps.setString(11, bookmark.tags.joinToString(" ")); ps.setString(12, bookmark.rating); ps.setString(13, bookmark.source)
                    ps.setLong(14, now.toEpochMilli())
                    ps.executeUpdate()
                    ps.generatedKeys.use { k -> if (k.next()) k.getLong(1) else 0L }
                }
            }
            val saved = bookmark.copy(id = id, createdAt = now)
            _entries.update { list -> listOf(saved) + list.filterNot { it.serviceId == saved.serviceId && it.postId == saved.postId } }
            return saved
        }

        override suspend fun remove(serviceId: String, postId: Long) {
            db { c -> c.prepareStatement("DELETE FROM bookmarks WHERE service_id = ? AND post_id = ?").use { it.setString(1, serviceId); it.setLong(2, postId); it.executeUpdate() } }
            _entries.update { list -> list.filterNot { it.serviceId == serviceId && it.postId == postId } }
        }

        override suspend fun clear() {
            db { c -> c.createStatement().use { it.execute("DELETE FROM bookmarks") } }
            _entries.value = emptyList()
        }

        private fun ResultSet.toBookmark() = Bookmark(
            id = getLong("id"),
            serviceId = getString("service_id"),
            serviceName = getString("service_name"),
            postId = getLong("post_id"),
            hash = getString("hash"),
            previewUrl = getString("preview_url"),
            sampleUrl = getString("sample_url"),
            fileUrl = getString("file_url"),
            pageUrl = getString("page_url"),
            width = getInt("width"),
            height = getInt("height"),
            tags = getString("tags").split(' ').filter { it.isNotEmpty() },
            rating = getString("rating"),
            source = getString("source"),
            createdAt = Instant.ofEpochMilli(getLong("created_at")),
        )
    }
}
