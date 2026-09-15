package dev.snapseek.android.platform

import android.webkit.CookieManager
import dev.snapseek.core.download.CookieSource

/**
 * The phone's answer to the desktop's embedded Chromium: logins happen in a WebView, and the system's own cookie
 * jar is what the site clients read afterwards. Same idea, one line of code, because Android keeps the jar for us.
 */
object WebViewCookies : CookieSource {
    override suspend fun cookieHeaderFor(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()?.takeIf { it.isNotBlank() }

    fun flush() = runCatching { CookieManager.getInstance().flush() }
}
