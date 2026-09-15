package dev.snapseek.core.booru

import dev.snapseek.core.download.CookieSource
import dev.snapseek.core.model.ServiceKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Pixiv through the website's AJAX API, which answers anonymously for all-ages work:
 *   search    /ajax/search/artworks/{word}?word=…&order=date_d&mode=all&p=…&s_mode=s_tag_full&type=all
 *   browse    /ranking.php?mode=daily&format=json&p=…            (used when the search is empty)
 *   suggest   /rpc/cps.php?keyword=…
 * Browser cookies from the embedded Chromium are sent along when the user is logged in there, which
 * unlocks R-18 results and the popularity sort. Originals need a Referer; the download pipeline adds it.
 *
 * Pseudo-tags: mode:safe|r18|all   order:date_d|date|popular_d   type:illust|manga|ugoira|all
 */
class PixivClient(
    override val baseUrl: String,
    private val cookies: CookieSource? = null,
    private val http: BooruHttp = BooruHttp.browserLike,
) : BooruClient {
    private val root = "https://www.pixiv.net"

    override val kind = ServiceKind.PIXIV
    override val maxPageSize = 60
    override val safeModeTags = listOf("mode:safe")
    override val resolveOriginals = true
    override val searchPlaceholder = "Search Pixiv tags, e.g. 風景 or landscape  ·  mode:safe  ·  order:popular_d (premium)"

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val (words, params) = extractParams(tags, setOf("mode", "order", "type", "s_mode"))
        val word = words.joinToString(" ")
        val headers = headers()
        if (word.isEmpty()) {
            val mode = params["mode"]?.takeIf { it in RANKING_MODES } ?: "daily"
            if (page >= 10) return emptyList()   // the ranking has 500 entries
            return parseRanking(http.get("$root/ranking.php?mode=$mode&format=json&p=${page + 1}", headers))
        }
        val url = "$root/ajax/search/artworks/${word.urlEncoded()}?word=${word.urlEncoded()}" +
            "&order=${params["order"] ?: "date_d"}&mode=${params["mode"] ?: "all"}&p=${page + 1}" +
            "&s_mode=${params["s_mode"] ?: "s_tag_full"}&type=${params["type"] ?: "all"}&lang=en"
        return parseSearch(http.get(url, headers))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim()
        if (term.isEmpty()) return emptyList()
        return runCatching { parseSuggestions(http.get("$root/rpc/cps.php?keyword=${term.urlEncoded()}&lang=en", headers())).take(limit) }
            .getOrDefault(emptyList())
    }

    override fun postPageUrl(post: BooruPost): String = "$root/artworks/${post.id}"

    private suspend fun headers(): Map<String, String> = buildMap {
        put("Referer", "$root/")
        put("Accept", "application/json")
        cookies?.cookieHeaderFor("$root/")?.let { put("Cookie", it) }
    }

    companion object {
        private val RANKING_MODES = setOf("daily", "weekly", "monthly", "rookie", "original", "male", "female", "daily_r18", "weekly_r18")

        /** …/c/250x250_80_a2/img-master/img/…/ID_p0_square1200.jpg → …/img-master/img/…/ID_p0_master1200.jpg */
        private val THUMB = Regex("""^(https://i\.pximg\.net)/c/[^/]+/(img-master/img/.+?/\d+_p\d+)_(?:square|master|custom)\d+\.\w+$""")
        private val MASTER = Regex("""^(https://i\.pximg\.net)/(img-master/img/.+?/\d+_p\d+)_master\d+\.\w+$""")

        fun masterOf(url: String): String =
            THUMB.matchEntire(url)?.let { "${it.groupValues[1]}/${it.groupValues[2]}_master1200.jpg" }
                ?: MASTER.matchEntire(url)?.let { "${it.groupValues[1]}/${it.groupValues[2]}_master1200.jpg" }
                ?: url

        fun parseSearch(body: String): List<BooruPost> {
            val data = (parseJson(body) as? JsonObject)?.obj("body")?.obj("illustManga")?.arr("data") ?: return emptyList()
            return data.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.long("id") ?: return@mapNotNull null
                val thumb = o.str("url") ?: return@mapNotNull null
                val user = o.str("userName")?.takeIf { it.isNotBlank() }
                val tags = o.strList("tags")
                post(
                    id = id, thumb = thumb, title = o.str("title"), user = user, tags = tags,
                    width = o.int("width") ?: 0, height = o.int("height") ?: 0,
                    pages = o.int("pageCount") ?: 1, xRestrict = o.int("xRestrict") ?: 0, illustType = o.int("illustType") ?: 0,
                    score = null,
                )
            }
        }

        fun parseRanking(body: String): List<BooruPost> {
            val contents = (parseJson(body) as? JsonObject)?.arr("contents") ?: return emptyList()
            return contents.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.long("illust_id") ?: return@mapNotNull null
                val thumb = o.str("url") ?: return@mapNotNull null
                post(
                    id = id, thumb = thumb, title = o.str("title"), user = o.str("user_name"), tags = o.strList("tags"),
                    width = o.int("width") ?: 0, height = o.int("height") ?: 0,
                    pages = o.int("illust_page_count") ?: 1, xRestrict = 0, illustType = o.int("illust_type") ?: 0,
                    score = o.int("rating_count"),
                )
            }
        }

        private fun post(id: Long, thumb: String, title: String?, user: String?, tags: List<String>, width: Int, height: Int, pages: Int, xRestrict: Int, illustType: Int, score: Int?): BooruPost {
            val master = masterOf(thumb)
            val categories = buildMap<String, TagCategory> {
                user?.let { put(it, TagCategory.ARTIST) }
                tags.forEach { put(it, TagCategory.GENERAL) }
                if (pages > 1) put("${pages}_pages", TagCategory.META)
                if (illustType == 1) put("manga", TagCategory.META)
                if (illustType == 2) put("ugoira", TagCategory.META)
            }
            return BooruPost(
                id = id,
                md5 = "",
                previewUrl = thumb,
                sampleUrl = master,
                fileUrl = master,
                width = width,
                height = height,
                tags = categories.keys.toList(),
                rating = when (xRestrict) { 0 -> "general"; else -> "explicit" },
                score = score,
                source = null,
                owner = user,
                hasSample = false,
                tagCategories = categories,
                title = title?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        fun parseSuggestions(body: String): List<TagSuggestion> {
            val candidates = (parseJson(body) as? JsonObject)?.arr("candidates") ?: return emptyList()
            return candidates.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val name = o.str("tag_name") ?: return@mapNotNull null
                TagSuggestion(name, o.int("access_count"), TagCategory.GENERAL)
            }
        }

        @Suppress("unused")
        private fun JsonArray.size2() = size
    }
}
