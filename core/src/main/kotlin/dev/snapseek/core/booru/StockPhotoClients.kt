package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private fun needKey(credentials: BooruCredentials?, site: String, where: String): String =
    credentials?.apiKey?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw MissingCredentialsException("$site needs a free API key. $where Then add it under Manage services → the key icon.")

private fun String.phrase(): List<String> = trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()

/** Unsplash API (free Access Key from unsplash.com/developers, 50 requests an hour on the demo tier). */
class UnsplashClient(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val api = "https://api.unsplash.com"
    override val kind = ServiceKind.UNSPLASH
    override val maxPageSize = 30
    override val safeModeTags = emptyList<String>()
    override val searchPlaceholder = "Search Unsplash photos, e.g. foggy forest  ·  orientation:portrait|landscape|squarish  ·  color:black_and_white"
    override fun splitQuery(query: String) = query.phrase()
    override fun categoryLabel(category: TagCategory): String = if (category == TagCategory.ARTIST) "Photographer" else category.label

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val key = needKey(credentials, "Unsplash", "Register an app at unsplash.com/developers and copy its Access Key.")
        val (words, params) = extractParams(tags, setOf("orientation", "color", "order_by"))
        val q = words.joinToString(" ")
        val size = limit.coerceIn(1, maxPageSize)
        val extra = (params["orientation"]?.let { "&orientation=$it" } ?: "") + (params["color"]?.let { "&color=$it" } ?: "") + (params["order_by"]?.let { "&order_by=$it" } ?: "")
        val url = if (q.isEmpty()) "$api/photos?page=${page + 1}&per_page=$size$extra" else "$api/search/photos?query=${q.urlEncoded()}&page=${page + 1}&per_page=$size$extra"
        return parsePhotos(http.get(url, mapOf("Authorization" to "Client-ID $key", "Accept-Version" to "v1")))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> = emptyList()
    override fun postPageUrl(post: BooruPost): String = post.source ?: "https://unsplash.com/photos/${post.displayId}"

    companion object {
        fun parsePhotos(body: String): List<BooruPost> {
            val element = parseJson(body) ?: return emptyList()
            val items: JsonArray = when (element) {
                is JsonArray -> element
                is JsonObject -> element.arr("results") ?: return emptyList()
                else -> return emptyList()
            }
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.str("id") ?: return@mapNotNull null
                val urls = o.obj("urls") ?: return@mapNotNull null
                val full = urls.str("full") ?: urls.str("raw") ?: return@mapNotNull null
                val user = o.obj("user")?.let { it.str("name") ?: it.str("username") }
                val tags = o.arr("tags")?.mapNotNull { (it as? JsonObject)?.str("title") } ?: emptyList()
                val categories = buildMap<String, TagCategory> {
                    user?.let { put(it, TagCategory.ARTIST) }
                    tags.forEach { put(it, TagCategory.GENERAL) }
                }
                BooruPost(
                    id = BooruPost.idFrom(id),
                    md5 = "",
                    previewUrl = urls.str("small") ?: urls.str("thumb") ?: full,
                    sampleUrl = urls.str("regular") ?: full,
                    fileUrl = full,
                    width = o.int("width") ?: 0,
                    height = o.int("height") ?: 0,
                    tags = categories.keys.toList(),
                    rating = "safe",
                    score = o.int("likes"),
                    source = o.obj("links")?.str("html"),
                    owner = user,
                    hasSample = true,
                    tagCategories = categories,
                    fileExtension = "jpg",
                    title = (o.str("description") ?: o.str("alt_description"))?.trim()?.takeIf { it.isNotEmpty() },
                    sourceId = id,
                )
            }
        }
    }
}

/** Pexels API (free key from pexels.com/api). */
class PexelsClient(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val api = "https://api.pexels.com/v1"
    override val kind = ServiceKind.PEXELS
    override val maxPageSize = 80
    override val safeModeTags = emptyList<String>()
    override val searchPlaceholder = "Search Pexels photos, e.g. city rain  ·  orientation:portrait  ·  size:large  ·  color:red"
    override fun splitQuery(query: String) = query.phrase()
    override fun categoryLabel(category: TagCategory): String = if (category == TagCategory.ARTIST) "Photographer" else category.label

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val key = needKey(credentials, "Pexels", "Get one at pexels.com/api.")
        val (words, params) = extractParams(tags, setOf("orientation", "size", "color"))
        val q = words.joinToString(" ")
        val size = limit.coerceIn(1, maxPageSize)
        val extra = params.entries.joinToString("") { "&${it.key}=${it.value.urlEncoded()}" }
        val url = if (q.isEmpty()) "$api/curated?page=${page + 1}&per_page=$size" else "$api/search?query=${q.urlEncoded()}&page=${page + 1}&per_page=$size$extra"
        return parsePhotos(http.get(url, mapOf("Authorization" to key)))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> = emptyList()
    override fun postPageUrl(post: BooruPost): String = post.source ?: "https://www.pexels.com/photo/${post.displayId}/"

    companion object {
        fun parsePhotos(body: String): List<BooruPost> {
            val photos = (parseJson(body) as? JsonObject)?.arr("photos") ?: return emptyList()
            return photos.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.long("id") ?: return@mapNotNull null
                val src = o.obj("src") ?: return@mapNotNull null
                val original = src.str("original") ?: return@mapNotNull null
                val photographer = o.str("photographer")?.takeIf { it.isNotBlank() }
                val categories = buildMap<String, TagCategory> { photographer?.let { put(it, TagCategory.ARTIST) } }
                BooruPost(
                    id = id,
                    md5 = "",
                    previewUrl = src.str("medium") ?: src.str("small") ?: original,
                    sampleUrl = src.str("large2x") ?: src.str("large") ?: original,
                    fileUrl = original,
                    width = o.int("width") ?: 0,
                    height = o.int("height") ?: 0,
                    tags = categories.keys.toList(),
                    rating = "safe",
                    score = null,
                    source = o.str("url"),
                    owner = photographer,
                    hasSample = true,
                    tagCategories = categories,
                    fileExtension = "jpg",
                    title = o.str("alt")?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        }
    }
}

/** Pixabay API (free key shown on pixabay.com/api/docs after logging in). */
class PixabayClient(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val api = "https://pixabay.com/api/"
    override val kind = ServiceKind.PIXABAY
    override val maxPageSize = 200
    override val safeModeTags = listOf("safesearch:true")
    override val searchPlaceholder = "Search Pixabay, e.g. yellow flowers  ·  image_type:photo|illustration|vector  ·  orientation:horizontal  ·  order:popular|latest"
    override fun splitQuery(query: String) = query.phrase()

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val key = needKey(credentials, "Pixabay", "Log in at pixabay.com/api/docs and copy the key shown there.")
        val (words, params) = extractParams(tags, setOf("image_type", "orientation", "category", "colors", "order", "safesearch", "editors_choice", "min_width", "min_height"))
        val q = words.joinToString(" ")
        val size = limit.coerceIn(3, maxPageSize)
        val extra = params.entries.joinToString("") { "&${it.key}=${it.value.urlEncoded()}" }
        val url = "$api?key=$key&q=${q.urlEncoded()}&page=${page + 1}&per_page=$size$extra"
        return parseHits(http.get(url))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> = emptyList()
    override fun postPageUrl(post: BooruPost): String = post.source ?: "https://pixabay.com/"

    companion object {
        fun parseHits(body: String): List<BooruPost> {
            val hits = (parseJson(body) as? JsonObject)?.arr("hits") ?: return emptyList()
            return hits.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.long("id") ?: return@mapNotNull null
                val large = o.str("fullHDURL") ?: o.str("largeImageURL") ?: o.str("webformatURL") ?: return@mapNotNull null
                val user = o.str("user")?.takeIf { it.isNotBlank() }
                val tags = o.str("tags")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                val categories = buildMap<String, TagCategory> {
                    user?.let { put(it, TagCategory.ARTIST) }
                    tags.forEach { put(it, TagCategory.GENERAL) }
                }
                BooruPost(
                    id = id,
                    md5 = "",
                    previewUrl = o.str("previewURL") ?: o.str("webformatURL") ?: large,
                    sampleUrl = o.str("webformatURL") ?: large,
                    fileUrl = large,
                    width = o.int("imageWidth") ?: 0,
                    height = o.int("imageHeight") ?: 0,
                    tags = categories.keys.toList(),
                    rating = "safe",
                    score = o.int("likes"),
                    source = o.str("pageURL"),
                    owner = user,
                    hasSample = true,
                    tagCategories = categories,
                    fileExtension = large.substringBefore('?').substringAfterLast('.', "jpg"),
                )
            }
        }
    }
}
