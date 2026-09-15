package dev.snapseek.android

import android.content.Context
import dev.snapseek.android.platform.AndroidTranscoder
import dev.snapseek.android.platform.GallerySaver
import dev.snapseek.android.platform.ImageLoader
import dev.snapseek.android.platform.WebViewCookies
import dev.snapseek.core.booru.BooruClient
import dev.snapseek.core.booru.BooruClients
import dev.snapseek.core.booru.BooruHttp
import dev.snapseek.core.download.ImageFetcher
import dev.snapseek.core.history.BookmarkRepository
import dev.snapseek.core.history.HistoryRepository
import dev.snapseek.core.history.InMemoryBookmarkRepository
import dev.snapseek.core.history.InMemoryHistoryRepository
import dev.snapseek.core.model.Service
import dev.snapseek.core.services.ServiceRepository
import dev.snapseek.core.settings.SettingsStore
import dev.snapseek.core.sites.RefererPolicy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything the Android app is made of, wired by hand like the desktop's graph. The site clients, the settings,
 * the service list and the download pipeline all come from `core` unchanged; only the things that touch the
 * platform (bitmaps, the gallery, the cookie jar) are built here.
 */
class AndroidGraph private constructor(
    val settings: SettingsStore,
    val services: ServiceRepository,
    val referers: RefererPolicy,
    val history: HistoryRepository,
    val bookmarks: BookmarkRepository,
    val images: ImageLoader,
    val saver: GallerySaver,
    val scope: CoroutineScope,
) {
    private val clients = ConcurrentHashMap<String, BooruClient>()

    fun client(service: Service): BooruClient =
        clients.getOrPut("${service.kind}|${service.url}|${service.login}|${service.apiKey}") {
            BooruClients.create(service, cookies = WebViewCookies)
        }

    companion object {
        @Volatile private var instance: AndroidGraph? = null

        fun of(context: Context): AndroidGraph = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(context: Context): AndroidGraph {
            val settings = SettingsStore(context.filesDir.toPath().resolve("settings.json"))
            val services = ServiceRepository(settings)
            val referers = RefererPolicy()
            val fetcher = ImageFetcher(cookies = WebViewCookies, referers = referers)
            return AndroidGraph(
                settings = settings,
                services = services,
                referers = referers,
                history = InMemoryHistoryRepository(),
                bookmarks = InMemoryBookmarkRepository(),
                images = ImageLoader(referers, WebViewCookies, BooruHttp.BROWSER_USER_AGENT),
                saver = GallerySaver(context, fetcher, AndroidTranscoder()),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("snapseek")),
            )
        }
    }
}
