package dev.snapseek.android.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.snapseek.android.BrowseModel
import dev.snapseek.core.booru.ConnectResult
import kotlinx.coroutines.delay

/**
 * Signing in, the phone way: the site's own page in a WebView, watched from the outside. The moment the session
 * appears in the system cookie jar the app closes the page by itself and goes back to the grid, the same as the
 * desktop does with its embedded browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(model: BrowseModel, onDone: () -> Unit) {
    val account = model.accountClient ?: return
    val state by model.state.collectAsState()

    LaunchedEffect(model) {
        var attempt = 0
        while (true) {
            delay(if (attempt < 5) 2_000 else 5_000)
            attempt++
            dev.snapseek.android.platform.WebViewCookies.flush()
            val result = runCatching { account.also { it.forgetAccount() }.connect() }.getOrNull()
            if (result is ConnectResult.Connected) {
                model.apply(result)
                onDone()
                return@LaunchedEffect
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Snap.Background)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Snap.PurpleBright)
            Column(Modifier.weight(1f)) {
                Text("Sign in to ${model.service.name}", style = MaterialTheme.typography.bodyMedium, color = Snap.TextMain)
                Text(
                    "I'll bring you back as soon as you're in.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Snap.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onDone, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Snap.Purple)) {
                Text("Done")
            }
        }
        state.notice?.let { Text(it, color = Snap.TextMuted, modifier = Modifier.padding(12.dp)) }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(context).apply {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = settings.userAgentString.replace("; wv)", ")")
                    webViewClient = WebViewClient()
                    loadUrl(account.loginUrl)
                }
            },
        )
    }
}
