package dev.snapseek.app.ui.booru

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import dev.snapseek.app.AppGraph
import dev.snapseek.app.ui.common.DownloadTray
import dev.snapseek.app.ui.common.RemoteImage
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.app.vm.BooruViewModel
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.booru.RemoteAccount
import dev.snapseek.core.booru.RemoteCollection
import dev.snapseek.core.booru.TagCategory
import dev.snapseek.core.download.BulkDownloader
import dev.snapseek.core.model.DownloadQuality
import dev.snapseek.core.model.OutputFormat
import kotlinx.coroutines.flow.distinctUntilChanged

/** Native booru browsing: tag search with suggestions, masonry grid, selection, bulk download, and a detail view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BooruScreen(vm: BooruViewModel, graph: AppGraph, onOpenWeb: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val history by vm.history.collectAsState()
    val bookmarked by vm.bookmarked.collectAsState()
    val bulkTasks by vm.bulkTasks.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val jobs by graph.downloads.jobs.collectAsState()
    val account by vm.account.collectAsState()
    val collections by vm.collections.collectAsState()
    val accountBusy by vm.accountBusy.collectAsState()
    val notice by vm.notice.collectAsState()
    val gridState = rememberLazyStaggeredGridState()
    var bulkDialog by remember { mutableStateOf(false) }
    var saveMenu by remember { mutableStateOf(false) }
    var boardMenu by remember { mutableStateOf(false) }

    LaunchedEffect(gridState, state.posts.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (state.posts.isNotEmpty() && last >= state.posts.size - 10) vm.loadMore() }
    }
    LaunchedEffect(state.activeTags) { gridState.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = state.queryText,
                        onValueChange = vm::onQueryChanged,
                        singleLine = true,
                        placeholder = { Text(vm.client.searchPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(UiIcons.Search, null, tint = SnapSeekColors.TextMuted, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (state.queryText.isNotBlank()) {
                                IconButton(onClick = { vm.search("") }) { Icon(UiIcons.Close, "Clear", tint = SnapSeekColors.TextMuted, modifier = Modifier.size(16.dp)) }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SnapSeekColors.PrimaryHover,
                            unfocusedBorderColor = SnapSeekColors.Border,
                            focusedContainerColor = SnapSeekColors.Card,
                            unfocusedContainerColor = SnapSeekColors.Card,
                        ),
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                            when {
                                e.type != KeyEventType.KeyDown -> false
                                e.key == Key.Enter -> { vm.search(); true }
                                e.key == Key.Escape -> { vm.clearSuggestions(); true }
                                else -> false
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = state.suggestions.isNotEmpty(),
                        onDismissRequest = vm::clearSuggestions,
                        properties = PopupProperties(focusable = false),
                    ) {
                        state.suggestions.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(s.name.replace('_', ' '), color = tagColor(s.category), modifier = Modifier.weight(1f))
                                        s.postCount?.let { Text(compact(it), style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted) }
                                    }
                                },
                                onClick = { vm.acceptSuggestion(s) },
                            )
                        }
                    }
                }
                Button(
                    onClick = { vm.search() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
                ) { Text("Search") }
                if (vm.client.safeModeTags.isNotEmpty()) {
                    ToolbarToggle(
                        icon = if (state.safeMode) UiIcons.ShieldCheck else UiIcons.Shield,
                        label = if (state.safeMode) "Safe mode on" else "Safe mode off",
                        active = state.safeMode,
                        onClick = vm::toggleSafeMode,
                    )
                }
                ToolbarToggle(icon = UiIcons.DownloadAll, label = "Download everything matching this search", active = false, onClick = { bulkDialog = true })
                if (vm.accountClient != null) {
                    AccountChip(
                        account = account,
                        busy = accountBusy,
                        collections = collections,
                        collectionNoun = vm.accountClient.collectionNoun,
                        onConnect = { onOpenWeb(vm.accountClient.loginUrl) },
                        onHomeFeed = { vm.search("") },
                        onOpenCollection = vm::openCollection,
                        onRefresh = { vm.refreshAccount(force = true) },
                        onOpenProfile = { account?.let { onOpenWeb("${vm.service.websiteUrl.trimEnd('/')}/${it.username}/") } },
                    )
                }
            }

            if (notice != null) {
                Text(
                    notice!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
                        .background(SnapSeekColors.PrimaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (state.activeTags.isNotEmpty()) {
                FlowRow(Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.activeTags.forEach { tag ->
                        Row(
                            Modifier.background(SnapSeekColors.PrimaryContainer, RoundedCornerShape(999.dp)).padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(vm.displayTag(tag), style = MaterialTheme.typography.labelMedium, color = Color(0xFFEDE0FF))
                            IconButton(onClick = { vm.removeTag(tag) }, modifier = Modifier.size(20.dp)) {
                                Icon(UiIcons.Close, "Remove $tag", tint = Color(0xFFEDE0FF), modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                }
            } else if (history.isNotEmpty()) {
                FlowRow(Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recent", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted, modifier = Modifier.padding(end = 4.dp, top = 4.dp))
                    history.take(8).forEach { q ->
                        Text(
                            q,
                            style = MaterialTheme.typography.labelMedium,
                            color = SnapSeekColors.TextMain,
                            modifier = Modifier
                                .background(SnapSeekColors.Card, RoundedCornerShape(999.dp))
                                .border(1.dp, SnapSeekColors.Border, RoundedCornerShape(999.dp))
                                .clickable { vm.search(q) }
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = SnapSeekColors.TextMuted,
                        modifier = Modifier.clickable(onClick = vm::clearHistory).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            bulkTasks.forEach { entry -> BulkBanner(entry, onCancel = { vm.cancelBulk(entry.task.id) }, onDismiss = { vm.dismissBulk(entry.task.id) }) }

            if (state.selection.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp)
                        .background(SnapSeekColors.PrimaryContainer.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${state.selection.size} selected", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::selectAllLoaded) { Text("Select all loaded", color = Color(0xFFEDE0FF)) }
                    Box {
                        Button(
                            onClick = { vm.saveSelected() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
                        ) {
                            Icon(UiIcons.Download, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save as ${settings.defaultFormat.label}")
                        }
                    }
                    Box {
                        IconButton(onClick = { saveMenu = true }) { Icon(UiIcons.ChevronDown, "Other formats", tint = Color.White) }
                        DropdownMenu(expanded = saveMenu, onDismissRequest = { saveMenu = false }) {
                            OutputFormat.entries.forEach { fmt ->
                                DropdownMenuItem(text = { Text(fmt.menuLabel) }, onClick = { saveMenu = false; vm.saveSelected(fmt) })
                            }
                        }
                    }
                    if (account != null && collections.isNotEmpty()) {
                        Box {
                            OutlinedButton(
                                onClick = { boardMenu = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            ) {
                                Icon(UiIcons.Pin, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save to ${vm.accountClient?.collectionNoun}")
                            }
                            DropdownMenu(expanded = boardMenu, onDismissRequest = { boardMenu = false }) {
                                collections.forEach { c ->
                                    DropdownMenuItem(text = { CollectionRow(c) }, onClick = { boardMenu = false; vm.saveSelectedToCollection(c) })
                                }
                            }
                        }
                    }
                    IconButton(onClick = vm::clearSelection) { Icon(UiIcons.Close, "Clear selection", tint = Color.White) }
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (state.posts.isEmpty() && !state.loading) {
                    Column(Modifier.align(Alignment.Center).padding(40.dp).widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val emptySearch = state.activeTags.isEmpty()
                        val message = when {
                            state.error != null -> state.error
                            state.needsQuery -> "Type something above to search ${vm.service.name}."
                            emptySearch && vm.client.emptyQueryHint != null -> vm.client.emptyQueryHint
                            else -> "No posts match these tags."
                        }
                        Text(message!!, color = if (state.error != null) SnapSeekColors.Danger else SnapSeekColors.TextMuted, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.error != null) TextButton(onClick = { vm.loadMore() }) { Text("Retry") }
                            if (vm.accountClient != null && account == null && emptySearch) {
                                Button(
                                    onClick = { onOpenWeb(vm.accountClient.loginUrl) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
                                ) {
                                    Icon(UiIcons.User, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Connect account")
                                }
                            }
                            TextButton(onClick = { onOpenWeb(vm.service.websiteUrl) }) { Text("Open the website instead", color = SnapSeekColors.PrimaryHover) }
                        }
                        if (vm.accountClient != null && account == null && emptySearch) {
                            Text(
                                "Log in on the website tab that opens, then press Home and come back here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SnapSeekColors.TextMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(settings.gridSize.minColumnDp.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalItemSpacing = 10.dp,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            graph = graph,
                            selected = post.id in state.selection,
                            selecting = state.selection.isNotEmpty(),
                            bookmarked = post.id in bookmarked,
                            onOpen = { vm.select(post) },
                            onSave = { vm.save(post) },
                            onToggleSelect = { vm.toggleSelect(post) },
                            onToggleBookmark = { vm.toggleBookmark(post) },
                        )
                    }
                }
            }

            DownloadTray(
                jobs = jobs,
                onDismissJob = graph.downloads::dismiss,
                onClearFinished = graph.downloads::clearFinished,
                emptyHint = "Click a post for details · hover for save, select and bookmark",
            ) {
                if (state.loading) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = SnapSeekColors.PrimaryHover)
                val summary = buildString {
                    append("${state.posts.size} posts")
                    if (state.hiddenByBlacklist > 0) append(" · ${state.hiddenByBlacklist} hidden by blacklist")
                    if (state.safeMode) append(" · safe mode")
                    if (state.endReached && state.posts.isNotEmpty()) append(" · end")
                }
                Text(summary, style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }

        val selected = state.selected
        if (selected != null) {
            PostDetail(
                post = selected,
                categories = state.selectedCategories,
                showOriginal = state.showOriginal,
                bookmarked = selected.id in bookmarked,
                vm = vm,
                graph = graph,
                onOpenWeb = onOpenWeb,
            )
        }
    }

    if (bulkDialog) {
        BulkDownloadDialog(
            query = state.activeTags.let { if (it.isEmpty()) "everything" else vm.client.joinQuery(it) },
            defaultMax = settings.bulkMaxPosts,
            defaultQuality = settings.booruQuality,
            onStart = { max, quality, subfolder ->
                graph.settings.update { it.copy(bulkMaxPosts = max) }
                vm.startBulk(max, quality, subfolder)
                bulkDialog = false
            },
            onDismiss = { bulkDialog = false },
        )
    }
}

/** "Connect account" when the browser isn't logged in; otherwise the username with a menu of feed and collections. */
@Composable
private fun AccountChip(
    account: RemoteAccount?,
    busy: Boolean,
    collections: List<RemoteCollection>,
    collectionNoun: String,
    onConnect: () -> Unit,
    onHomeFeed: () -> Unit,
    onOpenCollection: (RemoteCollection) -> Unit,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { if (account == null) onConnect() else open = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (account != null) SnapSeekColors.PrimaryHover else SnapSeekColors.Border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (account != null) SnapSeekColors.TextMain else SnapSeekColors.TextMuted),
            modifier = Modifier.height(44.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = SnapSeekColors.PrimaryHover)
            } else {
                Icon(UiIcons.User, null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(account?.let { "@${it.username}" } ?: "Connect account", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
            if (account != null) {
                Spacer(Modifier.width(4.dp))
                Icon(UiIcons.ChevronDown, null, modifier = Modifier.size(14.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 260.dp, max = 360.dp)) {
            DropdownMenuItem(
                text = { Text("Home feed") },
                leadingIcon = { Icon(UiIcons.Home, null, modifier = Modifier.size(16.dp)) },
                onClick = { open = false; onHomeFeed() },
            )
            if (collections.isNotEmpty()) {
                Text("Your ${collectionNoun}s", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                collections.take(40).forEach { c ->
                    DropdownMenuItem(text = { CollectionRow(c) }, onClick = { open = false; onOpenCollection(c) })
                }
            }
            DropdownMenuItem(text = { Text("Refresh", color = SnapSeekColors.TextMuted) }, onClick = { open = false; onRefresh() })
            DropdownMenuItem(text = { Text("Open my profile on the website", color = SnapSeekColors.TextMuted) }, onClick = { open = false; onOpenProfile() })
        }
    }
}

@Composable
fun CollectionRow(c: RemoteCollection) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(c.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (c.isPrivate) Icon(UiIcons.Shield, "Secret", tint = SnapSeekColors.TextMuted, modifier = Modifier.size(12.dp))
        c.count?.let { Text(compact(it), style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted) }
    }
}

@Composable
private fun ToolbarToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).background(if (active) SnapSeekColors.PrimaryContainer else SnapSeekColors.Card, RoundedCornerShape(12.dp))
            .border(1.dp, if (active) SnapSeekColors.PrimaryHover else SnapSeekColors.Border, RoundedCornerShape(12.dp)),
    ) {
        Icon(icon, label, tint = if (active) Color.White else SnapSeekColors.TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BulkBanner(entry: BulkDownloader.Entry, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val (text, color) = when (val s = entry.status) {
        is BulkDownloader.Status.Running -> "Downloading “${entry.task.query}”: ${s.enqueued} of ${entry.task.maxPosts} queued · page ${s.pages}" +
            (if (s.hidden > 0) " · ${s.hidden} hidden by blacklist" else "") to SnapSeekColors.PrimaryHover
        is BulkDownloader.Status.Done -> "Bulk “${entry.task.query}”: ${s.enqueued} queued, ${s.reason}" + (if (s.hidden > 0) ", ${s.hidden} hidden" else "") to SnapSeekColors.Success
        is BulkDownloader.Status.Cancelled -> "Bulk “${entry.task.query}” cancelled after ${s.enqueued} queued" to SnapSeekColors.Warning
        is BulkDownloader.Status.Failed -> "Bulk “${entry.task.query}” failed: ${s.message}" to SnapSeekColors.Danger
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp).background(SnapSeekColors.Card, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.isActive) {
                TextButton(onClick = onCancel, modifier = Modifier.height(28.dp)) { Text("Cancel", color = SnapSeekColors.TextMuted) }
            } else {
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(UiIcons.Close, "Dismiss", tint = SnapSeekColors.TextMuted, modifier = Modifier.size(12.dp)) }
            }
        }
        if (entry.isActive) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { entry.enqueued.toFloat() / entry.task.maxPosts }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BulkDownloadDialog(query: String, defaultMax: Int, defaultQuality: DownloadQuality, onStart: (Int, DownloadQuality, Boolean) -> Unit, onDismiss: () -> Unit) {
    var maxText by remember { mutableStateOf(defaultMax.toString()) }
    var quality by remember { mutableStateOf(defaultQuality) }
    var subfolder by remember { mutableStateOf(true) }
    val max = maxText.trim().toIntOrNull()?.coerceIn(1, 10_000)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = SnapSeekColors.Panel, border = BorderStroke(1.dp, SnapSeekColors.Border), modifier = Modifier.width(440.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Download everything", style = MaterialTheme.typography.titleLarge, color = SnapSeekColors.TextMain)
                Text("Walks the results for “$query” page by page and saves each post. Blacklisted posts are skipped, and posts you already saved are recognised without downloading them again.", style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted)
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Stop after this many posts") },
                    singleLine = true,
                    isError = max == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Quality", style = MaterialTheme.typography.labelMedium, color = SnapSeekColors.TextMuted)
                    DownloadQuality.entries.forEach { q ->
                        FilterChip(
                            selected = quality == q,
                            onClick = { quality = q },
                            label = { Text(q.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SnapSeekColors.PrimaryContainer, selectedLabelColor = Color.White, labelColor = SnapSeekColors.TextMuted),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { subfolder = !subfolder }) {
                    Checkbox(checked = subfolder, onCheckedChange = { subfolder = it }, colors = CheckboxDefaults.colors(checkedColor = SnapSeekColors.Primary))
                    Text("Put the files in a folder named after the search", color = SnapSeekColors.TextMain, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = SnapSeekColors.TextMuted) }
                    Button(
                        onClick = { max?.let { onStart(it, quality, subfolder) } },
                        enabled = max != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
                    ) { Text("Start") }
                }
            }
        }
    }
}

@Composable
private fun PostCard(
    post: BooruPost,
    graph: AppGraph,
    selected: Boolean,
    selecting: Boolean,
    bookmarked: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onToggleSelect: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(post.aspectRatio.coerceIn(0.45f, 2.2f))
            .clip(RoundedCornerShape(10.dp))
            .background(SnapSeekColors.CardRaised)
            .then(if (selected) Modifier.border(3.dp, SnapSeekColors.PrimaryHover, RoundedCornerShape(10.dp)) else Modifier)
            .hoverable(interaction)
            .clickable(onClick = if (selecting) onToggleSelect else onOpen),
    ) {
        RemoteImage(post.previewUrl, graph.imageLoader, Modifier.fillMaxSize())
        if (post.isAnimated) {
            Text(
                post.extension.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (hovered || selected || selecting) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                    .background(if (selected) SnapSeekColors.Primary else Color(0x99000000), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .clickable(onClick = onToggleSelect),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(UiIcons.Check, "Selected", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        if (bookmarked && !hovered) {
            Icon(UiIcons.HeartFilled, "Bookmarked", tint = Color(0xFFF472B6), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(14.dp))
        }
        if (hovered) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(start = 10.dp, end = 2.dp, top = 18.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(post.title ?: "#${post.displayId}", style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (post.score != null) Text("★ ${post.score}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFDE68A), modifier = Modifier.padding(end = 2.dp))
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(28.dp)) {
                    Icon(if (bookmarked) UiIcons.HeartFilled else UiIcons.Heart, if (bookmarked) "Remove bookmark" else "Bookmark", tint = if (bookmarked) Color(0xFFF472B6) else Color.White, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onSave, modifier = Modifier.size(28.dp)) {
                    Icon(UiIcons.Download, "Save", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

fun tagColor(category: TagCategory): Color = when (category) {
    TagCategory.ARTIST -> Color(0xFFF87171)
    TagCategory.COPYRIGHT -> Color(0xFFC084FC)
    TagCategory.CHARACTER -> Color(0xFF4ADE80)
    TagCategory.SPECIES -> Color(0xFF2DD4BF)
    TagCategory.GENERAL -> Color(0xFF93C5FD)
    TagCategory.META -> Color(0xFFFBBF24)
    TagCategory.LORE -> Color(0xFFA3E635)
    TagCategory.UNKNOWN -> SnapSeekColors.TextMuted
}

fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> "%.1fk".format(n / 1000.0)
    else -> n.toString()
}
