package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind

/**
 * DeviantArt through its public RSS backend, which needs no key:
 *   https://backend.deviantart.com/rss.xml?type=deviation&q=…&offset=…    (60 items a page)
 * The query is DeviantArt's own search syntax: "boost:popular cats", "by:someartist", "in:digitalart".
 * The largest image the feed offers is the 800px preview; full originals need OAuth, which the browser
 * tab still gives you when you're logged in there.
 */
class DeviantArtClient(
    override val baseUrl: String,
    private val http: BooruHttp = BooruHttp.browserLike,
) : BooruClient {
    override val kind = ServiceKind.DEVIANTART
    override val maxPageSize = 60
    override val safeModeTags = emptyList<String>()
    override val searchPlaceholder = "Search DeviantArt, e.g. boost:popular fantasy landscape  ·  by:username  ·  in:digitalart"

    override fun splitQuery(query: String): List<String> = query.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()

    override fun categoryLabel(category: TagCategory): String = when (category) {
        TagCategory.COPYRIGHT -> "Category"
        else -> category.label
    }

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val q = tags.trim().ifEmpty { "boost:popular" }
        val url = "https://backend.deviantart.com/rss.xml?type=deviation&q=${q.urlEncoded()}&offset=${page * maxPageSize}"
        return parseRss(http.get(url, accept = "application/rss+xml, application/xml, text/xml, */*;q=0.5"))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> = emptyList()

    override fun postPageUrl(post: BooruPost): String = post.source?.takeIf { it.contains("deviantart.com") } ?: "https://www.deviantart.com/"

    companion object {
        private val ITEM = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
        private val TITLE = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        private val LINK = Regex("<link>(.*?)</link>")
        private val CONTENT = Regex("""<media:content\s+([^>]*?)/?>""")
        private val THUMB = Regex("""<media:thumbnail\s+([^>]*?)/?>""")
        private val CREDIT = Regex("""<media:credit[^>]*role="author"[^>]*>([^<]*)</media:credit>""")
        private val RATING = Regex("<media:rating>([^<]*)</media:rating>")
        private val CATEGORY = Regex("""<media:category[^>]*label="([^"]*)"""")
        private val ID_IN_LINK = Regex("""-(\d+)/?$""")

        private fun attr(attrs: String, name: String): String? = Regex("""\b$name="([^"]*)"""").find(attrs)?.groupValues?.get(1)?.unescapeXml()

        fun parseRss(xml: String): List<BooruPost> = ITEM.findAll(xml).mapNotNull { m ->
            val item = m.groupValues[1]
            val link = LINK.find(item)?.groupValues?.get(1)?.trim()?.unescapeXml() ?: return@mapNotNull null
            val content = CONTENT.findAll(item).map { it.groupValues[1] }
                .firstOrNull { attr(it, "medium")?.let { medium -> medium == "image" } ?: true } ?: return@mapNotNull null
            val fileUrl = attr(content, "url") ?: return@mapNotNull null
            val thumbs = THUMB.findAll(item).map { it.groupValues[1] }.toList()
            val preview = thumbs.map { attr(it, "url") to (attr(it, "width")?.toIntOrNull() ?: 0) }
                .filter { it.first != null }
                .maxByOrNull { it.second }?.first ?: fileUrl
            val author = CREDIT.findAll(item).map { it.groupValues[1].trim().unescapeXml() }.firstOrNull { !it.startsWith("http") }
            val category = CATEGORY.find(item)?.groupValues?.get(1)?.unescapeXml()
            val categories = buildMap<String, TagCategory> {
                author?.takeIf { it.isNotEmpty() }?.let { put(it, TagCategory.ARTIST) }
                category?.takeIf { it.isNotEmpty() }?.let { put(it, TagCategory.COPYRIGHT) }
            }
            val idText = ID_IN_LINK.find(link)?.groupValues?.get(1)
            BooruPost(
                id = idText?.toLongOrNull() ?: BooruPost.idFrom(link),
                md5 = "",
                previewUrl = preview,
                sampleUrl = fileUrl,
                fileUrl = fileUrl,
                width = attr(content, "width")?.toIntOrNull() ?: 0,
                height = attr(content, "height")?.toIntOrNull() ?: 0,
                tags = categories.keys.toList(),
                rating = if (RATING.find(item)?.groupValues?.get(1)?.trim() == "adult") "explicit" else "general",
                score = null,
                source = link,
                owner = author,
                hasSample = false,
                tagCategories = categories,
                title = TITLE.find(item)?.groupValues?.get(1)?.trim()?.unescapeXml()?.takeIf { it.isNotEmpty() },
                sourceId = idText,
            )
        }.toList()
    }
}
