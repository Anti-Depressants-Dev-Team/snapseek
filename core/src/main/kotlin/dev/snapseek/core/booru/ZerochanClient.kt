package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import kotlinx.serialization.json.JsonObject

/**
 * Zerochan's JSON listing: https://www.zerochan.net/{Tag,Tag}?json&l=…&p=…  (anonymous with an honest user agent).
 * Thumbnails arrive as AVIF, which Skia can't decode, so the JPEG rendition of the same path is used.
 * Full-size files live at static.zerochan.net/{Primary.Tag}.full.{id}.{png|jpg}; the resolver tries both.
 */
class ZerochanClient(
    override val baseUrl: String,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val root = "https://www.zerochan.net"
    override val kind = ServiceKind.ZEROCHAN
    override val maxPageSize = 50
    override val tagSeparator = ", "
    override val safeModeTags = emptyList<String>()
    override val resolveOriginals = true
    override val searchPlaceholder = "Search Zerochan tags, comma-separated, e.g. Genshin Impact, Lumine"

    override fun categoryLabel(category: TagCategory): String = when (category) {
        TagCategory.COPYRIGHT -> "Series"
        TagCategory.GENERAL -> "Tags"
        else -> category.label
    }

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val parts = splitQuery(tags)
        val path = if (parts.isEmpty()) "" else "/" + parts.joinToString(",") { it.replace(' ', '+').urlEncodedPath() }
        val url = "$root$path?json&l=${limit.coerceIn(1, maxPageSize)}&p=${page + 1}"
        return parseListing(http.get(url))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim()
        if (term.length < 2) return emptyList()
        return runCatching { parseSuggestions(http.get("$root/suggest?q=${term.urlEncoded()}&limit=$limit", accept = "text/plain, */*")) }.getOrDefault(emptyList())
    }

    override fun postPageUrl(post: BooruPost): String = "$root/${post.id}"

    private fun String.urlEncodedPath() = urlEncoded().replace("%2B", "+")

    companion object {
        fun parseListing(body: String): List<BooruPost> {
            val items = (parseJson(body) as? JsonObject)?.arr("items") ?: return emptyList()
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.long("id") ?: return@mapNotNull null
                val thumb = o.str("thumbnail") ?: return@mapNotNull null
                val primary = o.str("tag") ?: o.str("primary") ?: ""
                val tags = o.strList("tags")
                val jpegThumb = thumb.replace(Regex("""\.(avif|webp)$"""), ".jpg")
                val sample = jpegThumb.replace("/240/", "/600/")
                val full = "https://static.zerochan.net/${primary.trim().replace(' ', '.')}.full.$id.png"
                val categories = buildMap<String, TagCategory> {
                    if (primary.isNotBlank()) put(primary, TagCategory.COPYRIGHT)
                    tags.forEach { put(it, TagCategory.GENERAL) }
                }
                BooruPost(
                    id = id,
                    md5 = o.str("md5") ?: o.str("hash") ?: "",
                    previewUrl = jpegThumb,
                    sampleUrl = sample,
                    fileUrl = full,
                    width = o.int("width") ?: 0,
                    height = o.int("height") ?: 0,
                    tags = categories.keys.toList(),
                    rating = "safe",
                    score = null,
                    source = o.str("source")?.takeIf { it.isNotBlank() },
                    owner = null,
                    hasSample = true,
                    tagCategories = categories,
                    fileExtension = "png",
                )
            }
        }

        /** Lines of "Name|Type|Parent". */
        fun parseSuggestions(body: String): List<TagSuggestion> = body.lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            val name = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val category = when (parts.getOrNull(1)?.trim()?.lowercase()) {
                "character" -> TagCategory.CHARACTER
                "mangaka", "studio", "artbook" -> TagCategory.ARTIST
                "game", "series", "anime", "visual novel", "source", "manga", "novel" -> TagCategory.COPYRIGHT
                "meta" -> TagCategory.META
                else -> TagCategory.GENERAL
            }
            TagSuggestion(name, null, category)
        }.toList()
    }
}
