package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

private fun requireKey(credentials: BooruCredentials?, site: String, where: String): String =
    credentials?.apiKey?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw MissingCredentialsException("$site needs a free API key. $where Then add it under Manage services → the key icon.")

/**
 * Giphy API v1 with the user's own key (free, instant at developers.giphy.com; Giphy shut its public beta key).
 *   search   /v1/gifs/search?q=…&limit=…&offset=…&rating=…      trending   /v1/gifs/trending
 *   suggest  /v1/gifs/search/tags?q=…
 */
class GiphyClient(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val api = "https://api.giphy.com/v1"
    override val kind = ServiceKind.GIPHY
    override val maxPageSize = 50
    override val safeModeTags = listOf("rating:g")
    override val searchPlaceholder = "Search Giphy, e.g. happy cat  ·  rating:g|pg|pg-13|r"

    override fun splitQuery(query: String): List<String> = query.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
    override fun categoryLabel(category: TagCategory): String = if (category == TagCategory.ARTIST) "User" else category.label

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val key = requireKey(credentials, "Giphy", "Create an app at developers.giphy.com to get one.")
        val (words, params) = extractParams(tags, setOf("rating"))
        val q = words.joinToString(" ")
        val size = limit.coerceIn(1, maxPageSize)
        val rating = params["rating"]?.let { "&rating=${it.urlEncoded()}" } ?: ""
        val url = if (q.isEmpty()) "$api/gifs/trending?api_key=$key&limit=$size&offset=${page * size}$rating"
        else "$api/gifs/search?api_key=$key&q=${q.urlEncoded()}&limit=$size&offset=${page * size}$rating"
        return parseGifs(http.get(url))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val key = credentials?.apiKey?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val term = prefix.trim()
        if (term.length < 2) return emptyList()
        return runCatching {
            val data = (parseJson(http.get("$api/gifs/search/tags?api_key=$key&q=${term.urlEncoded()}&limit=$limit")) as? JsonObject)?.arr("data")
            data?.mapNotNull { (it as? JsonObject)?.str("name") }?.map { TagSuggestion(it, null, TagCategory.GENERAL) } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    override fun postPageUrl(post: BooruPost): String = post.source ?: "https://giphy.com/gifs/${post.displayId}"

    companion object {
        fun parseGifs(body: String): List<BooruPost> {
            val data = (parseJson(body) as? JsonObject)?.arr("data") ?: return emptyList()
            return data.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.str("id") ?: return@mapNotNull null
                val images = o.obj("images") ?: return@mapNotNull null
                val original = images.obj("original") ?: return@mapNotNull null
                val fileUrl = original.str("url") ?: return@mapNotNull null
                val preview = images.obj("fixed_width_still")?.str("url") ?: images.obj("fixed_width")?.str("url") ?: fileUrl
                val sample = images.obj("downsized")?.str("url") ?: images.obj("fixed_height")?.str("url") ?: fileUrl
                val user = o.str("username")?.takeIf { it.isNotBlank() }
                val title = o.str("title")?.trim()?.takeIf { it.isNotEmpty() }
                val categories = buildMap<String, TagCategory> {
                    user?.let { put(it, TagCategory.ARTIST) }
                    title?.removeSuffix("GIF")?.trim()?.split(' ')?.filter { it.length > 2 }?.forEach { put(it.lowercase(), TagCategory.GENERAL) }
                }
                BooruPost(
                    id = BooruPost.idFrom(id),
                    md5 = "",
                    previewUrl = preview,
                    sampleUrl = sample,
                    fileUrl = fileUrl,
                    width = original.int("width") ?: 0,
                    height = original.int("height") ?: 0,
                    tags = categories.keys.toList(),
                    rating = when (o.str("rating")) { "g" -> "general"; "pg" -> "safe"; "pg-13" -> "questionable"; "r" -> "explicit"; else -> "" },
                    score = null,
                    source = o.str("url"),
                    owner = user,
                    hasSample = sample != fileUrl,
                    tagCategories = categories,
                    fileExtension = "gif",
                    title = title,
                    sourceId = id,
                )
            }
        }
    }
}

/**
 * Tenor API v2 (Google). Needs a Google Cloud API key with the Tenor API enabled, free.
 *   search   /v2/search?q=…&key=…&client_key=snapseek&limit=…&pos=…     featured   /v2/featured
 *   suggest  /v2/autocomplete?q=…
 */
class TenorClient(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val api = "https://tenor.googleapis.com/v2"
    private val cursors = CursorCache()
    override val kind = ServiceKind.TENOR
    override val maxPageSize = 50
    override val safeModeTags = listOf("contentfilter:high")
    override val searchPlaceholder = "Search Tenor, e.g. thumbs up  ·  contentfilter:high|medium|low|off"

    override fun splitQuery(query: String): List<String> = query.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val key = requireKey(credentials, "Tenor", "Create a Google Cloud project, enable the Tenor API and create an API key (developers.google.com/tenor).")
        val (words, params) = extractParams(tags, setOf("contentfilter"))
        val q = words.joinToString(" ")
        val pos = cursors.cursorFor(q, page)
        if (pos == CursorCache.MISSING) return emptyList()
        val size = limit.coerceIn(1, maxPageSize)
        val common = "key=$key&client_key=snapseek&limit=$size&media_filter=gif,mediumgif,tinygif,tinygifpreview" +
            (params["contentfilter"]?.let { "&contentfilter=$it" } ?: "") + (pos?.let { "&pos=${it.urlEncoded()}" } ?: "")
        val url = if (q.isEmpty()) "$api/featured?$common" else "$api/search?q=${q.urlEncoded()}&$common"
        val (posts, next) = parseResults(http.get(url))
        cursors.store(q, page + 1, next)
        return posts
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val key = credentials?.apiKey?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val term = prefix.trim()
        if (term.length < 2) return emptyList()
        return runCatching {
            (parseJson(http.get("$api/autocomplete?key=$key&client_key=snapseek&q=${term.urlEncoded()}&limit=$limit")) as? JsonObject)
                ?.strList("results")?.map { TagSuggestion(it, null, TagCategory.GENERAL) } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    override fun postPageUrl(post: BooruPost): String = post.source ?: "https://tenor.com/view/${post.displayId}"

    companion object {
        fun parseResults(body: String): Pair<List<BooruPost>, String?> {
            val rootObj = parseJson(body) as? JsonObject ?: return emptyList<BooruPost>() to null
            val results = rootObj.arr("results") ?: JsonArray(emptyList())
            val posts = results.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.str("id") ?: return@mapNotNull null
                val formats = o.obj("media_formats") ?: return@mapNotNull null
                val gif = formats.obj("gif") ?: formats.obj("mediumgif") ?: return@mapNotNull null
                val fileUrl = gif.str("url") ?: return@mapNotNull null
                val dims = gif.arr("dims")?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: emptyList()
                val preview = formats.obj("tinygifpreview")?.str("url") ?: formats.obj("tinygif")?.str("url") ?: fileUrl
                val sample = formats.obj("mediumgif")?.str("url") ?: fileUrl
                val tags = o.strList("tags")
                val title = (o.str("title")?.takeIf { it.isNotBlank() } ?: o.str("content_description"))?.trim()
                BooruPost(
                    id = BooruPost.idFrom(id),
                    md5 = "",
                    previewUrl = preview,
                    sampleUrl = sample,
                    fileUrl = fileUrl,
                    width = dims.getOrNull(0) ?: 0,
                    height = dims.getOrNull(1) ?: 0,
                    tags = tags,
                    rating = "",
                    score = null,
                    source = o.str("itemurl") ?: o.str("url"),
                    owner = null,
                    hasSample = sample != fileUrl,
                    tagCategories = tags.associateWith { TagCategory.GENERAL },
                    fileExtension = "gif",
                    title = title,
                    sourceId = id,
                )
            }
            return posts to rootObj.str("next")?.takeIf { it.isNotBlank() && it != "0" }
        }
    }
}

@Suppress("unused")
private fun JsonPrimitive.text() = contentOrNull
