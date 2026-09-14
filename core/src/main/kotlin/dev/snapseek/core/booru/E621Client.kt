package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * e621 / e926 API. Same shape as Danbooru but with nested objects and its own tag categories.
 *   posts         /posts.json?tags=…&page=…&limit=…   (≤ 320 per page; posts hidden by the default blacklist have file.url = null)
 *   autocomplete  /tags/autocomplete.json?search[name_matches]=…*&expiry=7
 * e621 rejects generic user agents, so this client identifies itself.
 */
class E621Client(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val root = baseUrl.trimEnd('/')
    override val kind = ServiceKind.E621
    override val maxPageSize = 320
    override val safeModeTags = listOf("rating:s")

    private val headers: Map<String, String>
        get() = credentials?.let { mapOf("Authorization" to BooruHttp.basicAuth(it)) } ?: emptyMap()

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val url = "$root/posts.json?tags=${tags.trim().urlEncoded()}&page=${page + 1}&limit=${limit.coerceIn(1, maxPageSize)}"
        return parsePosts(http.get(url, headers))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim().lowercase()
        if (term.length < 3) return emptyList()   // e621 wants three characters
        val url = "$root/tags/autocomplete.json?search%5Bname_matches%5D=${term.urlEncoded()}*&expiry=7"
        return runCatching { parseSuggestions(http.get(url, headers)).take(limit) }.getOrDefault(emptyList())
    }

    override fun postPageUrl(post: BooruPost): String = "$root/posts/${post.id}"

    companion object {
        private val TAG_GROUPS = mapOf(
            "artist" to TagCategory.ARTIST,
            "contributor" to TagCategory.ARTIST,
            "copyright" to TagCategory.COPYRIGHT,
            "character" to TagCategory.CHARACTER,
            "species" to TagCategory.SPECIES,
            "general" to TagCategory.GENERAL,
            "meta" to TagCategory.META,
            "lore" to TagCategory.LORE,
        )

        fun parsePosts(body: String): List<BooruPost> {
            val items = (parseJson(body) as? JsonObject)?.arr("posts") ?: (parseJson(body) as? JsonArray) ?: return emptyList()
            return items.mapNotNull { (it as? JsonObject)?.let(::postFrom) }
        }

        private fun postFrom(o: JsonObject): BooruPost? {
            val id = o.long("id") ?: return null
            val file = o.obj("file") ?: return null
            val fileUrl = file.str("url")?.takeIf { it.isNotBlank() } ?: return null
            val md5 = file.str("md5") ?: return null
            val sampleObj = o.obj("sample")
            val sample = sampleObj?.str("url")?.takeIf { it.isNotBlank() && (sampleObj.bool("has") ?: true) } ?: fileUrl
            val preview = o.obj("preview")?.str("url")?.takeIf { it.isNotBlank() } ?: sample
            val tagsObj = o.obj("tags")
            val categories = buildMap {
                TAG_GROUPS.forEach { (key, cat) -> tagsObj?.strList(key)?.forEach { put(it, cat) } }
            }
            val orderedTags = TAG_GROUPS.keys.flatMap { tagsObj?.strList(it) ?: emptyList() }
            return BooruPost(
                id = id,
                md5 = md5,
                previewUrl = preview,
                sampleUrl = sample,
                fileUrl = fileUrl,
                width = file.int("width") ?: 0,
                height = file.int("height") ?: 0,
                tags = orderedTags,
                rating = BooruPost.normaliseRating(o.str("rating"), sMeans = "safe"),
                score = o.obj("score")?.int("total"),
                source = o.arr("sources")?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } },
                owner = o.str("uploader_id"),
                hasSample = sample != fileUrl,
                tagCategories = categories,
                fileExtension = file.str("ext"),
            )
        }

        /** [{name, post_count, category}] with 0 general, 1 artist, 3 copyright, 4 character, 5 species, 7 meta, 8 lore. */
        fun parseSuggestions(body: String): List<TagSuggestion> {
            val items = parseJson(body) as? JsonArray ?: return emptyList()
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val name = o.str("name") ?: return@mapNotNull null
                TagSuggestion(name, o.int("post_count"), TagCategory.parseE621(o.int("category")))
            }
        }
    }
}
