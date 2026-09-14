package dev.snapseek.core.booru

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
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
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * One JDK HttpClient shared by every booru client, with the headers boorus care about.
 * The user agent names the app on purpose: Danbooru's Cloudflare front challenges browser-like agents that
 * don't come from a browser, and e621 rejects generic ones outright. An honest agent passes both.
 */
class BooruHttp(
    private val userAgent: String = APP_USER_AGENT,
    private val timeout: Duration = Duration.ofSeconds(30),
) {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), accept: String = "application/json, */*;q=0.5"): String =
        withContext(Dispatchers.IO) {
            val builder = HttpRequest.newBuilder(URI(url))
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .GET()
            headers.forEach { (k, v) -> builder.header(k, v) }
            val response = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()).await()
            if (response.statusCode() !in 200..299) throw BooruHttpException(url, response.statusCode())
            response.body()
        }

    companion object {
        const val APP_USER_AGENT = "SnapSeek/2.0 (desktop image saver; github.com/Yabosen/snapseek)"

        val default: BooruHttp by lazy { BooruHttp() }

        fun basicAuth(credentials: BooruCredentials): String =
            "Basic " + Base64.getEncoder().encodeToString("${credentials.login}:${credentials.apiKey}".toByteArray())
    }
}

internal val booruJson: Json = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8)

internal fun parseJson(body: String): JsonElement? = body.takeIf { it.isNotBlank() }?.let { booruJson.parseToJsonElement(it) }

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject
internal fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

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
