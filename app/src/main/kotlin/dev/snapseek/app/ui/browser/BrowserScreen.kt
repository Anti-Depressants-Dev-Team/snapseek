package dev.snapseek.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    onDismissNotice: () -> Unit,
    onDismissJob: (String) -> Unit,
    onClearFinished: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
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
