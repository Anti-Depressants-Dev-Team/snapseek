package dev.snapseek.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.app.ui.common.DownloadTray
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.app.vm.LoginFlow
import dev.snapseek.browser.BrowserTab
import dev.snapseek.browser.TabState
import dev.snapseek.core.download.DownloadJob

/** The page fills everything above a slim status strip that doubles as the download tray. */
@Composable
fun BrowserScreen(
    tab: BrowserTab,
    tabState: TabState,
    jobs: List<DownloadJob>,
    notice: String?,
    login: LoginFlow? = null,
    onEndLogin: () -> Unit = {},
    onDismissNotice: () -> Unit,
    onDismissJob: (String) -> Unit,
    onClearFinished: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (login != null) LoginBanner(login, onEndLogin)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            SwingPanel(
                background = Color(0xFF181A1B),
                modifier = Modifier.fillMaxSize(),
                factory = { tab.component },
            )
        }
        if (notice != null) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF3B0F12)).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(notice, style = MaterialTheme.typography.labelMedium, color = Color(0xFFFCA5A5), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDismissNotice, modifier = Modifier.size(24.dp)) {
                    Icon(UiIcons.Close, "Dismiss", tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                }
            }
        }
        DownloadTray(
            jobs = jobs,
            onDismissJob = onDismissJob,
            onClearFinished = onClearFinished,
            emptyHint = "Right-click an image to save it · Alt+click saves instantly",
        ) {
            if (tabState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = SnapSeekColors.PrimaryHover)
            }
            Text(
                tabState.url,
                style = MaterialTheme.typography.labelSmall,
                color = SnapSeekColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Sits above the page while a sign-in is in progress: it says what the app is waiting for, shows the site's own
 * complaint if there was one, and offers a way back that doesn't depend on noticing the login succeeded.
 */
@Composable
private fun LoginBanner(login: LoginFlow, onEndLogin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SnapSeekColors.PrimaryContainer).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = SnapSeekColors.PrimaryHover)
        Column(Modifier.weight(1f)) {
            Text(
                "Sign in to ${login.serviceName} on this page.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Text(
                login.problem ?: "As soon as you're signed in I'll take you back to ${login.serviceName} with your feed and your ${login.collectionNoun}s.",
                style = MaterialTheme.typography.labelMedium,
                color = if (login.problem != null) Color(0xFFFCA5A5) else Color(0xFFD9C7F5),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onEndLogin,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
        ) { Text("Back to ${login.serviceName}") }
    }
}
