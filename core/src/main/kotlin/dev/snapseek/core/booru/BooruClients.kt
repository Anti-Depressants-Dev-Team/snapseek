package dev.snapseek.core.booru

import dev.snapseek.core.model.Service
import dev.snapseek.core.model.ServiceKind
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Builds the right client for a service. */
object BooruClients {
    fun create(service: Service): BooruClient {
        val credentials = service.credentials
        return when (service.kind) {
            ServiceKind.WEB -> error("${service.name} has no native API mode")
            ServiceKind.GELBOORU_V2 -> GelbooruV2Client(service.url, credentials)
            ServiceKind.DANBOORU -> DanbooruClient(service.url, credentials)
            ServiceKind.MOEBOORU -> MoebooruClient(service.url)
            ServiceKind.E621 -> E621Client(service.url, credentials)
            ServiceKind.PHILOMENA -> PhilomenaClient(service.url, credentials)
            ServiceKind.SZURUBOORU -> SzurubooruClient(service.url, credentials)
        }
    }
}

/** A booru people are likely to add. Same role as Boorusama's boorus.yaml, kept short and honest about content. */
data class BooruPreset(
    val name: String,
    val url: String,
    val kind: ServiceKind,
    val nsfw: Boolean,
    val icon: String,
    val note: String? = null,
) {
    val host: String get() = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

object BooruPresets {
    val all: List<BooruPreset> = listOf(
        BooruPreset("Danbooru", "https://danbooru.donmai.us", ServiceKind.DANBOORU, nsfw = true, icon = "danbooru", note = "Anonymous searches take two tags at most."),
        BooruPreset("Safebooru (Danbooru)", "https://safebooru.donmai.us", ServiceKind.DANBOORU, nsfw = false, icon = "danbooru"),
        BooruPreset("AIBooru", "https://aibooru.online", ServiceKind.DANBOORU, nsfw = true, icon = "aibooru", note = "AI-generated art."),
        BooruPreset("Yande.re", "https://yande.re", ServiceKind.MOEBOORU, nsfw = true, icon = "yandere"),
        BooruPreset("Konachan", "https://konachan.com", ServiceKind.MOEBOORU, nsfw = true, icon = "konachan", note = "Wallpapers."),
        BooruPreset("Konachan (safe)", "https://konachan.net", ServiceKind.MOEBOORU, nsfw = false, icon = "konachan", note = "Wallpapers."),
        BooruPreset("Gelbooru", "https://gelbooru.com", ServiceKind.GELBOORU_V2, nsfw = true, icon = "gelbooru", note = "Add your API key and user ID from account options."),
        BooruPreset("Rule 34", "https://rule34.xxx", ServiceKind.GELBOORU_V2, nsfw = true, icon = "rule34"),
        BooruPreset("TBIB", "https://tbib.org", ServiceKind.GELBOORU_V2, nsfw = true, icon = "tbib"),
        BooruPreset("Xbooru", "https://xbooru.com", ServiceKind.GELBOORU_V2, nsfw = true, icon = "xbooru"),
        BooruPreset("Hypnohub", "https://hypnohub.net", ServiceKind.GELBOORU_V2, nsfw = true, icon = "hypnohub"),
        BooruPreset("Realbooru", "https://realbooru.com", ServiceKind.GELBOORU_V2, nsfw = true, icon = "realbooru", note = "Photos, not art."),
        BooruPreset("e621", "https://e621.net", ServiceKind.E621, nsfw = true, icon = "e621"),
        BooruPreset("e926", "https://e926.net", ServiceKind.E621, nsfw = false, icon = "e621", note = "The safe side of e621."),
        BooruPreset("Derpibooru", "https://derpibooru.org", ServiceKind.PHILOMENA, nsfw = false, icon = "derpibooru", note = "Default filter hides adult content; comma-separated tags."),
        BooruPreset("Ponybooru", "https://ponybooru.org", ServiceKind.PHILOMENA, nsfw = true, icon = "ponybooru"),
        BooruPreset("Furbooru", "https://furbooru.org", ServiceKind.PHILOMENA, nsfw = true, icon = "furbooru"),
    )

    fun byHost(host: String): BooruPreset? = all.firstOrNull { it.host.equals(host.removePrefix("www."), ignoreCase = true) }
}

/** Works out which API a URL speaks by asking it for one post, cheapest checks first. */
class BooruDetector(private val http: BooruHttp = BooruHttp(timeout = java.time.Duration.ofSeconds(10))) {
    private val log = KotlinLogging.logger {}

    suspend fun detect(rawUrl: String): ServiceKind? {
        val root = normalise(rawUrl)
        BooruPresets.byHost(root.removePrefix("https://").removePrefix("http://"))?.let { return it.kind }

        probe(root, "/api/info") { it is JsonObject && (it.obj("config") != null || it.int("postCount") != null) }?.let { return ServiceKind.SZURUBOORU }
        probe(root, "/api/v1/json/search/images?per_page=1&q=*") { it is JsonObject && it.arr("images") != null }?.let { return ServiceKind.PHILOMENA }
        probe(root, "/posts.json?limit=1") { el ->
            when {
                el is JsonObject && el.arr("posts")?.firstOrNull()?.let { (it as? JsonObject)?.obj("file") } != null -> true
                el is JsonArray && (el.firstOrNull() as? JsonObject)?.str("tag_string") != null -> true
                el is JsonArray && el.isEmpty() -> true
                else -> false
            }
        }?.let { el -> return if (el is JsonObject) ServiceKind.E621 else ServiceKind.DANBOORU }
        probe(root, "/post.json?limit=1") { el ->
            el is JsonArray && (el.firstOrNull() as? JsonObject)?.let { it.str("preview_url") != null && it.str("md5") != null } == true
        }?.let { return ServiceKind.MOEBOORU }
        probe(root, "/index.php?page=dapi&s=post&q=index&json=1&limit=1") { el ->
            el is JsonArray || (el is JsonObject && (el.arr("post") != null || el.obj("@attributes") != null))
        }?.let { return ServiceKind.GELBOORU_V2 }
        return null
    }

    private suspend fun probe(root: String, path: String, matches: (kotlinx.serialization.json.JsonElement) -> Boolean): kotlinx.serialization.json.JsonElement? =
        runCatching {
            val body = http.get(root + path, headers = mapOf("Accept" to "application/json"))
            // Gelbooru forks answer an empty body for "no posts"; that is still a Gelbooru.
            val element = parseJson(body) ?: JsonArray(emptyList())
            element.takeIf(matches)
        }.onFailure { log.debug { "Probe $path on $root: ${it.message}" } }.getOrNull()

    companion object {
        fun normalise(url: String): String {
            val withScheme = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            return withScheme.trim().trimEnd('/').substringBefore('?').let { u ->
                // Keep only the origin; users paste deep links.
                val schemeEnd = u.indexOf("://") + 3
                val slash = u.indexOf('/', schemeEnd)
                if (slash > 0) u.substring(0, slash) else u
            }
        }
    }
}
