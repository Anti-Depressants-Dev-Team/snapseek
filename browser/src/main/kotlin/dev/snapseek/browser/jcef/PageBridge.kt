package dev.snapseek.browser.jcef

import dev.snapseek.browser.TabEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter

/**
 * Remembers, per displayed image URL, the best URL the page's own markup offered (largest srcset candidate).
 * The page tells us on right-click; the context menu reads it when the user picks an item.
 */
internal class HintStore {
    @Volatile
    private var last: Pair<String, String>? = null

    fun remember(shown: String, best: String) {
        last = shown to best
    }

    fun bestFor(shown: String): String? = last?.takeIf { it.first == shown }?.second
}

/**
 * Receives messages from the injected bridge.js via window.snapseekQuery(...).
 *   {type:"save", src, page}        Alt+click on an image: save it in the default format
 *   {type:"hint", src, best, page}  right-click happened on an image; here is its best-known URL
 */
internal class PageBridge(
    private val hints: HintStore,
    private val emit: (TabEvent) -> Unit,
) : CefMessageRouterHandlerAdapter() {

    @Serializable
    private data class Message(val type: String, val src: String = "", val best: String = "", val page: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    override fun onQuery(
        browser: CefBrowser?,
        frame: CefFrame?,
        queryId: Long,
        request: String?,
        persistent: Boolean,
        callback: CefQueryCallback,
    ): Boolean {
        val msg = request?.let { runCatching { json.decodeFromString<Message>(it) }.getOrNull() } ?: return false
        when (msg.type) {
            "save" -> if (msg.src.isNotBlank()) emit(TabEvent.SaveImage(msg.src, msg.page, null))
            "hint" -> if (msg.src.isNotBlank()) hints.remember(msg.src, msg.best.ifBlank { msg.src })
            else -> return false
        }
        callback.success("ok")
        return true
    }
}
