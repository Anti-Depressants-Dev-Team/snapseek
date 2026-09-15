package dev.snapseek.core.download

import com.sun.net.httpserver.HttpServer
import dev.snapseek.core.history.InMemoryHistoryRepository
import dev.snapseek.core.model.OutputFormat
import dev.snapseek.core.settings.SettingsStore
import dev.snapseek.core.sites.RefererPolicy
import dev.snapseek.core.sites.SourceResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** End-to-end pipeline against a local HTTP server: resolve → fetch (with Referer) → hash → transcode → write → record. */
class DownloadManagerTest {
    private lateinit var server: HttpServer
    private lateinit var scope: CoroutineScope
    private val lastReferer = AtomicReference<String?>()
    private val lastCookie = AtomicReference<String?>()
    private val png: ByteArray = run {
        val img = BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until 6) for (y in 0 until 6) img.setRGB(x, y, 0xFF00AA55.toInt())
        ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    @BeforeTest
    fun start() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/originals/pic.png") { ex ->
            lastReferer.set(ex.requestHeaders.getFirst("Referer"))
            lastCookie.set(ex.requestHeaders.getFirst("Cookie"))
            ex.responseHeaders.add("Content-Type", "image/png")
            ex.sendResponseHeaders(200, png.size.toLong())
            ex.responseBody.use { it.write(png) }
        }
        server.createContext("/missing.png") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
        server.createContext("/page.html") { ex ->
            val body = "<html>nope</html>".toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        scope.cancel()
    }

    private val base get() = "http://127.0.0.1:${server.address.port}"

    private fun manager(dir: java.nio.file.Path, format: OutputFormat = OutputFormat.PNG): Pair<DownloadManager, InMemoryHistoryRepository> {
        val settings = SettingsStore(dir.resolve("settings.json"))
        settings.update { it.copy(downloadDir = dir.resolve("out").toString(), defaultFormat = format, fileNameTemplate = "{service}_{hash8}") }
        val history = InMemoryHistoryRepository()
        // A resolver that mimics Pinterest: /thumbs/x.png → try /originals/pic.png first, page URL last.
        val resolver = SourceResolver { imageUrl, _ ->
            if (imageUrl.contains("/thumbs/")) ImageSource(listOf("$base/missing.png", "$base/originals/pic.png", imageUrl), referer = "https://example.test/") else null
        }
        val cookies = object : CookieSource {
            override suspend fun cookieHeaderFor(url: String) = "session=abc"
        }
        val manager = DownloadManager(
            resolvers = listOf(resolver),
            fetcher = ImageFetcher(cookies, RefererPolicy()),
            transcoder = PassThroughTranscoder,
            namer = FileNamer(),
            history = history,
            settings = settings,
            scope = scope,
            workers = 1,
        )
        return manager to history
    }

    private suspend fun awaitFinished(manager: DownloadManager, id: String): DownloadJob = withTimeout(15_000) {
        manager.jobs.map { jobs -> jobs.firstOrNull { it.request.id == id } }
            .filterIsInstance<DownloadJob>()
            .first { !it.isActive }
    }

    @Test
    fun `saves through the resolver, converting png to jpeg, and records history`() = runBlocking {
        val dir = createTempDirectory("snapseek-dl")
        val (manager, history) = manager(dir)
        val request = DownloadRequest("$base/thumbs/x.png", "https://example.test/pin/1", OutputFormat.JPEG, serviceId = "pinterest")

        manager.enqueue(request)
        val job = awaitFinished(manager, request.id)

        val saved = assertIs<DownloadJob.Saved>(job, "job ended as $job")
        assertTrue(saved.file.exists())
        assertEquals("pinterest_", saved.file.fileName.toString().take(10))
        assertTrue(saved.file.fileName.toString().endsWith(".jpg"))
        assertEquals(ImageKind.JPEG, ImageKind.sniff(saved.file.readBytes()))
        assertEquals("https://example.test/", lastReferer.get(), "resolver's referer reaches the server")
        assertEquals("session=abc", lastCookie.get(), "browser cookies reach the server")

        val entry = history.entries.value.single()
        assertEquals("$base/originals/pic.png", entry.sourceUrl, "history records the URL that actually served the bytes")
        assertNotNull(history.findBySha(entry.sha256))
    }

    @Test
    fun `identical bytes are reported as duplicates instead of saved twice`() = runBlocking {
        val dir = createTempDirectory("snapseek-dl")
        val (manager, _) = manager(dir, OutputFormat.ORIGINAL)

        val first = DownloadRequest("$base/originals/pic.png", "https://example.test/")
        manager.enqueue(first)
        assertIs<DownloadJob.Saved>(awaitFinished(manager, first.id))

        val second = DownloadRequest("$base/originals/pic.png", "https://example.test/")
        manager.enqueue(second)
        assertIs<DownloadJob.Duplicate>(awaitFinished(manager, second.id))

        assertEquals(1, dir.resolve("out").listDirectoryEntries().size)
    }

    @Test
    fun `non-image responses fail with a readable message`() = runBlocking {
        val dir = createTempDirectory("snapseek-dl")
        val (manager, _) = manager(dir)
        val request = DownloadRequest("$base/page.html", "https://example.test/")

        manager.enqueue(request)
        val job = assertIs<DownloadJob.Failed>(awaitFinished(manager, request.id))
        assertTrue(job.message.contains("not an image"), job.message)
    }
}

/**
 * The pipeline's own tests care about naming, hashing, duplicates and history, not about pixels, so they run on a
 * transcoder that only relabels: it keeps the bytes and reports the container the caller asked for.
 */
private object PassThroughTranscoder : ImageTranscoder {
    override fun transcode(bytes: ByteArray, target: OutputFormat, fallbackExtension: String?): TranscodeResult {
        val source = ImageKind.sniff(bytes)
        val kind = target.kind ?: source
        val extension = if (kind == ImageKind.UNKNOWN) fallbackExtension ?: kind.extension else kind.extension
        return TranscodeResult(bytes, kind, copiedThrough = kind == source, extension = extension)
    }
}
