package dev.snapseek.android.platform

import android.webkit.CookieManager
import dev.snapseek.core.download.CookieSource

/**
 * The phone's answer to the desktop's embedded Chromium: logins happen in a WebView, and the system's own cookie
 * jar is what the site clients read afterwards. Same idea, one line of code, because Android keeps the jar for us.
 */
object WebViewCookies : CookieSource {

    /**
     * Loading the WebView provider is the main thread's job on some devices, and the first thing to ask for a
     * cookie is a background coroutine, so the activity wakes it up here while it still has the main thread.
     */
    fun warmUp() = runCatching { CookieManager.getInstance().setAcceptCookie(true) }

    override suspend fun cookieHeaderFor(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()?.takeIf { it.isNotBlank() }

    fun flush() = runCatching { CookieManager.getInstance().flush() }
}
