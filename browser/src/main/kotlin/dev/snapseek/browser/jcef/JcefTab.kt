package dev.snapseek.browser.jcef

import dev.snapseek.browser.BrowserTab
import dev.snapseek.browser.TabEvent
import dev.snapseek.browser.TabState
import dev.snapseek.core.adblock.HostBlocklist
import dev.snapseek.core.model.Settings
import dev.snapseek.core.sites.RefererPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Component

/** One Chromium browser with its own CefClient, so every handler below belongs to exactly this tab. */
internal class JcefTab(
    private val client: CefClient,
    initialUrl: String,
    referers: RefererPolicy,
    blocklist: () -> HostBlocklist,
    private val settings: () -> Settings,
    private val scripts: PageScripts,
) : BrowserTab {

    private val log = KotlinLogging.logger {}
    private val _state = MutableStateFlow(TabState(url = initialUrl, isLoading = true))
    override val state: StateFlow<TabState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TabEvent>(extraBufferCapacity = 64)
    override val events: Flow<TabEvent> = _events.asSharedFlow()

    private val hints = HintStore()
    private val browser: CefBrowser

    override val component: Component get() = browser.getUIComponent()

    init {
        client.addRequestHandler(RequestInterceptor(referers, blocklist, settings))
        client.addContextMenuHandler(ContextMenuBridge(hints, ::emit))

        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
                if (frame.isMain) _state.update { it.copy(url = url) }
            }

            override fun onTitleChange(browser: CefBrowser, title: String?) {
                _state.update { it.copy(title = title.orEmpty()) }
            }
        })

        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(browser: CefBrowser, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
                _state.update { it.copy(isLoading = isLoading, canGoBack = canGoBack, canGoForward = canGoForward) }
            }

            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (!frame.isMain) return
                log.info { "Loaded ${frame.getURL()} (HTTP $httpStatusCode)" }
                scripts.injectAfterLoad(frame, settings())
                emit(TabEvent.LoadFinished(frame.getURL()))
            }

            override fun onLoadError(
                browser: CefBrowser,
                frame: CefFrame,
                errorCode: CefLoadHandler.ErrorCode,
                errorText: String,
                failedUrl: String,
            ) {
                if (frame.isMain && errorCode != CefLoadHandler.ErrorCode.ERR_ABORTED) {
                    log.warn { "Load failed: $failedUrl ($errorCode $errorText)" }
                    emit(TabEvent.LoadFailed(failedUrl, errorText))
                }
            }
        })

        // Sites love opening things in new windows. We have one window, so open them here instead.
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(browser: CefBrowser, frame: CefFrame, targetUrl: String, targetFrameName: String?): Boolean {
                browser.loadURL(targetUrl)
                return true
            }
        })

        val router = CefMessageRouter.create(CefMessageRouter.CefMessageRouterConfig("snapseekQuery", "snapseekQueryCancel"))
        router.addHandler(PageBridge(hints, ::emit), true)
        client.addMessageRouter(router)

        browser = client.createBrowser(initialUrl, false, false)
    }

    private fun emit(event: TabEvent) {
        if (!_events.tryEmit(event)) log.warn { "Dropped tab event $event" }
    }

    override fun load(url: String) = browser.loadURL(url)
    override fun back() = browser.goBack()
    override fun forward() = browser.goForward()
    override fun reload() = browser.reload()
    override fun stop() = browser.stopLoad()
    override fun runScript(js: String) = browser.executeJavaScript(js, browser.getURL(), 0)
    override fun openDevTools() = browser.openDevTools()

    override fun close() {
        runCatching {
            browser.setCloseAllowed()
            browser.close(true)
        }.onFailure { log.warn(it) { "Closing browser threw" } }
        runCatching { client.dispose() }.onFailure { log.warn(it) { "Disposing client threw" } }
    }
}
