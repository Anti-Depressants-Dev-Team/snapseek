package dev.snapseek.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

/** What came back: the status, the body, and the headers, whatever the status was. */
class HttpResult(
    val url: String,
    val status: Int,
    val body: ByteArray,
    private val headers: Map<String?, List<String>>,
) {
    val ok: Boolean get() = status in 200..299

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key?.equals(name, ignoreCase = true) == true }?.value?.firstOrNull()

    fun text(): String = body.toString(Charsets.UTF_8)
}

/**
 * Every HTTP call the app makes goes through here, on `HttpURLConnection`, because that is the one client both
 * the desktop JVM and Android ship. It decodes gzip itself (asking for it explicitly is what keeps big JSON
 * responses small), follows redirects by hand so a hop from http to https isn't silently dropped the way the
 * built-in follower drops it, and hands back the body even on an error status, since sites explain refusals there.
 */
object Http {
    private const val MAX_REDIRECTS = 5

    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 90_000,
    ): HttpResult = withContext(Dispatchers.IO) {
        var current = url
        var currentMethod = method
        var currentBody = body
        repeat(MAX_REDIRECTS + 1) {
            val result = once(current, currentMethod, headers, currentBody, connectTimeoutMs, readTimeoutMs)
            val location = result.header("Location")
            if (result.status !in REDIRECTS || location.isNullOrBlank()) return@withContext result
            current = URI(current).resolve(location).toString()
            // 303, and by long convention 301 and 302, turn a POST into a GET; 307 and 308 keep the method.
            if (result.status != 307 && result.status != 308) {
                currentMethod = "GET"
                currentBody = null
            }
        }
        throw java.io.IOException("Too many redirects for $url")
    }

    private fun once(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpResult {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept-Encoding", "gzip")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val raw = stream?.use { it.readBytes() } ?: ByteArray(0)
            val gzipped = connection.getHeaderField("Content-Encoding")?.contains("gzip", ignoreCase = true) == true
            val decoded = if (gzipped || raw.isGzip()) GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() } else raw
            return HttpResult(url, status, decoded, connection.headerFields.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private val REDIRECTS = setOf(301, 302, 303, 307, 308)

    private fun ByteArray.isGzip() = size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()
}
