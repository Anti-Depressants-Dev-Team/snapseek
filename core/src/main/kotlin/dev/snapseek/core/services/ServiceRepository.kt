package dev.snapseek.core.services

import dev.snapseek.core.booru.BooruPreset
import dev.snapseek.core.model.Service
import dev.snapseek.core.model.ServiceKind
import dev.snapseek.core.model.ServiceType
import dev.snapseek.core.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Reads and edits the service list stored inside [SettingsStore]. On construction it merges in any built-in
 * service that is missing from the user's file (the migration the Electron app did on every start).
 */
class ServiceRepository(private val store: SettingsStore) {

    val services: Flow<List<Service>> = store.settings.map { it.services }
    val current: List<Service> get() = store.current.services

    init {
        ensureBuiltIns()
    }

    fun byId(id: String): Service? = current.firstOrNull { it.id == id }

    fun byUrl(url: String): Service? = current.firstOrNull { it.url.trimEnd('/').equals(url.trimEnd('/'), ignoreCase = true) }

    fun setEnabled(id: String, enabled: Boolean) = edit(id) { it.copy(enabled = enabled) }

    fun setUrl(id: String, url: String) = edit(id) { it.copy(url = url) }

    fun setKind(id: String, kind: ServiceKind) = edit(id) { it.copy(kind = kind) }

    fun setCredentials(id: String, login: String?, apiKey: String?) = edit(id) {
        it.copy(login = login?.trim()?.ifEmpty { null }, apiKey = apiKey?.trim()?.ifEmpty { null })
    }

    fun addCustom(name: String, url: String, iconUrl: String?, kind: ServiceKind = ServiceKind.WEB, nsfw: Boolean = false): Service {
        val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val service = Service(
            id = "custom_${System.currentTimeMillis()}",
            name = name.trim(),
            url = normalizedUrl.trim(),
            icon = iconUrl?.trim()?.takeIf { it.isNotEmpty() } ?: "default",
            type = ServiceType.CUSTOM,
            kind = kind,
            nsfw = nsfw,
        )
        store.update { it.copy(services = it.services + service) }
        return service
    }

    /** Adds a known booru, or returns the existing service when it is already there. */
    fun addPreset(preset: BooruPreset): Service {
        byUrl(preset.url)?.let { return it }
        val service = Service(
            id = "preset_${preset.host.replace(Regex("[^a-z0-9]"), "_")}",
            name = preset.name,
            url = preset.url.trimEnd('/') + "/",
            icon = preset.icon,
            type = ServiceType.CUSTOM,
            kind = preset.kind,
            nsfw = preset.nsfw,
        )
        store.update { s -> if (s.services.any { it.id == service.id }) s else s.copy(services = s.services + service) }
        return byId(service.id) ?: service
    }

    fun removeCustom(id: String) = store.update { s ->
        s.copy(services = s.services.filterNot { it.id == id && it.type == ServiceType.CUSTOM })
    }

    fun rememberSearch(serviceId: String, query: String, keep: Int = 20) {
        val q = query.trim()
        if (q.isEmpty()) return
        store.update { s ->
            val list = (listOf(q) + (s.searchHistory[serviceId] ?: emptyList()).filterNot { it.equals(q, ignoreCase = true) }).take(keep)
            s.copy(searchHistory = s.searchHistory + (serviceId to list))
        }
    }

    fun clearSearchHistory(serviceId: String) = store.update { s -> s.copy(searchHistory = s.searchHistory - serviceId) }

    private fun edit(id: String, transform: (Service) -> Service) = store.update { s ->
        s.copy(services = s.services.map { if (it.id == id) transform(it) else it })
    }

    private fun ensureBuiltIns() = store.update { s ->
        val known = s.services.map { it.id }.toSet()
        val repaired = s.services.map { existing ->
            val builtIn = DefaultServices.byId[existing.id] ?: return@map existing
            var e = existing
            if (e.regions.isEmpty() && builtIn.regions.isNotEmpty()) e = e.copy(regions = builtIn.regions)
            // Built-ins that gained a native mode switch to it; the card still offers the website as a fallback.
            if (e.kind == ServiceKind.WEB && builtIn.kind != ServiceKind.WEB) e = e.copy(kind = builtIn.kind)
            e
        }
        s.copy(services = repaired + DefaultServices.all.filter { it.id !in known })
    }
}
