package dev.snapseek.app

import dev.snapseek.app.ui.common.ImageLoader
import dev.snapseek.app.vm.BooruViewModel
import dev.snapseek.browser.EngineConfig
import dev.snapseek.browser.jcef.JcefEngine
import dev.snapseek.core.adblock.HostBlocklist
import dev.snapseek.core.booru.BooruClient
import dev.snapseek.core.booru.GelbooruV2Client
import dev.snapseek.core.download.DownloadManager
import dev.snapseek.core.download.FileNamer
import dev.snapseek.core.download.ImageFetcher
import dev.snapseek.core.download.ImageIoTranscoder
import dev.snapseek.core.history.HistoryRepository
import dev.snapseek.core.history.InMemoryHistoryRepository
import dev.snapseek.core.model.Service
import dev.snapseek.core.model.ServiceKind
import dev.snapseek.core.services.ServiceRepository
import dev.snapseek.core.settings.AppPaths
import dev.snapseek.core.settings.SettingsStore
import dev.snapseek.core.sites.PinterestResolver
import dev.snapseek.core.sites.PixivResolver
import dev.snapseek.core.sites.RefererPolicy
import dev.snapseek.core.sites.SafebooruResolver
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
    val history: HistoryRepository,
    val downloads: DownloadManager,
    val scope: CoroutineScope,
) {
    val engineConfig = EngineConfig(
        installDir = paths.cefInstallDir,
        cacheDir = paths.cefCacheDir,
        logFile = paths.logDir.resolve("cef.log"),
    )

    val imageLoader: ImageLoader by lazy { ImageLoader(paths.cacheDir.resolve("images"), referers, scope) }

    private val booruClients = ConcurrentHashMap<String, BooruClient>()

    fun booruClient(service: Service): BooruClient = booruClients.getOrPut(service.url) {
        when (service.kind) {
            ServiceKind.GELBOORU_V2 -> GelbooruV2Client(service.url)
            ServiceKind.WEB -> error("${service.name} has no native API mode")
        }
    }

    fun booruViewModel(service: Service): BooruViewModel =
        BooruViewModel(service, booruClient(service), downloads, settings, services, scope)

    fun shutdown() {
        scope.cancel()
        engine.shutdown()
    }

    companion object {
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
            val history = InMemoryHistoryRepository()
            val downloads = DownloadManager(
                resolvers = listOf(PinterestResolver, PixivResolver, SafebooruResolver),
                fetcher = ImageFetcher(cookies = engine, referers = referers),
                transcoder = ImageIoTranscoder(),
                namer = FileNamer(),
                history = history,
                settings = settings,
                scope = scope,
            )
            return AppGraph(paths, settings, services, referers, engine, history, downloads, scope)
        }
    }
}
