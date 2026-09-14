package dev.snapseek.app.ui.common

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.snapseek.core.download.ImageFetcher
import dev.snapseek.core.sites.RefererPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.await
import org.jetbrains.skia.Image
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * Small image loader for the native booru grid: memory LRU in front of a disk cache, Skia for decoding.
 * Concurrent requests for the same URL share one download. No third-party dependency to go stale on us.
 */
class ImageLoader(
    private val diskDir: Path,
    private val referers: RefererPolicy,
    private val scope: CoroutineScope,
) {
    private val log = KotlinLogging.logger {}
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build()
    private val thumbs = LruCache<String, ImageBitmap>(400)
    private val large = LruCache<String, ImageBitmap>(6)
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()

    init {
        diskDir.createDirectories()
    }

    fun cached(url: String, large: Boolean): ImageBitmap? = (if (large) this.large else thumbs).get(url)

    suspend fun load(url: String, large: Boolean = false): ImageBitmap? {
        val cache = if (large) this.large else thumbs
        cache.get(url)?.let { return it }
        val deferred = inFlight.computeIfAbsent(url) {
            scope.async(Dispatchers.IO) {
                try {
                    fetchAndDecode(url)?.also { cache.put(url, it) }
                } catch (e: Exception) {
                    log.debug(e) { "Image failed: $url" }
                    null
                } finally {
                    inFlight.remove(url)
                }
            }
        }
        return deferred.await()
    }

    private suspend fun fetchAndDecode(url: String): ImageBitmap? {
        val onDisk = diskPath(url)
        val bytes = if (onDisk.exists()) onDisk.readBytes() else download(url)?.also { runCatching { onDisk.writeBytes(it) } } ?: return null
        return runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }
            .onFailure { log.debug(it) { "Decode failed: $url" } }
            .getOrNull()
    }

    private suspend fun download(url: String): ByteArray? {
        val builder = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(40))
            .header("User-Agent", ImageFetcher.DEFAULT_USER_AGENT)
            .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        referers.refererForUrl(url)?.let { builder.header("Referer", it) }
        val response = http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray()).await()
        return response.body().takeIf { response.statusCode() in 200..299 && it.isNotEmpty() }
    }

    private fun diskPath(url: String): Path {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return diskDir.resolve(digest)
    }
}

class LruCache<K, V>(private val max: Int) {
    private val map = object : LinkedHashMap<K, V>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > max
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }
}
