package dev.snapseek.android.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dev.snapseek.core.download.CookieSource
import dev.snapseek.core.net.Http
import dev.snapseek.core.sites.RefererPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Thumbnails for the grid. Pixiv refuses any image request without a Referer and Pinterest wants a browser agent,
 * so these go out through the same client the rest of the app uses rather than a stock image library. Decoded
 * bitmaps are held in a cache a quarter the size of the app's heap, and only a few downloads run at once so a
 * fast scroll doesn't open fifty sockets.
 */
class ImageLoader(
    private val referers: RefererPolicy,
    private val cookies: CookieSource,
    private val userAgent: String,
) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 4).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val inFlight = Semaphore(4)

    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String, maxWidth: Int = 0): Bitmap? {
        cache.get(url)?.let { return it }
        return inFlight.withPermit {
            cache.get(url)?.let { return@withPermit it }
            withContext(Dispatchers.IO) {
                runCatching {
                    val headers = buildMap {
                        put("User-Agent", userAgent)
                        put("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                        referers.refererForUrl(url)?.let { put("Referer", it) }
                        cookies.cookieHeaderFor(url)?.let { put("Cookie", it) }
                    }
                    val response = Http.request(url, "GET", headers)
                    if (!response.ok || response.body.isEmpty()) return@runCatching null
                    decode(response.body, maxWidth)?.also { cache.put(url, it) }
                }.getOrNull()
            }
        }
    }

    /** Decodes at the smallest power-of-two scale that still covers the space it will be drawn in. */
    private fun decode(bytes: ByteArray, maxWidth: Int): Bitmap? {
        if (maxWidth <= 0) return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
