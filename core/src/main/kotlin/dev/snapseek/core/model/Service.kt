package dev.snapseek.core.model

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
enum class ServiceType { BUILT_IN, CUSTOM }

/** How SnapSeek talks to a site: through the embedded browser, or natively through a known API. */
@Serializable
enum class ServiceKind(val label: String) {
    WEB("Website"),
    GELBOORU_V2("Gelbooru-style booru"),
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
) {
    val host: String
        get() = runCatching { URI(url).host ?: url }.getOrDefault(url).removePrefix("www.")

    val isBooru: Boolean get() = kind != ServiceKind.WEB
}
