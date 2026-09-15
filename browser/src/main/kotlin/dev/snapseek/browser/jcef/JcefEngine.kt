package dev.snapseek.browser.jcef

import dev.snapseek.browser.BrowserEngine
import dev.snapseek.browser.BrowserTab
import dev.snapseek.browser.EngineConfig
import dev.snapseek.browser.EngineState
import dev.snapseek.core.adblock.HostBlocklist
import dev.snapseek.core.model.Settings
import dev.snapseek.core.sites.RefererPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.callback.CefCookieVisitor
import org.cef.network.CefCookieManager
import kotlin.coroutines.resume
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

/**
 * JCEF through jcefmaven. First start downloads and unpacks the Chromium runtime into [EngineConfig.installDir];
 * after that it starts in a second or two.
 */
class JcefEngine(
    private val referers: RefererPolicy,
    private val blocklist: () -> HostBlocklist,
    private val settings: () -> Settings,
) : BrowserEngine {

    private val log = KotlinLogging.logger {}
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val scripts = PageScripts()

    @Volatile
    private var primer: org.cef.browser.CefBrowser? = null

    @Volatile
    private var app: CefApp? = null

    override suspend fun start(config: EngineConfig) {
        if (app != null) return
        withContext(Dispatchers.IO) {
            try {
                config.installDir.createDirectories()
                config.cacheDir.createDirectories()
                config.logFile?.parent?.createDirectories()

                val builder = CefAppBuilder()
                builder.setInstallDir(config.installDir.toFile())
                builder.setProgressHandler { phase, percent ->
                    val name = phase.name.lowercase().replace('_', ' ')
                    _state.value = if (phase.name.startsWith("INIT")) EngineState.Starting
                    else EngineState.Installing(percent, name)
                }
                with(builder.cefSettings) {
                    windowless_rendering_enabled = false
                    cache_path = config.cacheDir.toAbsolutePath().toString()
                    root_cache_path = cache_path
                    persist_session_cookies = true
                    locale = config.locale
                    log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING
                    config.logFile?.let { log_file = it.toAbsolutePath().toString() }
                    // Dark page background while a page loads, so Dark Reader kicking in later isn't a white flash.
                    background_color = this.ColorType(255, 24, 26, 27)
                }
                builder.setAppHandler(object : MavenCefAppHandlerAdapter() {
                    override fun stateHasChanged(state: CefApp.CefAppState) {
                        log.info { "CEF state: $state" }
                        if (state == CefApp.CefAppState.TERMINATED) exitProcess(0)
                    }
                })

                app = builder.build()
                wakeChromium()
                _state.value = EngineState.Ready
                log.info { "CEF ready (install dir: ${config.installDir})" }
            } catch (e: Exception) {
                log.error(e) { "CEF failed to start" }
                _state.value = EngineState.Failed(e.message ?: e.toString())
            }
        }
    }

    /**
     * JCEF keeps Chromium asleep until something opens a page, and while it sleeps its cookie store answers
     * nothing at all — which made a perfectly good Pinterest or Pixiv login look like "not signed in" on any
     * screen that never opened a tab. One throwaway blank browser wakes it, off-screen and never shown.
     */
    private fun wakeChromium() {
        val cef = app ?: return
        if (primer != null) return
        runCatching {
            javax.swing.SwingUtilities.invokeAndWait {
                val client = cef.createClient()
                primer = client.createBrowser("about:blank", false, false).also { it.getUIComponent() }
            }
            log.info { "Chromium awake; browser logins are readable" }
        }.onFailure { log.warn(it) { "Couldn't wake Chromium for cookie access" } }
    }

    override fun createTab(initialUrl: String): BrowserTab {
        val cef = checkNotNull(app) { "Browser engine is not running" }
        return JcefTab(cef.createClient(), initialUrl, referers, blocklist, settings, scripts)
    }

    /**
     * Joins the browser session's cookies for [url] into one Cookie header, http-only ones included, which is
     * what makes a logged-in session usable. Returns null when there are none.
     *
     * CEF visits cookies one callback at a time and never says "that was the last one" when there are zero, so
     * we collect into a shared list and take whatever arrived when the visit stops calling back.
     */
    override suspend fun cookieHeaderFor(url: String): String? {
        if (app == null) {
            // A native screen can open before Chromium has finished starting; wait rather than report "no login".
            val ready = withTimeoutOrNull(20_000) { state.first { it is EngineState.Ready || it is EngineState.Failed } }
            if (ready !is EngineState.Ready || app == null) return null
        }
        val manager = CefCookieManager.getGlobalManager() ?: return null
        val parts = java.util.concurrent.ConcurrentHashMap<String, String>()
        val order = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val done = withTimeoutOrNull(2000) {
            suspendCancellableCoroutine { cont ->
                val visitor = CefCookieVisitor { cookie, count, total, _ ->
                    val name = cookie.name.orEmpty()
                    val value = cookie.value.orEmpty()
                    if (name.isNotEmpty()) {
                        if (parts.put(name, value) == null) order += name
                    }
                    if (count >= total - 1 && cont.isActive) cont.resume(true)
                    true
                }
                if (!manager.visitUrlCookies(url, true, visitor) && cont.isActive) cont.resume(false)
            }
        }
        if (done == false) log.debug { "CEF refused to visit cookies for $url" }
        return order.joinToString("; ") { "$it=${parts[it]}" }.takeIf { it.isNotEmpty() }
    }

    override fun shutdown() {
        runCatching { primer?.close(true) }
        primer = null
        app?.dispose()
        app = null
    }
}
