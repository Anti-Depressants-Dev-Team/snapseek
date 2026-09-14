package dev.snapseek.browser.jcef

import dev.snapseek.core.model.Settings
import org.cef.browser.CefFrame

/** Scripts injected into every page after its main frame finishes loading. Read once, reused for every tab. */
internal class PageScripts {
    private val hydrate: String by lazy { read("/scripts/hydrate.js") }
    private val bridge: String by lazy { read("/scripts/bridge.js") }
    private val darkReader: String by lazy { read("/scripts/darkreader.js") + "\n" + read("/scripts/darkreader-enable.js") }

    fun injectAfterLoad(frame: CefFrame, settings: Settings) {
        val url = frame.getURL()
        if (settings.darkMode) frame.executeJavaScript(darkReader, url, 0)
        frame.executeJavaScript(hydrate, url, 0)
        frame.executeJavaScript(bridge, url, 0)
    }

    private fun read(path: String): String =
        PageScripts::class.java.getResourceAsStream(path)?.use { it.reader().readText() }
            ?: error("Missing bundled script: $path")
}
