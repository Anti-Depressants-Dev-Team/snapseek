package dev.snapseek.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.AppGraph
import dev.snapseek.app.ui.common.Reveal
import dev.snapseek.app.ui.theme.SnapSeekColors
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun HistoryScreen(graph: AppGraph, onBack: () -> Unit) {
    val entries by graph.history.entries.collectAsState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(UiIcons.ArrowLeft, "Back", tint = SnapSeekColors.TextMain) }
            Spacer(Modifier.width(8.dp))
            Text("History", style = MaterialTheme.typography.headlineMedium, color = SnapSeekColors.TextMain, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                TextButton(onClick = { scope.launch { graph.history.clear() } }) { Text("Clear history", color = SnapSeekColors.Danger) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "This session's downloads. Phase 1 keeps history across restarts with thumbnails and search.",
            style = MaterialTheme.typography.bodySmall,
            color = SnapSeekColors.TextMuted,
        )
        Spacer(Modifier.height(20.dp))

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                Text("No downloads yet.", color = SnapSeekColors.TextMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(SnapSeekColors.Card, RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, SnapSeekColors.Border), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.fileName, style = MaterialTheme.typography.titleSmall, color = SnapSeekColors.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${timeFormat.format(entry.savedAt)} · ${entry.sourceHost} · ${entry.bytes / 1024} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = SnapSeekColors.TextMuted,
                            )
                        }
                        TextButton(onClick = { Reveal.inFolder(entry.file) }) { Text("Show in folder", color = SnapSeekColors.PrimaryHover) }
                        TextButton(onClick = { Reveal.open(entry.file) }) { Text("Open", color = SnapSeekColors.PrimaryHover) }
                    }
                }
            }
        }
    }
}
