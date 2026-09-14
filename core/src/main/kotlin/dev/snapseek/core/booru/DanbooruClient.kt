package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Danbooru 2 API: danbooru.donmai.us, safebooru.donmai.us, aibooru.online and other Danbooru forks.
 *   posts         /posts.json?tags=…&page=…&limit=…            (anonymous: two tags per search, 200 per page)
 *   autocomplete  /autocomplete.json?search[query]=…&search[type]=tag_query&limit=…
 * Tag categories ride along in the post payload as tag_string_artist and friends.
 */
class DanbooruClient(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val root = baseUrl.trimEnd('/')
    override val kind = ServiceKind.DANBOORU
    override val maxPageSize = 200
    override val safeModeTags = listOf("rating:g")

    private val auth: String
        get() = credentials?.let { "&login=${it.login.urlEncoded()}&api_key=${it.apiKey.urlEncoded()}" } ?: ""

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val url = "$root/posts.json?tags=${tags.trim().urlEncoded()}&page=${page + 1}&limit=${limit.coerceIn(1, maxPageSize)}$auth"
        return parsePosts(http.get(url))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim().lowercase()
        if (term.isEmpty()) return emptyList()
        val url = "$root/autocomplete.json?search%5Bquery%5D=${term.urlEncoded()}&search%5Btype%5D=tag_query&limit=$limit$auth"
        return runCatching { parseSuggestions(http.get(url)) }.getOrDefault(emptyList())
    }

    override fun postPageUrl(post: BooruPost): String = "$root/posts/${post.id}"

    companion object {
        fun parsePosts(body: String): List<BooruPost> {
            val items = parseJson(body) as? JsonArray ?: return emptyList()
            return items.mapNotNull { (it as? JsonObject)?.let(::postFrom) }
        }

        private fun postFrom(o: JsonObject): BooruPost? {
            val id = o.long("id") ?: return null
            val md5 = o.str("md5") ?: return null
            val fileUrl = o.str("file_url")?.takeIf { it.isNotBlank() } ?: return null   // gold-only or banned posts have none
            val large = o.str("large_file_url")?.takeIf { it.isNotBlank() }
            val preview = o.str("preview_file_url")?.takeIf { it.isNotBlank() } ?: large ?: fileUrl
            val sample = large ?: fileUrl
            val categories = buildMap<String, TagCategory> {
                fun addAll(key: String, category: TagCategory) = o.strList(key).forEach { tag -> put(tag, category) }
                addAll("tag_string_artist", TagCategory.ARTIST)
                addAll("tag_string_copyright", TagCategory.COPYRIGHT)
                addAll("tag_string_character", TagCategory.CHARACTER)
                addAll("tag_string_general", TagCategory.GENERAL)
                addAll("tag_string_meta", TagCategory.META)
            }
            return BooruPost(
                id = id,
                md5 = md5,
                previewUrl = preview,
                sampleUrl = sample,
                fileUrl = fileUrl,
                width = o.int("image_width") ?: 0,
                height = o.int("image_height") ?: 0,
                tags = o.strList("tag_string"),
                rating = BooruPost.normaliseRating(o.str("rating"), sMeans = "sensitive"),
                score = o.int("score"),
                source = o.str("source")?.takeIf { it.isNotBlank() },
                owner = o.str("uploader_id"),
                hasSample = (o.bool("has_large") ?: false) && sample != fileUrl,
                tagCategories = categories,
                fileExtension = o.str("file_ext"),
            )
        }

        fun parseSuggestions(body: String): List<TagSuggestion> {
            val items = parseJson(body) as? JsonArray ?: return emptyList()
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val value = o.str("value") ?: return@mapNotNull null
                TagSuggestion(value, o.int("post_count"), TagCategory.parse(o.str("category")))
            }
        }
    }
}
