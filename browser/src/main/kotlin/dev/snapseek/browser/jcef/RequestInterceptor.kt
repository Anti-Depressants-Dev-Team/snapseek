package dev.snapseek.browser.jcef

import dev.snapseek.core.adblock.HostBlocklist
import dev.snapseek.core.model.Settings
import dev.snapseek.core.sites.RefererPolicy
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest

/**
 * Sees every request the page makes before it leaves. Two jobs:
 * cancel requests to blocked hosts (never the navigation itself), and add the Referer some CDNs insist on.
 */
internal class RequestInterceptor(
    private val referers: RefererPolicy,
    private val blocklist: () -> HostBlocklist,
    private val settings: () -> Settings,
) : CefRequestHandlerAdapter() {

    override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef?,
    ): CefResourceRequestHandler = ResourceHandler(isNavigation)

    private inner class ResourceHandler(private val isNavigation: Boolean) : CefResourceRequestHandlerAdapter() {
        override fun onBeforeResourceLoad(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): Boolean {
            val host = RefererPolicy.hostOf(request.getURL()) ?: return false
            if (!isNavigation && settings().adBlock && blocklist().blocks(host)) return true
            referers.refererFor(host)?.let { request.setHeaderByName("Referer", it, true) }
            return false
        }
    }
}
