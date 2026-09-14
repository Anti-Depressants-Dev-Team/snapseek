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
                _state.value = EngineState.Ready
                log.info { "CEF ready (install dir: ${config.installDir})" }
            } catch (e: Exception) {
                log.error(e) { "CEF failed to start" }
                _state.value = EngineState.Failed(e.message ?: e.toString())
            }
        }
    }

    override fun createTab(initialUrl: String): BrowserTab {
        val cef = checkNotNull(app) { "Browser engine is not running" }
        return JcefTab(cef.createClient(), initialUrl, referers, blocklist, settings, scripts)
    }

    /** Joins the browser session's cookies for [url] into one Cookie header. Returns null when there are none. */
    override suspend fun cookieHeaderFor(url: String): String? {
        if (app == null) return null
        val manager = CefCookieManager.getGlobalManager() ?: return null
        return withTimeoutOrNull(1500) {
            suspendCancellableCoroutine { cont ->
                val parts = mutableListOf<String>()
                val visitor = CefCookieVisitor { cookie, count, total, _ ->
                    parts += "${cookie.name}=${cookie.value}"
                    if (count == total - 1 && cont.isActive) cont.resume(parts.joinToString("; "))
                    true
                }
                if (!manager.visitUrlCookies(url, true, visitor) && cont.isActive) cont.resume(null)
            }
        }?.takeIf { it.isNotEmpty() }
    }

    override fun shutdown() {
        app?.dispose()
        app = null
    }
}
