package dev.snapseek.app.platform

import dev.snapseek.core.model.Bookmark
import dev.snapseek.core.model.NewHistoryEntry
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqliteStoreTest {

    @Test
    fun `history survives reopening the database and is searchable by hash`() = runBlocking {
        val db = createTempDirectory("snapseek-db").resolve("snapseek.db")
        SqliteStore(db).use { store ->
            val saved = store.history.record(NewHistoryEntry(Path.of("C:/x/a.png"), "https://s/a.png", "https://s/page", "sha-a", "md5-a", "safebooru", 1234))
            assertTrue(saved.id > 0)
            store.history.record(NewHistoryEntry(Path.of("C:/x/b.png"), "https://s/b.png", "https://s/page", "sha-b", "MD5-B", null, 1))
            assertEquals(listOf("b.png", "a.png"), store.history.entries.value.map { it.fileName })
        }
        SqliteStore(db).use { store ->
            assertEquals(2, store.history.entries.value.size)
            assertNotNull(store.history.findBySha("sha-a"))
            assertNotNull(store.history.findByMd5("md5-b"), "md5 lookup is case-insensitive")
            assertNull(store.history.findBySha("nope"))
            store.history.remove(store.history.entries.value.first().id)
            assertEquals(1, store.history.entries.value.size)
            store.history.clear()
            assertTrue(store.history.entries.value.isEmpty())
        }
    }

    @Test
    fun `bookmarks are unique per service and post`() = runBlocking {
        val db = createTempDirectory("snapseek-db").resolve("snapseek.db")
        SqliteStore(db).use { store ->
            val b = Bookmark(0, "safebooru", "Safebooru", 42, "hash", "p", "s", "f", "page", 100, 200, listOf("cat", "solo"), "general", null, Instant.EPOCH)
            store.bookmarks.add(b)
            store.bookmarks.add(b.copy(tags = listOf("cat", "solo", "edited")))
            assertEquals(1, store.bookmarks.entries.value.size)
            assertEquals(listOf("cat", "solo", "edited"), store.bookmarks.entries.value.single().tags)
            assertTrue(store.bookmarks.isBookmarked("safebooru", 42))
            assertFalse(store.bookmarks.isBookmarked("safebooru", 43))
        }
        SqliteStore(db).use { store ->
            assertEquals(1, store.bookmarks.entries.value.size)
            store.bookmarks.remove("safebooru", 42)
            assertTrue(store.bookmarks.entries.value.isEmpty())
        }
    }
}
