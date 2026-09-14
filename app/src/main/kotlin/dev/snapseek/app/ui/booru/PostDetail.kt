package dev.snapseek.app.ui.booru

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.app.AppGraph
import dev.snapseek.app.ui.common.RemoteImage
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.app.vm.BooruViewModel
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.booru.TagCategory
import dev.snapseek.core.model.DownloadQuality
import dev.snapseek.core.model.OutputFormat
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/** Full-window overlay for one post: the image on the left, everything known about it on the right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostDetail(
    post: BooruPost,
    categories: Map<String, TagCategory>,
    showOriginal: Boolean,
    bookmarked: Boolean,
    vm: BooruViewModel,
    graph: AppGraph,
    onOpenWeb: (String) -> Unit,
) {
    val settings by graph.settings.settings.collectAsState()
    val focus = remember { FocusRequester() }
    var formatMenu by remember { mutableStateOf(false) }
    var quality by remember(post.id) { mutableStateOf(settings.booruQuality) }
    LaunchedEffect(post.id) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF50A0A0C))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Escape -> { vm.closeDetail(); true }
                    Key.DirectionLeft -> { vm.previous(); true }
                    Key.DirectionRight -> { vm.next(); true }
                    Key.S -> { vm.save(post, quality = quality); true }
                    Key.B -> { vm.toggleBookmark(post); true }
                    else -> false
                }
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                RemoteImage(
                    url = if (showOriginal || !post.hasSample) post.fileUrl else post.sampleUrl,
                    loader = graph.imageLoader,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentScale = ContentScale.Fit,
                    large = true,
                    placeholder = Color.Transparent,
                )
                if (post.isVideo) {
                    Text(
                        "Video posts can't be previewed yet. Save keeps the original file.",
                        color = SnapSeekColors.TextMuted,
                        modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(8.dp)).padding(12.dp),
                    )
                }
                IconButton(onClick = vm::closeDetail, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    Icon(UiIcons.Close, "Close", tint = Color.White)
                }
                IconButton(onClick = vm::previous, modifier = Modifier.align(Alignment.CenterStart).padding(8.dp)) {
                    Icon(UiIcons.ChevronLeft, "Previous", tint = Color.White)
                }
                IconButton(onClick = vm::next, modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)) {
                    Icon(UiIcons.ChevronRight, "Next", tint = Color.White)
                }
            }

            Column(
                Modifier.width(360.dp).fillMaxHeight().background(SnapSeekColors.Panel).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#${post.id}", style = MaterialTheme.typography.headlineSmall, color = SnapSeekColors.TextMain)
                        Text(
                            listOfNotNull("${post.width} × ${post.height}", post.extension.uppercase(), post.ratingLabel, post.score?.let { "★ $it" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = SnapSeekColors.TextMuted,
                        )
                    }
                    IconButton(onClick = { vm.toggleBookmark(post) }) {
                        Icon(
                            if (bookmarked) UiIcons.HeartFilled else UiIcons.Heart,
                            if (bookmarked) "Remove bookmark" else "Bookmark",
                            tint = if (bookmarked) Color(0xFFF472B6) else SnapSeekColors.TextMuted,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { vm.save(post, quality = quality) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
                    ) {
                        Icon(UiIcons.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (post.isVideo) "Save video" else "Save as ${settings.defaultFormat.label}")
                    }
                    Box {
                        IconButton(onClick = { formatMenu = true }, enabled = !post.isVideo) {
                            Icon(UiIcons.ChevronDown, "Other formats", tint = SnapSeekColors.TextMain)
                        }
                        DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                            OutputFormat.entries.forEach { fmt ->
                                DropdownMenuItem(text = { Text(fmt.menuLabel) }, onClick = { formatMenu = false; vm.save(post, fmt, quality) })
                            }
                        }
                    }
                }

                if (post.hasSample && !post.isVideo) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Quality", style = MaterialTheme.typography.labelMedium, color = SnapSeekColors.TextMuted, modifier = Modifier.padding(end = 4.dp))
                        DownloadQuality.entries.forEach { q ->
                            FilterChip(
                                selected = quality == q,
                                onClick = { quality = q },
                                label = { Text(q.label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SnapSeekColors.PrimaryContainer, selectedLabelColor = Color.White, labelColor = SnapSeekColors.TextMuted),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = vm::toggleOriginal, modifier = Modifier.height(30.dp)) {
                            Text(if (showOriginal) "Show sample" else "View original", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.PrimaryHover)
                        }
                    }
                }

                HorizontalDivider(color = SnapSeekColors.Border)

                LinkRow("Post page", vm.postPageUrl(post), onOpenWeb)
                post.source?.let { LinkRow("Source", it, onOpenWeb) }

                HorizontalDivider(color = SnapSeekColors.Border)

                val groups = vm.groupedTags(post, categories)
                groups.forEach { (category, tags) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (categories.isEmpty() && category == TagCategory.UNKNOWN) "Tags" else category.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = tagColor(category),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { tag -> TagChip(tag, tagColor(category), onClick = { vm.addTag(tag) }) }
                        }
                    }
                }
                if (categories.isEmpty() && post.tags.isNotEmpty()) {
                    Text("Loading tag categories…", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted)
                }
                Text("Esc closes · ← → move · S saves · B bookmarks", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, url: String, onOpenWeb: (String) -> Unit) {
    val host = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted)
            Text(host, style = MaterialTheme.typography.bodyMedium, color = SnapSeekColors.PrimaryHover, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onOpenWeb(url) })
        }
        IconButton(onClick = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null) }, modifier = Modifier.size(28.dp)) {
            Icon(UiIcons.Copy, "Copy link", tint = SnapSeekColors.TextMuted, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = { onOpenWeb(url) }, modifier = Modifier.size(28.dp)) {
            Icon(UiIcons.ExternalLink, "Open", tint = SnapSeekColors.TextMuted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun TagChip(tag: String, color: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        tag.replace('_', ' '),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
