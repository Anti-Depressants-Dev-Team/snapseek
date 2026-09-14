package dev.snapseek.core.model

import dev.snapseek.core.booru.BooruCredentials
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
enum class ServiceType { BUILT_IN, CUSTOM }

/** How SnapSeek talks to a site: through the embedded browser, or natively through a known API family. */
@Serializable
enum class ServiceKind(val label: String, val description: String) {
    WEB("Website", "Shows the site in the built-in browser. Right-click or Alt+click images to save them."),
    GELBOORU_V2("Gelbooru 0.2", "gelbooru.com, rule34.xxx, safebooru.org, tbib.org, xbooru.com and other Gelbooru forks."),
    DANBOORU("Danbooru", "danbooru.donmai.us, safebooru.donmai.us, aibooru.online and other Danbooru forks."),
    MOEBOORU("Moebooru", "yande.re, konachan.com, konachan.net."),
    E621("e621", "e621.net and e926.net."),
    PHILOMENA("Philomena", "derpibooru.org, ponybooru.org, furbooru.org. Tags are comma-separated."),
    SZURUBOORU("Szurubooru", "Self-hosted szurubooru instances. Anonymous access depends on the instance."),
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
        get() = runCatching { URI(url).host ?: url }.getOrDefault(url).removePrefix("www.")

    val isBooru: Boolean get() = kind != ServiceKind.WEB

    val credentials: BooruCredentials?
        get() = if (!apiKey.isNullOrBlank()) BooruCredentials(login?.trim().orEmpty(), apiKey.trim()) else null
}
