package dev.snapseek.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.app.AppGraph
import dev.snapseek.app.ui.common.Reveal
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.common.UpdateBanner
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.browser.EngineState
import dev.snapseek.core.model.Service
import java.nio.file.Path

@Composable
fun HomeScreen(
    graph: AppGraph,
    engineState: EngineState,
    onBrowse: (String) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryEngine: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onQuit: () -> Unit,
) {
    val settings by graph.settings.settings.collectAsState()
    val services = settings.services
    val update by graph.updater.available.collectAsState()
    val downloadState by graph.updater.download.collectAsState()
    var manageOpen by remember { mutableStateOf(false) }
    val ready = engineState is EngineState.Ready

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text("SnapSeek", style = MaterialTheme.typography.headlineLarge, color = SnapSeekColors.TextMain)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Browse and download images beautifully.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SnapSeekColors.TextMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { Reveal.openFolder(Path.of(settings.downloadDir)) }) {
                    Icon(UiIcons.Folder, "Open downloads folder", tint = SnapSeekColors.TextMuted)
                }
                IconButton(onClick = onOpenBookmarks) { Icon(UiIcons.Bookmark, "Bookmarks", tint = SnapSeekColors.TextMuted) }
                IconButton(onClick = onOpenHistory) { Icon(UiIcons.History, "Download history", tint = SnapSeekColors.TextMuted) }
                IconButton(onClick = onOpenSettings) { Icon(UiIcons.Settings, "Settings", tint = SnapSeekColors.TextMuted) }
            }
        }

        update?.let {
            UpdateBanner(
                update = it,
                download = downloadState,
                onInstall = { graph.updater.install(it, onQuit) },
                onNotes = { onOpenWeb(it.pageUrl) },
                onSkip = { graph.updater.skip(it) },
                onDismiss = graph.updater::dismiss,
            )
        }

        EngineBanner(engineState, onRetryEngine)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 250.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(services.filter { it.enabled }, key = { it.id }) { service ->
                ServiceCard(
                    service = service,
                    ready = ready,
                    onBrowse = { onBrowse(service.id) },
                    onOpenWebsite = { onOpenWebsite(service.id) },
                    onRegion = { url -> graph.services.setUrl(service.id, url) },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(
                onClick = { manageOpen = true },
                border = BorderStroke(1.dp, SnapSeekColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SnapSeekColors.TextMain),
            ) {
                Icon(UiIcons.Menu, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage services")
            }
        }
    }

    if (manageOpen) {
        ManageServicesDialog(graph = graph, services = services, onDismiss = { manageOpen = false })
    }
}

@Composable
private fun EngineBanner(state: EngineState, onRetry: () -> Unit) {
    when (state) {
        is EngineState.Installing -> Banner {
            Column(Modifier.weight(1f)) {
                Text(
                    "Setting up the browser runtime (first launch only) · ${state.phase}" +
                        if (state.percent >= 0) " ${state.percent.toInt()}%" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SnapSeekColors.TextMain,
                )
                Spacer(Modifier.height(8.dp))
                if (state.percent >= 0) {
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        is EngineState.Starting, EngineState.Idle -> Banner {
            Text("Starting Chromium…", style = MaterialTheme.typography.bodyMedium, color = SnapSeekColors.TextMain)
        }
        is EngineState.Failed -> Banner(border = SnapSeekColors.Danger) {
            Column(Modifier.weight(1f)) {
                Text("The browser runtime failed to start", style = MaterialTheme.typography.titleSmall, color = SnapSeekColors.Danger)
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        EngineState.Ready -> Unit
    }
}

@Composable
private fun Banner(border: Color = SnapSeekColors.Border, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .padding(bottom = 20.dp)
            .background(SnapSeekColors.Panel, RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ServiceCard(service: Service, ready: Boolean, onBrowse: () -> Unit, onOpenWebsite: () -> Unit, onRegion: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SnapSeekColors.Card),
        border = BorderStroke(1.dp, SnapSeekColors.Border),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ServiceIcon(service, size = 64.dp)
            Spacer(Modifier.height(16.dp))
            Text(service.name, style = MaterialTheme.typography.titleLarge, color = SnapSeekColors.TextMain)
            Spacer(Modifier.height(6.dp))
            when {
                service.regions.isNotEmpty() -> RegionPicker(service, onRegion)
                service.isBooru -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(service.host, style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        service.kind.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = SnapSeekColors.PrimaryHover,
                        modifier = Modifier.background(SnapSeekColors.PrimaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    if (service.nsfw) {
                        Text(
                            "NSFW",
                            style = MaterialTheme.typography.labelSmall,
                            color = SnapSeekColors.Danger,
                            modifier = Modifier.background(SnapSeekColors.Danger.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                else -> Text(service.host, style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onBrowse,
                enabled = ready || service.isBooru,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
            ) {
                Text(if (ready || service.isBooru) "Browse" else "Starting…")
            }
            if (service.isBooru) {
                TextButton(onClick = onOpenWebsite, enabled = ready, modifier = Modifier.height(30.dp)) {
                    Text("Open website instead", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun RegionPicker(service: Service, onRegion: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = service.regions.firstOrNull { it.url == service.url }
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
            Text(current?.name ?: service.host, style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted)
            Icon(UiIcons.ChevronDown, contentDescription = "Change region", tint = SnapSeekColors.TextMuted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            service.regions.forEach { region ->
                DropdownMenuItem(
                    text = { Text(region.name) },
                    onClick = {
                        open = false
                        onRegion(region.url)
                    },
                )
            }
        }
    }
}
