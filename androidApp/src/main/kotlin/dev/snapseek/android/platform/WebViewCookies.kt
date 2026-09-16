package dev.snapseek.android.platform

import android.webkit.CookieManager
import dev.snapseek.core.download.CookieSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone's answer to the desktop's embedded Chromium: logins happen in a WebView, and the system's own cookie
 * jar is what the site clients read afterwards.
 *
 * Every call here goes to the main thread first. Reading a cookie is documented as safe from anywhere, but the
 * very first call is what loads the WebView engine, and doing that from a background thread while the main one is
 * doing the same thing is how apps die in native code with nothing catchable thrown. The clients that ask for
 * cookies all run in the background, so this is where that gets straightened out.
 */
object WebViewCookies : CookieSource {

    /** Called as the app starts, so the engine is up before anything in the background wants a cookie. */
    suspend fun warmUp() = onMainThread { CookieManager.getInstance().setAcceptCookie(true) }

    override suspend fun cookieHeaderFor(url: String): String? =
        onMainThread { CookieManager.getInstance().getCookie(url) }?.takeIf { it.isNotBlank() }

    suspend fun flush() = onMainThread { CookieManager.getInstance().flush() }

    private suspend fun <T> onMainThread(block: () -> T): T? =
        withContext(Dispatchers.Main.immediate) { runCatching(block).getOrNull() }
}
