package dev.snapseek.browser

import dev.snapseek.core.download.CookieSource
import dev.snapseek.core.model.OutputFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Component
import java.nio.file.Path

/**
 * The only browser API the rest of the app sees. Today it is implemented with JCEF;
 * swapping engines means implementing these two interfaces and nothing else.
 */
interface BrowserEngine : CookieSource {
    val state: StateFlow<EngineState>

    /** Installs the runtime if needed, then starts it. Safe to call again after a failure. */
    suspend fun start(config: EngineConfig)

    fun createTab(initialUrl: String): BrowserTab

    fun shutdown()
}

interface BrowserTab {
    /** The heavyweight AWT component that renders the page; host it in a SwingPanel. */
    val component: Component
    val state: StateFlow<TabState>
    val events: Flow<TabEvent>

    fun load(url: String)
    fun back()
    fun forward()
    fun reload()
    fun stop()
    fun runScript(js: String)
    fun openDevTools()
    fun close()
}

data class EngineConfig(
    val installDir: Path,
    val cacheDir: Path,
    val logFile: Path? = null,
    val locale: String = "en-US",
)

sealed interface EngineState {
    data object Idle : EngineState
    /** [percent] is -1 when the phase has no meaningful progress. */
    data class Installing(val percent: Float, val phase: String) : EngineState
    data object Starting : EngineState
    data object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

data class TabState(
    val url: String = "",
    val title: String = "",
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

sealed interface TabEvent {
    /** The user asked to save an image. [format] null means the default format. */
    data class SaveImage(val imageUrl: String, val pageUrl: String, val format: OutputFormat?) : TabEvent
    data class LoadFinished(val url: String) : TabEvent
    data class LoadFailed(val url: String, val error: String) : TabEvent
}
