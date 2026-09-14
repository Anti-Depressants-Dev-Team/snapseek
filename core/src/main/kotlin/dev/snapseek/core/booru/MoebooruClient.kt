package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Moebooru API: yande.re, konachan.com, konachan.net.
 *   posts         /post.json?tags=…&page=…&limit=…
 *   autocomplete  /tag.json?name=…*&order=count&limit=…
 *   tag types     /post.json?api_version=2&include_tags=1&tags=id:…   → {"posts":[…], "tags":{name: type}}
 */
class MoebooruClient(
    override val baseUrl: String,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val log = KotlinLogging.logger {}
    private val root = baseUrl.trimEnd('/')
    override val kind = ServiceKind.MOEBOORU
    override val maxPageSize = 100
    override val safeModeTags = listOf("rating:s")

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val url = "$root/post.json?tags=${tags.trim().urlEncoded()}&page=${page + 1}&limit=${limit.coerceIn(1, maxPageSize)}"
        return parsePosts(http.get(url), root)
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim().lowercase()
        if (term.isEmpty()) return emptyList()
        val url = "$root/tag.json?name=${term.urlEncoded()}*&order=count&limit=$limit"
        return runCatching { parseSuggestions(http.get(url)) }.getOrDefault(emptyList())
    }

    override suspend fun tagCategories(post: BooruPost): Map<String, TagCategory> =
        tagCategoryCache.getOrPut("$root/${post.id}") {
            runCatching { parseTagTypes(http.get("$root/post.json?api_version=2&include_tags=1&limit=1&tags=id:${post.id}")) }
                .onFailure { log.debug(it) { "No tag types for post ${post.id}" } }
                .getOrDefault(emptyMap())
        }

    override fun postPageUrl(post: BooruPost): String = "$root/post/show/${post.id}"

    companion object {
        private val tagCategoryCache = ConcurrentHashMap<String, Map<String, TagCategory>>()

        fun parsePosts(body: String, root: String): List<BooruPost> {
            val element = parseJson(body) ?: return emptyList()
            val items = when (element) {
                is JsonArray -> element
                is JsonObject -> element.arr("posts") ?: return emptyList()
                else -> return emptyList()
            }
            return items.mapNotNull { (it as? JsonObject)?.let { o -> postFrom(o, root) } }
        }

        private fun postFrom(o: JsonObject, root: String): BooruPost? {
            val id = o.long("id") ?: return null
            val md5 = o.str("md5") ?: return null
            val fileUrl = absolutize(o.str("file_url"), root) ?: return null
            val sample = absolutize(o.str("sample_url"), root) ?: absolutize(o.str("jpeg_url"), root) ?: fileUrl
            val preview = absolutize(o.str("preview_url"), root) ?: sample
            return BooruPost(
                id = id,
                md5 = md5,
                previewUrl = preview,
                sampleUrl = sample,
                fileUrl = fileUrl,
                width = o.int("width") ?: 0,
                height = o.int("height") ?: 0,
                tags = o.strList("tags"),
                rating = BooruPost.normaliseRating(o.str("rating"), sMeans = "safe"),
                score = o.int("score"),
                source = o.str("source")?.takeIf { it.isNotBlank() },
                owner = o.str("author"),
                hasSample = sample != fileUrl,
                fileExtension = o.str("file_ext"),
            )
        }

        /** [{name, count, type}] with 0 general, 1 artist, 3 copyright, 4 character, 5 circle, 6 faults. */
        fun parseSuggestions(body: String): List<TagSuggestion> {
            val items = parseJson(body) as? JsonArray ?: return emptyList()
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val name = o.str("name") ?: return@mapNotNull null
                TagSuggestion(name, o.int("count"), TagCategory.parse(o.str("type")))
            }
        }

        /** api_version=2 payload: {"posts":[…], "tags":{"name":"artist", …}} */
        fun parseTagTypes(body: String): Map<String, TagCategory> {
            val tags = (parseJson(body) as? JsonObject)?.obj("tags") ?: return emptyMap()
            return tags.mapNotNull { (name, type) -> (type as? JsonPrimitive)?.contentOrNull?.let { name to TagCategory.parse(it) } }.toMap()
        }
    }
}
