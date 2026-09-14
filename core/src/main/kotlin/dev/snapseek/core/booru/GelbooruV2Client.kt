package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Client for the Gelbooru 0.2 API family: safebooru.org, gelbooru.com, rule34.xxx, tbib.org, xbooru and friends.
 *   posts         index.php?page=dapi&s=post&q=index&json=1&tags=…&limit=…&pid=…
 *   autocomplete  autocomplete.php?q=…  (safebooru-style)  or  index.php?page=autocomplete2&term=…  (gelbooru.com)
 *   tag types     index.php?page=dapi&s=tag&q=index&json=1&names=…  where supported, else the post page's #tag-sidebar
 * gelbooru.com hands out API keys in account settings; other sites work anonymously.
 */
class GelbooruV2Client(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val log = KotlinLogging.logger {}
    private val root = baseUrl.trimEnd('/')
    override val kind = ServiceKind.GELBOORU_V2
    override val maxPageSize = 100
    override val safeModeTags = listOf("-rating:explicit", "-rating:questionable")

    @Volatile
    private var autocompleteStyle: AutocompleteStyle? = null

    private val auth: String
        get() = credentials?.let { "&api_key=${it.apiKey.urlEncoded()}&user_id=${it.login.urlEncoded()}" } ?: ""

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val url = "$root/index.php?page=dapi&s=post&q=index&json=1&limit=${limit.coerceIn(1, maxPageSize)}&pid=$page&tags=${tags.trim().urlEncoded()}$auth"
        return parsePosts(http.get(url), root)
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim().lowercase()
        if (term.isEmpty()) return emptyList()
        val styles = autocompleteStyle?.let { listOf(it) } ?: AutocompleteStyle.entries
        for (style in styles) {
            val url = when (style) {
                AutocompleteStyle.SIMPLE -> "$root/autocomplete.php?q=${term.urlEncoded()}"
                AutocompleteStyle.AUTOCOMPLETE2 -> "$root/index.php?page=autocomplete2&type=tag_query&limit=$limit&term=${term.urlEncoded()}$auth"
            }
            val parsed = runCatching { parseSuggestions(http.get(url)) }.getOrNull()
            if (parsed != null) {
                autocompleteStyle = style
                return parsed.take(limit)
            }
        }
        return emptyList()
    }

    override suspend fun tagCategories(post: BooruPost): Map<String, TagCategory> =
        tagCategoryCache.getOrPut("$root/${post.id}") {
            runCatching { fromTagApi(post.tags) }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: runCatching { parseTagSidebar(http.get(postPageUrl(post), accept = "text/html")) }
                    .onFailure { log.debug(it) { "No tag sidebar for post ${post.id}" } }
                    .getOrDefault(emptyMap())
        }

    private suspend fun fromTagApi(tags: List<String>): Map<String, TagCategory> {
        if (tags.isEmpty()) return emptyMap()
        val url = "$root/index.php?page=dapi&s=tag&q=index&json=1&limit=${tags.size}&names=${tags.joinToString(" ").urlEncoded()}$auth"
        return parseTagApi(http.get(url))
    }

    override fun postPageUrl(post: BooruPost): String = "$root/index.php?page=post&s=view&id=${post.id}"

    private enum class AutocompleteStyle { SIMPLE, AUTOCOMPLETE2 }

    companion object {
        private val tagCategoryCache = ConcurrentHashMap<String, Map<String, TagCategory>>()

        /** Accepts both shapes: a bare JSON array (safebooru) or {"@attributes":…, "post":[…]} (gelbooru.com). */
        fun parsePosts(body: String, baseUrl: String): List<BooruPost> {
            val element = parseJson(body) ?: return emptyList()
            val items: JsonArray = when (element) {
                is JsonArray -> element
                is JsonObject -> element["post"]?.let { if (it is JsonArray) it else JsonArray(listOf(it)) } ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            return items.mapNotNull { (it as? JsonObject)?.let { obj -> postFrom(obj, baseUrl) } }
        }

        private fun postFrom(o: JsonObject, baseUrl: String): BooruPost? {
            val id = o.long("id") ?: return null
            val md5 = o.str("hash") ?: o.str("md5") ?: return null
            val directory = o.str("directory")
            val image = o.str("image")
            val fileUrl = o.str("file_url")?.takeIf { it.isNotBlank() }
                ?: if (directory != null && image != null) "$baseUrl/images/$directory/$image" else return null
            val hasSample = o.bool("sample") ?: false
            val previewUrl = o.str("preview_url")?.takeIf { it.isNotBlank() }
                ?: if (directory != null && image != null) "$baseUrl/thumbnails/$directory/thumbnail_${image.substringBeforeLast('.')}.jpg" else fileUrl
            val sampleUrl = o.str("sample_url")?.takeIf { it.isNotBlank() }
                ?: if (hasSample && directory != null && image != null) "$baseUrl/samples/$directory/sample_${image.substringBeforeLast('.')}.jpg" else fileUrl
            return BooruPost(
                id = id,
                md5 = md5,
                previewUrl = previewUrl,
                sampleUrl = sampleUrl,
                fileUrl = fileUrl,
                width = o.int("width") ?: 0,
                height = o.int("height") ?: 0,
                tags = (o.str("tags") ?: "").split(' ').map { it.trim() }.filter { it.isNotEmpty() },
                rating = BooruPost.normaliseRating(o.str("rating"), sMeans = "safe"),
                score = o.int("score"),
                source = o.str("source")?.takeIf { it.isNotBlank() },
                owner = o.str("owner"),
                hasSample = hasSample && sampleUrl != fileUrl,
            )
        }

        /** Accepts [{label:"tag (123)", value:"tag"}] and [{type, label, value, post_count, category}]. */
        fun parseSuggestions(body: String): List<TagSuggestion> {
            val items = parseJson(body) as? JsonArray ?: throw IllegalArgumentException("Autocomplete response is not a list")
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val value = o.str("value") ?: return@mapNotNull null
                val label = o.str("label") ?: value
                val count = o.int("post_count") ?: LABEL_COUNT.matchEntire(label)?.groupValues?.get(2)?.toIntOrNull()
                TagSuggestion(name = value, postCount = count, category = TagCategory.parse(o.str("category") ?: o.str("type")))
            }
        }

        /**
         * {"tag":[{name, type, count}]} or a bare array; types 0 general, 1 artist, 3 copyright, 4 character, 5 metadata.
         * Some forks (safebooru.org) ignore json=1 and answer <tags><tag type="1" name="…"/></tags>, so XML is read too.
         */
        fun parseTagApi(body: String): Map<String, TagCategory> {
            if (body.trimStart().startsWith("<")) {
                return XML_TAG.findAll(body).mapNotNull { m ->
                    val attrs = m.groupValues[1]
                    val name = XML_ATTR_NAME.find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
                    val type = XML_ATTR_TYPE.find(attrs)?.groupValues?.get(1)
                    name to TagCategory.parse(type)
                }.toMap()
            }
            val element = runCatching { parseJson(body) }.getOrNull() ?: return emptyMap()
            val items = when (element) {
                is JsonArray -> element
                is JsonObject -> element.arr("tag") ?: return emptyMap()
                else -> return emptyMap()
            }
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val name = o.str("name") ?: return@mapNotNull null
                name to TagCategory.parse(o.str("type"))
            }.toMap()
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
        private val XML_TAG = Regex("""<tag\s+([^>]*?)/?>""", RegexOption.IGNORE_CASE)
        private val XML_ATTR_NAME = Regex("""\bname="([^"]*)"""")
        private val XML_ATTR_TYPE = Regex("""\btype="([^"]*)"""")
        private val TAG_LI = Regex(
            """<li[^>]*class="[^"]*tag-type-([a-z]+)[^"]*"[^>]*>.*?href="[^"]*(?:[?&]|&amp;)tags=([^"&]+)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
    }
}
