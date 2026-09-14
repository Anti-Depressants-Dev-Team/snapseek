package dev.snapseek.app.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.app.AppGraph
import dev.snapseek.app.ui.common.DownloadTray
import dev.snapseek.app.ui.common.RemoteImage
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.core.model.Bookmark
import kotlinx.coroutines.launch

/** Posts kept for later, across every booru. Save them, open their page, or let them go. */
@Composable
fun BookmarksScreen(graph: AppGraph, onBack: () -> Unit, onOpenWeb: (String, String?) -> Unit, onSave: (Bookmark) -> Unit) {
    val bookmarks by graph.bookmarks.entries.collectAsState()
    val jobs by graph.downloads.jobs.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(UiIcons.ArrowLeft, "Back", tint = SnapSeekColors.TextMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Bookmarks", style = MaterialTheme.typography.headlineMedium, color = SnapSeekColors.TextMain)
                Text(
                    if (bookmarks.isEmpty()) "Press the heart on any booru post to keep it here." else "${bookmarks.size} posts · hover one to save, open or remove it",
                    style = MaterialTheme.typography.bodySmall,
                    color = SnapSeekColors.TextMuted,
                )
            }
            if (bookmarks.isNotEmpty()) {
                TextButton(onClick = { bookmarks.forEach(onSave) }) { Text("Save all", color = SnapSeekColors.PrimaryHover) }
                TextButton(onClick = { scope.launch { graph.bookmarks.clear() } }) { Text("Clear all", color = SnapSeekColors.Danger) }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (bookmarks.isEmpty()) {
                Text("Nothing bookmarked yet.", color = SnapSeekColors.TextMuted, modifier = Modifier.align(Alignment.Center))
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(settings.gridSize.minColumnDp.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 20.dp),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(bookmarks, key = { it.id }) { b ->
                    BookmarkCard(
                        bookmark = b,
                        graph = graph,
                        onOpen = { onOpenWeb(b.pageUrl, b.serviceId) },
                        onSave = { onSave(b) },
                        onRemove = { scope.launch { graph.bookmarks.remove(b.serviceId, b.postId) } },
                    )
                }
            }
        }

        DownloadTray(jobs = jobs, onDismissJob = graph.downloads::dismiss, onClearFinished = graph.downloads::clearFinished, emptyHint = "Bookmarks live in your data folder and survive restarts") {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BookmarkCard(bookmark: Bookmark, graph: AppGraph, onOpen: () -> Unit, onSave: () -> Unit, onRemove: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.fillMaxWidth().aspectRatio(bookmark.aspectRatio.coerceIn(0.45f, 2.2f)).clip(RoundedCornerShape(10.dp))
            .background(SnapSeekColors.CardRaised).hoverable(interaction).clickable(onClick = onOpen),
    ) {
        RemoteImage(bookmark.previewUrl, graph.imageLoader, Modifier.fillMaxSize())
        Text(
            bookmark.serviceName,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        )
        if (hovered) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(start = 10.dp, end = 2.dp, top = 18.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${bookmark.postId}", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) { Icon(UiIcons.Trash, "Remove", tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp)) }
                IconButton(onClick = onOpen, modifier = Modifier.size(28.dp)) { Icon(UiIcons.ExternalLink, "Open page", tint = Color.White, modifier = Modifier.size(14.dp)) }
                IconButton(onClick = onSave, modifier = Modifier.size(28.dp)) { Icon(UiIcons.Download, "Save", tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}
