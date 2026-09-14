package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import kotlinx.serialization.json.JsonObject

/**
 * Philomena API: derpibooru.org, ponybooru.org, furbooru.org and other Philomena instances.
 *   posts         /api/v1/json/search/images?q=…&page=…&per_page=…   (tags are comma-separated, ≤ 50 per page)
 *   autocomplete  /api/v1/json/search/tags?q=name:…*&per_page=…
 * The site's content filter applies to anonymous requests; an API key from account settings lifts it.
 */
class PhilomenaClient(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val root = baseUrl.trimEnd('/')
    override val kind = ServiceKind.PHILOMENA
    override val maxPageSize = 50
    override val tagSeparator = ", "
    override val safeModeTags = listOf("safe")

    private val key: String
        get() = credentials?.apiKey?.takeIf { it.isNotBlank() }?.let { "&key=${it.urlEncoded()}" } ?: ""

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val q = tags.trim().ifEmpty { "*" }
        val url = "$root/api/v1/json/search/images?q=${q.urlEncoded()}&page=${page + 1}&per_page=${limit.coerceIn(1, maxPageSize)}&sf=first_seen_at&sd=desc$key"
        return parsePosts(http.get(url), root)
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim().lowercase()
        if (term.isEmpty()) return emptyList()
        val url = "$root/api/v1/json/search/tags?q=${"name:$term*".urlEncoded()}&per_page=$limit$key"
        return runCatching { parseSuggestions(http.get(url)) }.getOrDefault(emptyList())
    }

    override suspend fun tagCategories(post: BooruPost): Map<String, TagCategory> =
        post.tags.associateWith { categoryOf(it) }

    override fun postPageUrl(post: BooruPost): String = "$root/images/${post.id}"

    companion object {
        private val RATING_TAGS = setOf("safe", "suggestive", "questionable", "explicit", "semi-grimdark", "grimdark", "grotesque")

        fun categoryOf(tag: String): TagCategory = when {
            tag.startsWith("artist:") -> TagCategory.ARTIST
            tag.startsWith("oc:") -> TagCategory.CHARACTER
            tag.startsWith("spoiler:") || tag in RATING_TAGS -> TagCategory.META
            else -> TagCategory.GENERAL
        }

        fun parsePosts(body: String, root: String): List<BooruPost> {
            val items = (parseJson(body) as? JsonObject)?.arr("images") ?: return emptyList()
            return items.mapNotNull { (it as? JsonObject)?.let { o -> postFrom(o, root) } }
        }

        private fun postFrom(o: JsonObject, root: String): BooruPost? {
            val id = o.long("id") ?: return null
            if (o.bool("hidden_from_users") == true) return null
            val reps = o.obj("representations")
            val fileUrl = absolutize(o.str("view_url"), root) ?: absolutize(reps?.str("full"), root) ?: return null
            val preview = absolutize(reps?.str("thumb"), root) ?: absolutize(reps?.str("thumb_small"), root) ?: fileUrl
            val sample = absolutize(reps?.str("large"), root) ?: absolutize(reps?.str("medium"), root) ?: fileUrl
            val tags = o.strList("tags")
            val hash = o.str("sha512_hash") ?: o.str("orig_sha512_hash") ?: return null
            return BooruPost(
                id = id,
                md5 = hash.take(32),
                previewUrl = preview,
                sampleUrl = sample,
                fileUrl = fileUrl,
                width = o.int("width") ?: 0,
                height = o.int("height") ?: 0,
                tags = tags,
                rating = tags.firstOrNull { it in RATING_TAGS } ?: "",
                score = o.int("score"),
                source = o.str("source_url")?.takeIf { it.isNotBlank() } ?: o.strList("source_urls").firstOrNull(),
                owner = o.str("uploader"),
                hasSample = sample != fileUrl,
                tagCategories = tags.associateWith { categoryOf(it) },
                fileExtension = o.str("format"),
            )
        }

        /** {"tags":[{name, images, category}]} with categories like origin, character, oc, species, rating, spoiler. */
        fun parseSuggestions(body: String): List<TagSuggestion> {
            val items = (parseJson(body) as? JsonObject)?.arr("tags") ?: return emptyList()
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val name = o.str("name") ?: return@mapNotNull null
                val category = o.str("category")?.let { TagCategory.parse(it) }?.takeIf { it != TagCategory.UNKNOWN } ?: categoryOf(name)
                TagSuggestion(name, o.int("images"), category)
            }
        }
    }
}
