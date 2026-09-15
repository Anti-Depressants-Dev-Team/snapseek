package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import java.net.URI

/**
 * The catch-all for sites without an API: fetches a page and lists the images on it.
 * The service URL is a template: {q} is replaced by the search, {page} by the 1-based page number.
 *   https://wallpapers.com/{q}          https://example.com/search?q={q}&page={page}
 * Picks the largest srcset candidate, follows lazy-load attributes, drops icons and tiny images, and uses the
 * enclosing link as the page URL. Good enough to browse and save; sites that render everything with JavaScript
 * will come back empty, and then the website mode is the way to go.
 */
class WebGridClient(
    override val baseUrl: String,
    private val http: BooruHttp = BooruHttp.browserLike,
) : BooruClient {
    override val kind = ServiceKind.WEB_GRID
    override val maxPageSize = 200
    override val safeModeTags = emptyList<String>()
    override val supportsEmptyQuery: Boolean = !baseUrl.contains("{q}")
    override val searchPlaceholder = if (baseUrl.contains("{q}")) "Search this site" else "This page lists its images; type to filter by file name"
    override fun splitQuery(query: String): List<String> = query.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
    override fun categoryLabel(category: TagCategory): String = if (category == TagCategory.GENERAL) "Alt text" else category.label

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val q = tags.trim()
        val hasPage = baseUrl.contains("{page}")
        if (page > 0 && !hasPage) return emptyList()
        if (q.isEmpty() && baseUrl.contains("{q}")) return emptyList()
        val url = baseUrl.replace("{q}", q.replace(' ', '-').urlEncoded()).replace("{page}", (page + 1).toString())
        val html = http.get(url, accept = "text/html, */*;q=0.5")
        val all = extractImages(html, url)
        return if (q.isNotEmpty() && !baseUrl.contains("{q}")) all.filter { it.fileUrl.contains(q, ignoreCase = true) || it.title?.contains(q, ignoreCase = true) == true } else all
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> = emptyList()
    override fun postPageUrl(post: BooruPost): String = post.source ?: post.fileUrl

    companion object {
        private val ANCHOR_IMG = Regex("""<a\b[^>]*?href="([^"]+)"[^>]*>(?:(?!</a>).)*?(<img\b[^>]*>)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val IMG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val SKIP = Regex("""(icon|logo|avatar|sprite|emoji|badge|pixel|spinner|loading|blank|placeholder|\.svg|^data:)""", RegexOption.IGNORE_CASE)

        private fun attr(tag: String, name: String): String? =
            Regex("""\b$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.unescapeXml()
                ?: Regex("""\b$name\s*=\s*'([^']*)'""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.unescapeXml()

        private fun largestFromSrcset(srcset: String): String? =
            srcset.split(',').mapNotNull { c ->
                val parts = c.trim().split(Regex("\\s+"))
                val url = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                url to (parts.getOrNull(1)?.trimEnd('w', 'x')?.toFloatOrNull() ?: 0f)
            }.maxByOrNull { it.second }?.first

        fun extractImages(html: String, pageUrl: String): List<BooruPost> {
            val root = runCatching { URI(pageUrl).let { "${it.scheme}://${it.host}" } }.getOrDefault(pageUrl)
            val pageFor = HashMap<String, String>()
            ANCHOR_IMG.findAll(html).forEach { m -> pageFor[m.groupValues[2]] = m.groupValues[1] }
            val seen = HashSet<String>()
            return IMG.findAll(html).mapNotNull { m ->
                val tag = m.value
                val candidate = attr(tag, "data-srcset")?.let(::largestFromSrcset)
                    ?: attr(tag, "srcset")?.let(::largestFromSrcset)
                    ?: attr(tag, "data-src") ?: attr(tag, "data-original") ?: attr(tag, "data-lazy-src") ?: attr(tag, "data-url")
                    ?: attr(tag, "src")
                if (candidate == null || candidate.startsWith("data:") || candidate.startsWith("javascript:") || candidate.startsWith("blob:")) return@mapNotNull null
                val url = absolutize(candidate, root) ?: return@mapNotNull null
                if (SKIP.containsMatchIn(url) || !seen.add(url)) return@mapNotNull null
                val w = attr(tag, "width")?.toIntOrNull() ?: 0
                val h = attr(tag, "height")?.toIntOrNull() ?: 0
                if (w in 1..99 && h in 1..99) return@mapNotNull null
                val alt = attr(tag, "alt")?.trim()?.takeIf { it.isNotEmpty() }
                val link = pageFor[tag]?.let { absolutize(it, root) }
                val categories = alt?.let { mapOf(it to TagCategory.GENERAL) } ?: emptyMap()
                BooruPost(
                    id = BooruPost.idFrom(url),
                    md5 = "",
                    previewUrl = url,
                    sampleUrl = url,
                    fileUrl = url,
                    width = w,
                    height = h,
                    tags = categories.keys.toList(),
                    rating = "",
                    score = null,
                    source = link,
                    owner = null,
                    hasSample = false,
                    tagCategories = categories,
                    title = alt,
                )
            }.toList()
        }
    }
}
