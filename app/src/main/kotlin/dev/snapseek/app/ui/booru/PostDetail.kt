package dev.snapseek.app.ui.booru

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
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
    val account by vm.account.collectAsState()
    val collections by vm.collections.collectAsState()
    val lastCollection by vm.lastCollection.collectAsState()
    val focus = remember { FocusRequester() }
    var formatMenu by remember { mutableStateOf(false) }
    var boardMenu by remember { mutableStateOf(false) }
    var newBoardDialog by remember { mutableStateOf(false) }
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
                    Key.P -> { lastCollection?.let { vm.saveToCollection(post, it) }; true }
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
                        Text(
                            post.title ?: "#${post.displayId}",
                            style = if (post.title != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                            color = SnapSeekColors.TextMain,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                if (post.title != null) "#${post.displayId}" else null,
                                if (post.width > 0) "${post.width} × ${post.height}" else null,
                                post.extension.uppercase(),
                                post.ratingLabel.takeIf { post.rating.isNotBlank() },
                                post.score?.let { "★ $it" },
                            ).joinToString(" · "),
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

                if (vm.accountClient != null && account != null) {
                    val noun = vm.accountClient.collectionNoun
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { lastCollection?.let { vm.saveToCollection(post, it) } ?: run { boardMenu = true } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SnapSeekColors.PrimaryHover),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SnapSeekColors.TextMain),
                        ) {
                            Icon(UiIcons.Pin, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(lastCollection?.let { "Save to ${it.name}" } ?: "Save to a $noun", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box {
                            IconButton(onClick = { boardMenu = true }) { Icon(UiIcons.ChevronDown, "Choose $noun", tint = SnapSeekColors.TextMain) }
                            DropdownMenu(expanded = boardMenu, onDismissRequest = { boardMenu = false }, modifier = Modifier.widthIn(min = 240.dp, max = 340.dp)) {
                                collections.forEach { c ->
                                    DropdownMenuItem(text = { CollectionRow(c) }, onClick = { boardMenu = false; vm.saveToCollection(post, c) })
                                }
                                if (collections.isNotEmpty()) HorizontalDivider(color = SnapSeekColors.Border)
                                DropdownMenuItem(
                                    text = { Text("New $noun…", color = SnapSeekColors.PrimaryHover) },
                                    leadingIcon = { Icon(UiIcons.Plus, null, modifier = Modifier.size(14.dp)) },
                                    onClick = { boardMenu = false; newBoardDialog = true },
                                )
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
                            if (categories.isEmpty() && category == TagCategory.UNKNOWN) "Tags" else vm.client.categoryLabel(category),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = tagColor(category),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { tag -> TagChip(tag, tagColor(category), onClick = { vm.addTag(tag) }) }
                        }
                    }
                }
                if (categories.isEmpty() && post.tags.isNotEmpty() && post.tagCategories == null) {
                    Text("Loading tag categories…", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted)
                }
                Text(
                    if (account != null) "Esc closes · ← → move · S saves · B bookmarks · P saves to your last ${vm.accountClient?.collectionNoun}" else "Esc closes · ← → move · S saves · B bookmarks",
                    style = MaterialTheme.typography.labelSmall,
                    color = SnapSeekColors.TextMuted.copy(alpha = 0.6f),
                )
            }
        }
    }

    if (newBoardDialog) {
        NewCollectionDialog(
            noun = vm.accountClient?.collectionNoun ?: "collection",
            onCreate = { name -> vm.createCollection(name, thenSave = post); newBoardDialog = false },
            onDismiss = { newBoardDialog = false },
        )
    }
}

@Composable
private fun NewCollectionDialog(noun: String, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = SnapSeekColors.Panel, border = BorderStroke(1.dp, SnapSeekColors.Border), modifier = Modifier.width(380.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("New $noun", style = MaterialTheme.typography.titleLarge, color = SnapSeekColors.TextMain)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("The $noun is created on the site and this post is saved into it.", style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = SnapSeekColors.TextMuted) }
                    Button(
                        onClick = { onCreate(name.trim()) },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
                    ) { Text("Create and save") }
                }
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
