package dev.snapseek.app.vm

import dev.snapseek.app.AppGraph
import dev.snapseek.browser.BrowserTab
import dev.snapseek.browser.EngineState
import dev.snapseek.browser.TabEvent
import dev.snapseek.core.booru.BooruHttp
import dev.snapseek.core.booru.ConnectResult
import dev.snapseek.core.download.DownloadRequest
import dev.snapseek.core.model.Bookmark
import dev.snapseek.core.model.OutputFormat
import dev.snapseek.core.model.Service
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object History : Screen
    data object Bookmarks : Screen
    data class Browser(val serviceId: String?) : Screen
    data class Booru(val serviceId: String) : Screen
}

/**
 * The login the app is waiting for: the website tab is showing the site's sign-in page and the native screen
 * behind it is parked, waiting for the session to appear.
 */
data class LoginFlow(
    val serviceId: String,
    val serviceName: String,
    /** What this site calls a collection, so the banner doesn't promise "boards" on a site that has none. */
    val collectionNoun: String = "collection",
    val checking: Boolean = false,
    /** Set when the site answered something that isn't "still nobody", so the user isn't left guessing. */
    val problem: String? = null,
)

/** Which screen is showing, plus the one open browser tab or booru session (phase 3 makes these lists). */
class RootViewModel(private val graph: AppGraph) {
    private val log = KotlinLogging.logger {}
    private val scope = graph.scope

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _tab = MutableStateFlow<BrowserTab?>(null)
    val tab: StateFlow<BrowserTab?> = _tab.asStateFlow()

    private val _booru = MutableStateFlow<BooruViewModel?>(null)
    val booru: StateFlow<BooruViewModel?> = _booru.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _login = MutableStateFlow<LoginFlow?>(null)
    val login: StateFlow<LoginFlow?> = _login.asStateFlow()

    private var eventsJob: Job? = null
    private var loginJob: Job? = null

    fun startEngine() {
        scope.launch { graph.engine.start(graph.engineConfig) }
        graph.updater.checkOnStart()
    }

    /** Opens a service the way it prefers: natively for boorus, in the embedded browser for everything else. */
    fun openService(serviceId: String) {
        val service = graph.services.byId(serviceId) ?: return
        if (service.isBooru) openBooru(service) else openWeb(service, service.websiteUrl)
    }

    /** Always the website, even for services that have a native mode. */
    fun openWebsite(serviceId: String) {
        val service = graph.services.byId(serviceId) ?: return
        openWeb(service, service.websiteUrl)
    }

    /** Any URL in the embedded browser, for example a post's source link. */
    fun openUrl(url: String, serviceId: String? = null) {
        openWeb(serviceId?.let { graph.services.byId(it) }, url)
    }

    fun goHome() = show(Screen.Home)
    fun openSettings() = show(Screen.Settings)
    fun openHistory() = show(Screen.History)
    fun openBookmarks() = show(Screen.Bookmarks)
    fun dismissNotice() {
        _notice.value = null
    }

    // ---- connecting an account -------------------------------------------------------------------------------

    /**
     * Shows the site's sign-in page in the website tab while keeping the native screen alive behind it, and asks
     * the site every couple of seconds whether the session has appeared. The moment it has, we close the login
     * page, go back to the native screen and load the personal feed. No "press Home and come back" any more.
     */
    fun connectAccount() {
        val vm = _booru.value ?: return
        val ac = vm.accountClient ?: return
        if (graph.engine.state.value !is EngineState.Ready) {
            _notice.value = "The browser runtime is still starting; try again in a moment."
            return
        }
        openWeb(vm.service, ac.loginUrl, keepBooru = true)
        _login.value = LoginFlow(vm.service.id, vm.service.name, ac.collectionNoun)
        log.info { "Waiting for a ${vm.service.name} login at ${ac.loginUrl}" }
        loginJob = scope.launch {
            var attempt = 0
            while (isActive) {
                delay(if (attempt < 5) 2_000 else 5_000)
                attempt++
                _login.update { it?.copy(checking = true) }
                val result = vm.probeConnection()
                _login.update { it?.copy(checking = false) }
                when (result) {
                    is ConnectResult.Connected -> {
                        finishLogin(vm, result)
                        return@launch
                    }
                    is ConnectResult.Failed -> {
                        if (_login.value?.problem != result.reason) log.warn { "Login check #$attempt: ${result.reason}" }
                        _login.update { it?.copy(problem = result.reason) }
                    }
                    is ConnectResult.Waiting -> {
                        log.info { "Login check #$attempt: nobody signed in yet on ${vm.service.name}" }
                        _login.update { it?.copy(problem = null) }
                    }
                }
            }
        }
    }

    /** "I'm done" / "Back to the grid": stop watching, close the login page, check once more on the way back. */
    fun endLogin() {
        val vm = _booru.value
        stopWatchingLogin()
        // Show the native screen before the tab goes away, so the browser screen never renders without its tab.
        _screen.value = if (vm != null) Screen.Booru(vm.service.id) else Screen.Home
        closeTab()
        vm?.refreshAccount(force = true)
    }

    private fun finishLogin(vm: BooruViewModel, result: ConnectResult.Connected) {
        stopWatchingLogin()
        _screen.value = Screen.Booru(vm.service.id)
        closeTab()
        vm.applyConnectResult(result)
        log.info { "Connected to ${vm.service.name} as @${result.account.username}" }
    }

    private fun stopWatchingLogin() {
        loginJob?.cancel()
        loginJob = null
        _login.value = null
    }

    /** Saves a bookmarked post with the default format, keeping the booru template tokens it was saved with. */
    fun saveBookmark(bookmark: Bookmark, format: OutputFormat? = null) {
        graph.downloads.enqueue(
            DownloadRequest(
                imageUrl = bookmark.fileUrl,
                pageUrl = bookmark.pageUrl,
                format = if (bookmark.extension in setOf("webm", "mp4")) OutputFormat.ORIGINAL else format,
                serviceId = bookmark.serviceId,
                serviceName = bookmark.serviceName,
                metadata = mapOf(
                    "id" to bookmark.postId.toString(), "md5" to bookmark.hash, "tags" to bookmark.tags.joinToString(" "),
                    "rating" to bookmark.rating, "width" to bookmark.width.toString(), "height" to bookmark.height.toString(),
                    "source" to (bookmark.source ?: ""),
                ),
                resolve = false,
                referer = graph.services.byId(bookmark.serviceId)?.url,
                userAgent = BooruHttp.APP_USER_AGENT,
            ),
        )
    }

    fun shutdown() {
        stopWatchingLogin()
        closeTab()
        closeBooru()
    }

    private fun openBooru(service: Service) {
        stopWatchingLogin()
        closeTab()
        closeBooru()
        _booru.value = graph.booruViewModel(service)
        _screen.value = Screen.Booru(service.id)
        log.info { "Opened ${service.name} natively (${service.kind})" }
    }

    private fun openWeb(service: Service?, url: String, keepBooru: Boolean = false) {
        if (graph.engine.state.value !is EngineState.Ready) {
            _notice.value = "The browser runtime is still starting."
            return
        }
        if (!keepBooru) stopWatchingLogin()
        closeTab()
        if (!keepBooru) closeBooru()
        val tab = graph.engine.createTab(url)
        eventsJob = scope.launch {
            tab.events.collect { event ->
                when (event) {
                    is TabEvent.SaveImage -> graph.downloads.enqueue(
                        DownloadRequest(event.imageUrl, event.pageUrl, event.format, service?.id, serviceName = service?.name),
                    )
                    is TabEvent.LoadFailed -> _notice.value = "Couldn't load ${event.url}: ${event.error}"
                    is TabEvent.LoadFinished -> _notice.value = null
                }
            }
        }
        _tab.value = tab
        _screen.value = Screen.Browser(service?.id)
        log.info { "Opened ${service?.name ?: "page"} at $url" }
    }

    private fun show(screen: Screen) {
        stopWatchingLogin()
        closeTab()
        closeBooru()
        _screen.value = screen
    }

    private fun closeTab() {
        eventsJob?.cancel()
        eventsJob = null
        val tab = _tab.value ?: return
        _tab.value = null
        tab.close()
    }

    private fun closeBooru() {
        val vm = _booru.value ?: return
        _booru.value = null
        vm.dispose()
    }
}
