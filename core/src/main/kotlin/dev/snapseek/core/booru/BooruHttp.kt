package dev.snapseek.core.booru

import dev.snapseek.core.net.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

/**
 * The headers every site client sends, on top of the shared [Http].
 * The default user agent names the app on purpose: Danbooru's Cloudflare front challenges browser-like agents that
 * don't come from a browser, and e621 rejects generic ones outright. An honest agent passes both.
 * Sites that only talk to browsers (Pinterest, Pixiv) get a browser agent explicitly.
 */
class BooruHttp(
    private val userAgent: String = APP_USER_AGENT,
    private val timeoutMillis: Int = 30_000,
) {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), accept: String = "application/json, */*;q=0.5"): String =
        send(url, "GET", headers, accept, null)

    /** Form-encoded POST. Non-2xx answers still surface their body through [BooruHttpException.body], since sites explain failures there. */
    suspend fun post(url: String, form: String, headers: Map<String, String> = emptyMap(), accept: String = "application/json, */*;q=0.5"): String =
        send(
            url,
            "POST",
            headers + ("Content-Type" to (headers["Content-Type"] ?: "application/x-www-form-urlencoded; charset=UTF-8")),
            accept,
            form.toByteArray(Charsets.UTF_8),
        )

    private suspend fun send(url: String, method: String, headers: Map<String, String>, accept: String, body: ByteArray?): String {
        val all = buildMap {
            put("User-Agent", userAgent)
            put("Accept", headers["Accept"] ?: accept)
            headers.forEach { (name, value) -> if (!name.equals("Accept", ignoreCase = true)) put(name, value) }
        }
        val result = Http.request(url, method, all, body, readTimeoutMs = timeoutMillis)
        val text = result.text()
        if (!result.ok) throw BooruHttpException(url, result.status, text)
        return text
    }

    companion object {
        const val APP_USER_AGENT = "SnapSeek/2.0 (desktop image saver; github.com/Yabosen/snapseek)"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"

        val default: BooruHttp by lazy { BooruHttp() }
        val browserLike: BooruHttp by lazy { BooruHttp(userAgent = BROWSER_USER_AGENT) }

        fun basicAuth(credentials: BooruCredentials): String =
            "Basic " + Base64.getEncoder().encodeToString("${credentials.login}:${credentials.apiKey}".toByteArray())
    }
}

internal val booruJson: Json = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8)

internal fun parseJson(body: String): JsonElement? = body.takeIf { it.isNotBlank() }?.let { booruJson.parseToJsonElement(it) }

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it != "null" }

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { p ->
    p.booleanOrNull ?: p.intOrNull?.let { it != 0 } ?: p.contentOrNull?.let { it == "true" || it == "1" }
}
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonObject.strList(key: String): List<String> =
    arr(key)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: str(key)?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()

/** Protocol-relative and root-relative URLs become absolute against [root]. */
internal fun absolutize(url: String?, root: String): String? = when {
    url.isNullOrBlank() -> null
    url.startsWith("//") -> "https:$url"
    url.startsWith("/") -> root.trimEnd('/') + url
    url.startsWith("http://") || url.startsWith("https://") -> url
    else -> root.trimEnd('/') + "/" + url
}

internal fun String.unescapeXml(): String = replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
