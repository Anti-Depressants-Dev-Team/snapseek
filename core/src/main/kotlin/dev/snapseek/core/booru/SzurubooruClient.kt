package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Base64

/**
 * Szurubooru API (self-hosted boorus). Instances decide whether anonymous browsing is allowed;
 * a username plus a token from account settings unlocks the rest.
 *   posts         /api/posts/?query=…&offset=…&limit=…
 *   autocomplete  /api/tags/?query=…*&limit=…
 */
class SzurubooruClient(
    override val baseUrl: String,
    private val credentials: BooruCredentials? = null,
    private val http: BooruHttp = BooruHttp.default,
) : BooruClient {
    private val root = baseUrl.trimEnd('/')
    override val kind = ServiceKind.SZURUBOORU
    override val maxPageSize = 100
    override val safeModeTags = listOf("safety:safe")

    private val headers: Map<String, String>
        get() = buildMap {
            put("Accept", "application/json")
            credentials?.let { put("Authorization", "Token " + Base64.getEncoder().encodeToString("${it.login}:${it.apiKey}".toByteArray())) }
        }

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val size = limit.coerceIn(1, maxPageSize)
        val url = "$root/api/posts/?query=${tags.trim().urlEncoded()}&offset=${page * size}&limit=$size" +
            "&fields=id,checksum,checksumMD5,contentUrl,thumbnailUrl,canvasWidth,canvasHeight,tags,safety,score,source,type,mimeType,user"
        return parsePosts(http.get(url, headers), root)
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim().lowercase()
        if (term.isEmpty()) return emptyList()
        val url = "$root/api/tags/?query=${(term + "*").urlEncoded()}&limit=$limit&fields=names,category,usages"
        return runCatching { parseSuggestions(http.get(url, headers)) }.getOrDefault(emptyList())
    }

    override fun postPageUrl(post: BooruPost): String = "$root/post/${post.id}"

    companion object {
        fun parsePosts(body: String, root: String): List<BooruPost> {
            val items = (parseJson(body) as? JsonObject)?.arr("results") ?: return emptyList()
            return items.mapNotNull { (it as? JsonObject)?.let { o -> postFrom(o, root) } }
        }

        private fun postFrom(o: JsonObject, root: String): BooruPost? {
            val id = o.long("id") ?: return null
            val hash = o.str("checksumMD5") ?: o.str("checksum") ?: return null
            val fileUrl = absolutize(o.str("contentUrl"), root) ?: return null
            val preview = absolutize(o.str("thumbnailUrl"), root) ?: fileUrl
            val tagEntries = o.arr("tags")?.mapNotNull { t ->
                val tagObj = t as? JsonObject ?: return@mapNotNull null
                val name = tagObj.arr("names")?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
                name to TagCategory.parse(tagObj.str("category"))
            } ?: emptyList()
            val mime = o.str("mimeType") ?: ""
            val ext = fileUrl.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..5 }
                ?: mime.substringAfter('/', "").ifEmpty { null }
            return BooruPost(
                id = id,
                md5 = hash,
                previewUrl = preview,
                sampleUrl = fileUrl,
                fileUrl = fileUrl,
                width = o.int("canvasWidth") ?: 0,
                height = o.int("canvasHeight") ?: 0,
                tags = tagEntries.map { it.first },
                rating = BooruPost.normaliseRating(o.str("safety")),
                score = o.int("score"),
                source = o.str("source")?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() },
                owner = o.obj("user")?.str("name"),
                hasSample = false,
                tagCategories = tagEntries.toMap(),
                fileExtension = ext,
            )
        }

        fun parseSuggestions(body: String): List<TagSuggestion> {
            val items = (parseJson(body) as? JsonObject)?.arr("results") ?: return emptyList()
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val name = o.arr("names")?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
                TagSuggestion(name, o.int("usages"), TagCategory.parse(o.str("category")))
            }
        }
    }
}
