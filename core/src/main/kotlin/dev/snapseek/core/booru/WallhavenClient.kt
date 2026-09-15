package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Wallhaven's public API: https://wallhaven.cc/api/v1/search?q=…&page=…  (24 a page, anonymous for SFW).
 * An API key from account settings unlocks NSFW purity. Tags come from the per-wallpaper endpoint.
 *
 * Pseudo-tags: purity:100 (sfw) 110 (sfw+sketchy) 111   categories:111 (general,anime,people)
 *              sorting:relevance|toplist|random|date_added|views|favorites   atleast:1920x1080   ratios:16x9
 */
class WallhavenClient(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val root = "https://wallhaven.cc"
    override val kind = ServiceKind.WALLHAVEN
    override val maxPageSize = 24
    override val safeModeTags = listOf("purity:100")
    override val searchPlaceholder = "Search Wallhaven, e.g. mountains night  ·  sorting:toplist  ·  atleast:2560x1440  ·  purity:100"

    override fun categoryLabel(category: TagCategory): String = when (category) {
        TagCategory.COPYRIGHT -> "Category"
        TagCategory.GENERAL -> "Tags"
        else -> category.label
    }

    private val key: String get() = credentials?.apiKey?.takeIf { it.isNotBlank() }?.let { "&apikey=${it.urlEncoded()}" } ?: ""

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val (words, params) = extractParams(tags, setOf("purity", "categories", "sorting", "atleast", "ratios", "order", "topRange"))
        val q = words.joinToString(" ")
        val sorting = params["sorting"] ?: if (q.isEmpty()) "toplist" else "relevance"
        val url = buildString {
            append("$root/api/v1/search?page=${page + 1}&sorting=$sorting")
            if (q.isNotEmpty()) append("&q=${q.urlEncoded()}")
            params["purity"]?.let { append("&purity=$it") }
            params["categories"]?.let { append("&categories=$it") }
            params["atleast"]?.let { append("&atleast=$it") }
            params["ratios"]?.let { append("&ratios=$it") }
            params["order"]?.let { append("&order=$it") }
            params["topRange"]?.let { append("&topRange=$it") }
            append(key)
        }
        return parseSearch(http.get(url))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> = emptyList()

    override suspend fun tagCategories(post: BooruPost): Map<String, TagCategory> =
        tagCache.getOrPut(post.displayId) {
            runCatching { parseDetailTags(http.get("$root/api/v1/w/${post.displayId}?${key.trimStart('&')}")) }.getOrDefault(emptyMap())
        }

    override fun postPageUrl(post: BooruPost): String = "$root/w/${post.displayId}"

    companion object {
        private val tagCache = ConcurrentHashMap<String, Map<String, TagCategory>>()

        fun parseSearch(body: String): List<BooruPost> {
            val data = (parseJson(body) as? JsonObject)?.arr("data") ?: return emptyList()
            return data.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.str("id") ?: return@mapNotNull null
                val path = o.str("path") ?: return@mapNotNull null
                val thumbs = o.obj("thumbs")
                val purity = o.str("purity") ?: "sfw"
                val category = o.str("category")
                BooruPost(
                    id = id.toLongOrNull(36) ?: BooruPost.idFrom(id),
                    md5 = "",
                    previewUrl = thumbs?.str("small") ?: thumbs?.str("large") ?: path,
                    sampleUrl = thumbs?.str("large") ?: path,
                    fileUrl = path,
                    width = o.int("dimension_x") ?: 0,
                    height = o.int("dimension_y") ?: 0,
                    tags = listOfNotNull(category),
                    rating = when (purity) { "sfw" -> "safe"; "sketchy" -> "sketchy"; else -> "explicit" },
                    score = o.int("favorites"),
                    source = o.str("source")?.takeIf { it.isNotBlank() },
                    owner = null,
                    hasSample = true,
                    tagCategories = category?.let { mapOf(it to TagCategory.COPYRIGHT) },
                    fileExtension = o.str("file_type")?.substringAfter('/'),
                    sourceId = id,
                )
            }
        }

        fun parseDetailTags(body: String): Map<String, TagCategory> {
            val data = (parseJson(body) as? JsonObject)?.obj("data") ?: return emptyMap()
            val out = linkedMapOf<String, TagCategory>()
            data.str("category")?.let { out[it] = TagCategory.COPYRIGHT }
            data.arr("tags")?.forEach { t -> (t as? JsonObject)?.str("name")?.let { out[it] = TagCategory.GENERAL } }
            return out
        }
    }
}
