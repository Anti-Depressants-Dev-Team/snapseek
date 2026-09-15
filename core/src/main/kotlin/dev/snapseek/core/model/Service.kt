package dev.snapseek.core.model

import dev.snapseek.core.booru.BooruCredentials
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
enum class ServiceType { BUILT_IN, CUSTOM }

/** How SnapSeek talks to a site: through the embedded browser, or natively through a known API. */
@Serializable
enum class ServiceKind(val label: String, val description: String, val needsKey: Boolean = false) {
    WEB("Website", "Shows the site in the built-in browser. Right-click or Alt+click images to save them."),
    GELBOORU_V2("Gelbooru 0.2", "gelbooru.com, rule34.xxx, safebooru.org, tbib.org, xbooru.com and other Gelbooru forks."),
    DANBOORU("Danbooru", "danbooru.donmai.us, safebooru.donmai.us, aibooru.online and other Danbooru forks."),
    MOEBOORU("Moebooru", "yande.re, konachan.com, konachan.net."),
    E621("e621", "e621.net and e926.net."),
    PHILOMENA("Philomena", "derpibooru.org, ponybooru.org, furbooru.org. Tags are comma-separated."),
    SZURUBOORU("Szurubooru", "Self-hosted szurubooru instances. Anonymous access depends on the instance."),
    PINTEREST("Pinterest", "Pin search through Pinterest's own web API, no account needed."),
    PIXIV("Pixiv", "Tag search and daily ranking through Pixiv's web API. Log in on the website tab for R-18."),
    DEVIANTART("DeviantArt", "DeviantArt's public feed: popular, by:artist, in:category. 800px previews."),
    WALLHAVEN("Wallhaven", "wallhaven.cc wallpapers. An API key unlocks NSFW."),
    ZEROCHAN("Zerochan", "zerochan.net anime art. Tags are comma-separated."),
    GIPHY("Giphy", "GIF search with your own free Giphy API key.", needsKey = true),
    TENOR("Tenor", "GIF search with a free Google Cloud API key (Tenor API enabled).", needsKey = true),
    UNSPLASH("Unsplash", "Stock photos with a free Unsplash Access Key.", needsKey = true),
    PEXELS("Pexels", "Stock photos with a free Pexels API key.", needsKey = true),
    PIXABAY("Pixabay", "Stock photos and illustrations with a free Pixabay API key.", needsKey = true),
    WEB_GRID("Image grid from a page", "Any site without an API: put {q} in the URL where the search goes and {page} for paging. Lists the images on the page."),
}

@Serializable
data class ServiceRegion(
    val code: String,
    val name: String,
    val url: String,
)

/**
 * A site the user can browse. Built-in services ship with the app; custom ones are added by the user.
 * The same model is persisted to settings.json, so keep it stable and default every new field.
 */
@Serializable
data class Service(
    val id: String,
    val name: String,
    val url: String,
    val icon: String = "default",
    val type: ServiceType = ServiceType.BUILT_IN,
    val kind: ServiceKind = ServiceKind.WEB,
    val regions: List<ServiceRegion> = emptyList(),
    val enabled: Boolean = true,
    /** Account name or user ID, depending on the site. */
    val login: String? = null,
    /** API key or token from the site's account settings. */
    val apiKey: String? = null,
    val nsfw: Boolean = false,
) {
    val host: String
        get() = runCatching { URI(url.replace("{q}", "q").replace("{page}", "1")).host ?: url }.getOrDefault(url).removePrefix("www.")

    val isBooru: Boolean get() = kind != ServiceKind.WEB

    /** The plain site to open in the browser tab, even when [url] is a search template. */
    val websiteUrl: String
        get() = if (url.contains("{")) runCatching { URI(url.replace("{q}", "q").replace("{page}", "1")).let { "${it.scheme}://${it.host}/" } }.getOrDefault(url) else url

    val credentials: BooruCredentials?
        get() = if (!apiKey.isNullOrBlank()) BooruCredentials(login?.trim().orEmpty(), apiKey.trim()) else null
}
