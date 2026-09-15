package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Pinterest through the same internal "resource" endpoints its website uses (as gallery-dl does):
 *   GET /resource/BaseSearchResource/get/?source_url=…&data={"options":{"query":…,"scope":"pins","bookmarks":[…]}}
 * Anonymous, no key. Pages are chained by the bookmark token each response returns. Pins carry the original
 * image URL plus 236/474/736 renders, an md5 image signature, the pinner, the board and visual annotations.
 */
class PinterestClient(
    override val baseUrl: String,
    private val http: BooruHttp = BooruHttp.browserLike,
) : BooruClient {
    private val root = baseUrl.trimEnd('/').let { if (it.contains("pinterest")) it else "https://www.pinterest.com" }
    private val csrf = UUID.randomUUID().toString().replace("-", "")
    private val cursors = CursorCache()

    override val kind = ServiceKind.PINTEREST
    override val maxPageSize = 25
    override val safeModeTags = emptyList<String>()
    override val supportsEmptyQuery = false
    override val searchPlaceholder = "Search Pinterest, e.g. minimalist desk setup  ·  watercolor landscape"

    /** A Pinterest search is a phrase, not a tag list. */
    override fun splitQuery(query: String): List<String> = query.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()

    override fun categoryLabel(category: TagCategory): String = when (category) {
        TagCategory.ARTIST -> "Pinner"
        TagCategory.COPYRIGHT -> "Board"
        TagCategory.GENERAL -> "Related"
        else -> category.label
    }

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val q = tags.trim()
        if (q.isEmpty()) return emptyList()
        val bookmark = cursors.cursorFor(q, page)
        if (bookmark == CursorCache.MISSING) return emptyList()

        val options = buildJsonObject {
            put("query", q)
            put("scope", "pins")
            put("rs", "typed")
            if (bookmark != null) put("bookmarks", buildJsonArray { add(bookmark) })
        }
        val data = buildJsonObject { put("options", options); put("context", buildJsonObject {}) }.toString()
        val sourceUrl = "/search/pins/?q=${q.urlEncoded()}&rs=typed"
        val url = "$root/resource/BaseSearchResource/get/?source_url=${sourceUrl.urlEncoded()}&data=${data.urlEncoded()}"

        val body = http.get(url, headers(sourceUrl))
        val (posts, next) = parseSearch(body)
        cursors.store(q, page + 1, next)
        return posts
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim()
        if (term.length < 2) return emptyList()
        val options = buildJsonObject {
            put("term", term)
            put("pin_scope", "pins")
            put("count", limit)
            put("enable_autocomplete_redirects", false)
            put("show_full_search_url", false)
        }
        val data = buildJsonObject { put("options", options); put("context", buildJsonObject {}) }.toString()
        val sourceUrl = "/search/pins/?q=${term.urlEncoded()}"
        val url = "$root/resource/AdvancedTypeaheadResource/get/?source_url=${sourceUrl.urlEncoded()}&data=${data.urlEncoded()}"
        return runCatching { parseSuggestions(http.get(url, headers(sourceUrl))).take(limit) }.getOrDefault(emptyList())
    }

    override fun postPageUrl(post: BooruPost): String = "$root/pin/${post.id}/"

    private fun headers(sourceUrl: String): Map<String, String> = mapOf(
        "Accept" to "application/json, text/javascript, */*, q=0.01",
        "Accept-Language" to "en-US,en;q=0.9",
        "Referer" to "$root/",
        "X-Requested-With" to "XMLHttpRequest",
        "X-APP-VERSION" to "a89153f",
        "X-CSRFToken" to csrf,
        "X-Pinterest-AppState" to "active",
        "X-Pinterest-Source-Url" to sourceUrl,
        "X-Pinterest-PWS-Handler" to "www/search/[scope].js",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "Cookie" to "csrftoken=$csrf",
    )

    companion object {
        /** Returns the pins and the bookmark for the next page, or null when the feed is exhausted. */
        fun parseSearch(body: String): Pair<List<BooruPost>, String?> {
            val rootObj = parseJson(body) as? JsonObject ?: return emptyList<BooruPost>() to null
            val response = rootObj.obj("resource_response") ?: return emptyList<BooruPost>() to null
            val dataEl = response["data"]
            val results: JsonArray = when (dataEl) {
                is JsonArray -> dataEl
                is JsonObject -> dataEl.arr("results") ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            val posts = results.mapNotNull { (it as? JsonObject)?.let(::pinFrom) }
            val bookmark = rootObj.obj("resource")?.obj("options")?.arr("bookmarks")?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: response.str("bookmark")
            val next = bookmark?.takeIf { it.isNotBlank() && it != "-end-" && !it.startsWith("Y2JOb25lO") }
            return posts to next
        }

        private fun pinFrom(o: JsonObject): BooruPost? {
            if (o.str("type")?.let { it != "pin" } == true) return null
            val id = o.long("id") ?: return null
            val images = o.obj("images") ?: return null
            val orig = images.obj("orig") ?: return null
            val fileUrl = orig.str("url") ?: return null
            fun render(key: String) = images.obj(key)?.str("url")
            val preview = render("236x") ?: render("170x") ?: fileUrl
            val sample = render("736x") ?: render("474x") ?: fileUrl

            val pinner = o.obj("pinner")?.str("username")?.takeIf { it.isNotBlank() }
            val board = o.obj("board")?.str("name")?.takeIf { it.isNotBlank() }
            val annotations = o.obj("pin_join")?.strList("visual_annotation").orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
            val categories = buildMap<String, TagCategory> {
                pinner?.let { put(it, TagCategory.ARTIST) }
                board?.let { put(it, TagCategory.COPYRIGHT) }
                annotations.forEach { put(it, TagCategory.GENERAL) }
            }
            val title = (o.str("grid_title") ?: o.str("title"))?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
                ?: o.str("description")?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
            val reactions = o.obj("reaction_counts")?.values?.sumOf { (it as? JsonPrimitive)?.intOrNull ?: 0 }
            return BooruPost(
                id = id,
                md5 = o.str("image_signature") ?: "",
                previewUrl = preview,
                sampleUrl = sample,
                fileUrl = fileUrl,
                width = orig.int("width") ?: 0,
                height = orig.int("height") ?: 0,
                tags = categories.keys.toList(),
                rating = "",
                score = reactions?.takeIf { it > 0 } ?: o.int("repin_count"),
                source = o.str("link")?.takeIf { it.isNotBlank() },
                owner = pinner,
                hasSample = sample != fileUrl,
                tagCategories = categories,
                title = title,
            )
        }

        /** AdvancedTypeahead returns {"resource_response":{"data":{"items":[{"label":…}]}}}. Shape is best-effort. */
        fun parseSuggestions(body: String): List<TagSuggestion> {
            val data = (parseJson(body) as? JsonObject)?.obj("resource_response")?.get("data")
            val items: JsonArray = when (data) {
                is JsonArray -> data
                is JsonObject -> data.arr("items") ?: JsonArray(emptyList())
                else -> return emptyList()
            }
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val label = (o.str("label") ?: o.str("query") ?: o.str("term"))?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                TagSuggestion(label, null, TagCategory.GENERAL)
            }.distinctBy { it.name.lowercase() }
        }
    }
}
