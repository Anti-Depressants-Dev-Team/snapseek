package dev.snapseek.core.download

import dev.snapseek.core.sites.RefererPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Something that can hand us the browser session's cookies for a URL. The browser engine implements this. */
interface CookieSource {
    suspend fun cookieHeaderFor(url: String): String?
}

object NoCookies : CookieSource {
    override suspend fun cookieHeaderFor(url: String): String? = null
}

class FetchedImage(val bytes: ByteArray, val url: String, val contentType: String?)

class HttpFailure(val url: String, val status: Int, detail: String? = null) :
    IOException("HTTP $status for $url" + (detail?.let { " ($it)" } ?: ""))

/**
 * Downloads image bytes with the same Referer the browser would send and the browser's own cookies,
 * so anything you can see logged in, you can save logged in.
 */
class ImageFetcher(
    private val cookies: CookieSource,
    private val referers: RefererPolicy,
    private val userAgent: String = DEFAULT_USER_AGENT,
) {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    suspend fun fetch(source: ImageSource): FetchedImage = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (url in source.candidates) {
            try {
                return@withContext fetchOne(url, source.referer)
            } catch (e: IOException) {
                last = e
            } catch (e: IllegalArgumentException) {
                last = e
            }
        }
        throw last ?: IllegalStateException("No candidate URLs")
    }

    private suspend fun fetchOne(url: String, referer: String?): FetchedImage {
        val builder = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(90))
            .header("User-Agent", userAgent)
            .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        (referer ?: referers.refererForUrl(url))?.let { builder.header("Referer", it) }
        cookies.cookieHeaderFor(url)?.let { builder.header("Cookie", it) }

        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        val status = response.statusCode()
        if (status !in 200..299) throw HttpFailure(url, status)

        val contentType = response.headers().firstValue("Content-Type").orElse(null)
        if (contentType != null && !looksLikeImage(contentType)) throw HttpFailure(url, status, "not an image: $contentType")
        if (response.body().isEmpty()) throw HttpFailure(url, status, "empty body")
        return FetchedImage(response.body(), url, contentType)
    }

    private fun looksLikeImage(contentType: String): Boolean {
        val ct = contentType.lowercase()
        return ct.startsWith("image/") || ct.startsWith("video/") || ct.startsWith("application/octet-stream") || ct.startsWith("binary/")
    }

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
    }
}
