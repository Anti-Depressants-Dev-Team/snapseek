package dev.snapseek.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.core.download.DownloadJob

/** The slim strip at the bottom of a browsing screen: caller-provided status on the left, download chips on the right. */
@Composable
fun DownloadTray(
    jobs: List<DownloadJob>,
    onDismissJob: (String) -> Unit,
    onClearFinished: () -> Unit,
    emptyHint: String,
    leading: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(34.dp).background(SnapSeekColors.Panel).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading()
        if (jobs.isEmpty()) {
            Text(emptyHint, style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted.copy(alpha = 0.7f))
        } else {
            jobs.take(3).forEach { JobChip(it, onDismiss = { onDismissJob(it.request.id) }) }
            if (jobs.size > 3) {
                Text("+${jobs.size - 3}", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted)
            }
            if (jobs.any { !it.isActive }) {
                TextButton(onClick = onClearFinished, modifier = Modifier.height(26.dp)) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun JobChip(job: DownloadJob, onDismiss: () -> Unit) {
    val (color, label) = when (job) {
        is DownloadJob.Queued -> SnapSeekColors.TextMuted to "Queued"
        is DownloadJob.Fetching -> SnapSeekColors.PrimaryHover to "Fetching…"
        is DownloadJob.Converting -> SnapSeekColors.PrimaryHover to "Converting…"
        is DownloadJob.Saved -> SnapSeekColors.Success to "Saved ${job.file.fileName}"
        is DownloadJob.Duplicate -> SnapSeekColors.Warning to "Already saved as ${job.existing.fileName}"
        is DownloadJob.Failed -> SnapSeekColors.Danger to "Failed: ${job.message}"
    }
    Row(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        if (!job.isActive) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(18.dp)) {
                Icon(UiIcons.Close, "Dismiss", tint = color, modifier = Modifier.size(10.dp))
            }
        } else {
            Spacer(Modifier.width(6.dp))
        }
    }
}
