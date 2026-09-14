package dev.snapseek.app.vm

import dev.snapseek.app.AppGraph
import dev.snapseek.browser.BrowserTab
import dev.snapseek.browser.EngineState
import dev.snapseek.browser.TabEvent
import dev.snapseek.core.download.DownloadRequest
import dev.snapseek.core.model.Service
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object History : Screen
    data class Browser(val serviceId: String?) : Screen
    data class Booru(val serviceId: String) : Screen
}

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

    private var eventsJob: Job? = null

    fun startEngine() {
        scope.launch { graph.engine.start(graph.engineConfig) }
    }

    /** Opens a service the way it prefers: natively for boorus, in the embedded browser for everything else. */
    fun openService(serviceId: String) {
        val service = graph.services.byId(serviceId) ?: return
        if (service.isBooru) openBooru(service) else openWeb(service, service.url)
    }

    /** Always the website, even for services that have a native mode. */
    fun openWebsite(serviceId: String) {
        val service = graph.services.byId(serviceId) ?: return
        openWeb(service, service.url)
    }

    /** Any URL in the embedded browser, for example a post's source link. */
    fun openUrl(url: String, serviceId: String? = null) {
        openWeb(serviceId?.let { graph.services.byId(it) }, url)
    }

    fun goHome() = show(Screen.Home)
    fun openSettings() = show(Screen.Settings)
    fun openHistory() = show(Screen.History)
    fun dismissNotice() {
        _notice.value = null
    }

    fun shutdown() {
        closeTab()
        closeBooru()
    }

    private fun openBooru(service: Service) {
        closeTab()
        closeBooru()
        _booru.value = graph.booruViewModel(service)
        _screen.value = Screen.Booru(service.id)
        log.info { "Opened ${service.name} natively (${service.kind})" }
    }

    private fun openWeb(service: Service?, url: String) {
        if (graph.engine.state.value !is EngineState.Ready) {
            _notice.value = "The browser runtime is still starting."
            return
        }
        closeTab()
        closeBooru()
        val tab = graph.engine.createTab(url)
        eventsJob = scope.launch {
            tab.events.collect { event ->
                when (event) {
                    is TabEvent.SaveImage -> graph.downloads.enqueue(
                        DownloadRequest(event.imageUrl, event.pageUrl, event.format, service?.id),
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
