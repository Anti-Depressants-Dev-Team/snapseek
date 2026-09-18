package dev.snapseek.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.core.update.DownloadState
import dev.snapseek.core.update.Update

/**
 * What a new release looks like from inside the app: a line at the top of the home screen, never a dialog in the
 * way. Downloading shows its progress, and the app closes itself once the installer is up, because an installer
 * cannot replace files that are still open.
 */
@Composable
fun UpdateBanner(
    update: Update,
    download: DownloadState,
    onInstall: () -> Unit,
    onNotes: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SnapSeekColors.PrimaryContainer)
            .border(1.dp, SnapSeekColors.PrimaryHover, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(UiIcons.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "SnapSeek ${update.version} is out",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when (download) {
                        is DownloadState.Failed -> download.reason
                        is DownloadState.Done -> "Starting the installer…"
                        is DownloadState.Running -> "Downloading ${update.fileName}…"
                        DownloadState.Idle -> update.notes.lineSequence().firstOrNull { it.isNotBlank() }?.take(120)
                            ?: "You are on ${update.fileName.substringAfterLast('.')} builds."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (download is DownloadState.Failed) SnapSeekColors.Danger else Color(0xFFD9C7F5),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (download is DownloadState.Idle || download is DownloadState.Failed) {
                TextButton(onClick = onNotes) { Text("What changed", color = Color(0xFFD9C7F5)) }
                TextButton(onClick = onSkip) { Text("Skip this one", color = Color(0xFFD9C7F5)) }
                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
                ) { Text(if (download is DownloadState.Failed) "Try again" else "Update now") }
                TextButton(onClick = onDismiss) { Text("Later", color = SnapSeekColors.TextMuted) }
            }
        }

        if (download is DownloadState.Running) {
            LinearProgressIndicator(
                progress = { download.fraction },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color(0x33FFFFFF),
            )
        }
    }
}
