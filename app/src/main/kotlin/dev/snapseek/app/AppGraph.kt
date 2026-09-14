package dev.snapseek.app

import dev.snapseek.app.ui.common.ImageLoader
import dev.snapseek.app.ui.common.SkiaImageTranscoder
import dev.snapseek.app.vm.BooruViewModel
import dev.snapseek.browser.EngineConfig
import dev.snapseek.browser.jcef.JcefEngine
import dev.snapseek.core.adblock.HostBlocklist
import dev.snapseek.core.booru.BooruClient
import dev.snapseek.core.booru.BooruClients
import dev.snapseek.core.booru.BooruDetector
import dev.snapseek.core.download.BulkDownloader
import dev.snapseek.core.download.DownloadManager
import dev.snapseek.core.download.FileNamer
import dev.snapseek.core.download.ImageFetcher
import dev.snapseek.core.download.ImageIoTranscoder
import dev.snapseek.core.history.BookmarkRepository
import dev.snapseek.core.history.HistoryRepository
import dev.snapseek.core.history.InMemoryBookmarkRepository
import dev.snapseek.core.history.InMemoryHistoryRepository
import dev.snapseek.core.history.SqliteStore
import dev.snapseek.core.model.Service
import dev.snapseek.core.services.ServiceRepository
import dev.snapseek.core.settings.AppPaths
import dev.snapseek.core.settings.SettingsStore
import dev.snapseek.core.sites.PinterestResolver
import dev.snapseek.core.sites.PixivResolver
import dev.snapseek.core.sites.RefererPolicy
import dev.snapseek.core.sites.SafebooruResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories

/** Hand-wired object graph. Small enough that a DI framework would cost more than it saves. */
class AppGraph private constructor(
    val paths: AppPaths,
    val settings: SettingsStore,
    val services: ServiceRepository,
    val referers: RefererPolicy,
    val engine: JcefEngine,
    private val store: SqliteStore?,
    val history: HistoryRepository,
    val bookmarks: BookmarkRepository,
    val downloads: DownloadManager,
    val bulk: BulkDownloader,
    val scope: CoroutineScope,
) {
    val engineConfig = EngineConfig(
        installDir = paths.cefInstallDir,
        cacheDir = paths.cefCacheDir,
        logFile = paths.logDir.resolve("cef.log"),
    )

    val imageLoader: ImageLoader by lazy { ImageLoader(paths.cacheDir.resolve("images"), referers, scope) }
    val detector: BooruDetector by lazy { BooruDetector() }

    private val booruClients = ConcurrentHashMap<String, BooruClient>()

    fun booruClient(service: Service): BooruClient =
        booruClients.getOrPut("${service.kind}|${service.url}|${service.login}|${service.apiKey}") { BooruClients.create(service) }

    fun booruViewModel(service: Service): BooruViewModel =
        BooruViewModel(service, booruClient(service), downloads, bulk, bookmarks, settings, services, scope)

    fun shutdown() {
        scope.cancel()
        engine.shutdown()
        store?.close()
    }

    companion object {
        private val log = KotlinLogging.logger {}

        fun create(): AppGraph {
            val paths = AppPaths.forCurrentOs()
            paths.dataDir.createDirectories()
            paths.cacheDir.createDirectories()
            paths.logDir.createDirectories()

            val settings = SettingsStore(paths.settingsFile)
            val services = ServiceRepository(settings)
            val referers = RefererPolicy()
            val blocklist = HostBlocklist.fromResource("/adblock/seed-hosts.txt")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("snapseek"))

            val engine = JcefEngine(
                referers = referers,
                blocklist = { blocklist },
                settings = { settings.current },
            )
            val store = runCatching { SqliteStore(paths.historyDb) }
                .onFailure { log.error(it) { "Could not open ${paths.historyDb}; history and bookmarks will not persist this session" } }
                .getOrNull()
            val history = store?.history ?: InMemoryHistoryRepository()
            val bookmarks = store?.bookmarks ?: InMemoryBookmarkRepository()
            val downloads = DownloadManager(
                resolvers = listOf(PinterestResolver, PixivResolver, SafebooruResolver),
                fetcher = ImageFetcher(cookies = engine, referers = referers),
                transcoder = SkiaImageTranscoder(ImageIoTranscoder()),
                namer = FileNamer(),
                history = history,
                settings = settings,
                scope = scope,
            )
            val bulk = BulkDownloader(downloads, scope)
            return AppGraph(paths, settings, services, referers, engine, store, history, bookmarks, downloads, bulk, scope)
        }
    }
}
