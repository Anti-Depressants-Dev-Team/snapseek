package dev.snapseek.core.booru

import dev.snapseek.core.download.ImageFetcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Client for the Gelbooru 0.2 API family: safebooru.org, gelbooru.com, rule34.xxx, tbib.org, xbooru and friends.
 * Endpoints follow what Boorusama's booru_clients package does for "gelbooru_v2":
 *   posts         index.php?page=dapi&s=post&q=index&json=1&tags=…&limit=…&pid=…
 *   autocomplete  autocomplete.php?q=…            (safebooru-style)  or
 *                 index.php?page=autocomplete2&term=…&type=tag_query&limit=…   (gelbooru.com-style)
 *   tag types     scraped from the post page's #tag-sidebar, since v0.2 sites have no reliable tag API
 */
class GelbooruV2Client(
    override val baseUrl: String,
    private val http: HttpClient = defaultHttp,
    private val userAgent: String = ImageFetcher.DEFAULT_USER_AGENT,
) : BooruClient {
    private val log = KotlinLogging.logger {}
    private val root = baseUrl.trimEnd('/')

    @Volatile
    private var autocompleteStyle: AutocompleteStyle? = null

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val url = "$root/index.php?page=dapi&s=post&q=index&json=1&limit=$limit&pid=$page&tags=${tags.trim().urlEncoded()}"
        val body = get(url)
        return parsePosts(body, root)
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim().lowercase()
        if (term.isEmpty()) return emptyList()
        val styles = autocompleteStyle?.let { listOf(it) } ?: AutocompleteStyle.entries
        for (style in styles) {
            val url = when (style) {
                AutocompleteStyle.SIMPLE -> "$root/autocomplete.php?q=${term.urlEncoded()}"
                AutocompleteStyle.AUTOCOMPLETE2 -> "$root/index.php?page=autocomplete2&type=tag_query&limit=$limit&term=${term.urlEncoded()}"
            }
            val parsed = runCatching { parseSuggestions(get(url)) }.getOrNull()
            if (parsed != null) {
                autocompleteStyle = style
                return parsed.take(limit)
            }
        }
        return emptyList()
    }

    override suspend fun tagCategories(post: BooruPost): Map<String, TagCategory> =
        tagCategoryCache.getOrPut("$root/${post.id}") {
            runCatching { parseTagSidebar(get(postPageUrl(post))) }
                .onFailure { log.debug(it) { "No tag sidebar for post ${post.id}" } }
                .getOrDefault(emptyMap())
        }

    override fun postPageUrl(post: BooruPost): String = "$root/index.php?page=post&s=view&id=${post.id}"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/html;q=0.8, */*;q=0.5")
            .GET()
            .build()
        val response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        if (response.statusCode() !in 200..299) throw BooruHttpException(url, response.statusCode())
        response.body()
    }

    private enum class AutocompleteStyle { SIMPLE, AUTOCOMPLETE2 }

    companion object {
        private val defaultHttp: HttpClient by lazy {
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build()
        }
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val tagCategoryCache = ConcurrentHashMap<String, Map<String, TagCategory>>()

        private fun String.urlEncoded() = URLEncoder.encode(this, Charsets.UTF_8)

        /** Accepts both shapes: a bare JSON array (safebooru) or {"@attributes":…, "post":[…]} (gelbooru.com). */
        fun parsePosts(body: String, baseUrl: String): List<BooruPost> {
            if (body.isBlank()) return emptyList()
            val element = json.parseToJsonElement(body)
            val items: JsonArray = when (element) {
                is JsonArray -> element
                is JsonObject -> element["post"]?.let { if (it is JsonArray) it else JsonArray(listOf(it)) } ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            return items.mapNotNull { (it as? JsonObject)?.let { obj -> postFrom(obj, baseUrl) } }
        }

        private fun postFrom(o: JsonObject, baseUrl: String): BooruPost? {
            val id = o.long("id") ?: return null
            val md5 = o.string("hash") ?: o.string("md5") ?: return null
            val directory = o.string("directory")
            val image = o.string("image")
            val fileUrl = o.string("file_url")
                ?: if (directory != null && image != null) "$baseUrl/images/$directory/$image" else return null
            val hasSample = o.bool("sample") ?: false
            val previewUrl = o.string("preview_url")
                ?: if (directory != null && image != null) "$baseUrl/thumbnails/$directory/thumbnail_${image.substringBeforeLast('.')}.jpg" else fileUrl
            val sampleUrl = o.string("sample_url")
                ?: if (hasSample && directory != null && image != null) "$baseUrl/samples/$directory/sample_${image.substringBeforeLast('.')}.jpg" else fileUrl
            return BooruPost(
                id = id,
                md5 = md5,
                previewUrl = previewUrl,
                sampleUrl = sampleUrl,
                fileUrl = fileUrl,
                width = o.int("width") ?: 0,
                height = o.int("height") ?: 0,
                tags = (o.string("tags") ?: "").split(' ').map { it.trim() }.filter { it.isNotEmpty() },
                rating = o.string("rating") ?: "",
                score = o.int("score"),
                source = o.string("source")?.takeIf { it.isNotBlank() },
                owner = o.string("owner"),
                hasSample = hasSample,
            )
        }

        /** Accepts [{label:"tag (123)", value:"tag"}] and [{type, label, value, post_count, category}]. */
        fun parseSuggestions(body: String): List<TagSuggestion> {
            val element = json.parseToJsonElement(body)
            val items = element as? JsonArray ?: throw IllegalArgumentException("Autocomplete response is not a list")
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val value = o.string("value") ?: return@mapNotNull null
                val label = o.string("label") ?: value
                val labelMatch = LABEL_COUNT.matchEntire(label)
                val count = o.int("post_count") ?: labelMatch?.groupValues?.get(2)?.toIntOrNull()
                TagSuggestion(
                    name = value,
                    postCount = count,
                    category = TagCategory.parse(o.string("category") ?: o.string("type")),
                )
            }
        }

        /** Reads <li class="tag-type-artist …"><a href="…tags=name">…</a></li> entries from a post page. */
        fun parseTagSidebar(html: String): Map<String, TagCategory> {
            val sidebar = html.substringAfter("id=\"tag-sidebar\"", "").substringBefore("</ul>")
            if (sidebar.isBlank()) return emptyMap()
            return TAG_LI.findAll(sidebar).associate { m ->
                val name = URLDecoder.decode(m.groupValues[2], Charsets.UTF_8).replace('+', ' ').replace(' ', '_')
                name to TagCategory.parse(m.groupValues[1])
            }
        }

        private val LABEL_COUNT = Regex("""^(.*) \((\d+)\)$""")
        private val TAG_LI = Regex(
            """<li[^>]*class="[^"]*tag-type-([a-z]+)[^"]*"[^>]*>.*?href="[^"]*(?:[?&]|&amp;)tags=([^"&]+)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
        private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
        private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { p ->
            p.booleanOrNull ?: p.intOrNull?.let { it != 0 } ?: p.contentOrNull?.let { it == "true" || it == "1" }
        }

        @Suppress("unused")
        private fun JsonElement.asArrayOrEmpty(): JsonArray = runCatching { jsonArray }.getOrElse { runCatching { JsonArray(listOf(jsonObject)) }.getOrDefault(JsonArray(emptyList())) }
    }
}

class BooruHttpException(val url: String, val status: Int) : RuntimeException("HTTP $status for $url")
