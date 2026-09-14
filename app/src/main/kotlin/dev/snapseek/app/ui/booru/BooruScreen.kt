package dev.snapseek.app.ui.booru

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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import dev.snapseek.app.AppGraph
import dev.snapseek.app.ui.common.DownloadTray
import dev.snapseek.app.ui.common.RemoteImage
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.app.vm.BooruViewModel
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.booru.TagCategory
import kotlinx.coroutines.flow.distinctUntilChanged

/** Native booru browsing: tag search with suggestions, masonry grid, and a detail view. Inspired by Boorusama. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BooruScreen(vm: BooruViewModel, graph: AppGraph, onOpenWeb: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val history by vm.history.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val jobs by graph.downloads.jobs.collectAsState()
    val gridState = rememberLazyStaggeredGridState()

    LaunchedEffect(gridState, state.posts.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (state.posts.isNotEmpty() && last >= state.posts.size - 10) vm.loadMore() }
    }
    LaunchedEffect(state.activeQuery) { gridState.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = state.queryText,
                        onValueChange = vm::onQueryChanged,
                        singleLine = true,
                        placeholder = { Text("Search tags, e.g. 1girl scenery -text  ·  rating:general  ·  sort:score") },
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
                                e.key == Key.Enter -> {
                                    vm.search()
                                    true
                                }
                                e.key == Key.Escape -> {
                                    vm.clearSuggestions()
                                    true
                                }
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
            }

            if (state.activeTags.isNotEmpty()) {
                FlowRow(Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.activeTags.forEach { tag ->
                        Row(
                            Modifier.background(SnapSeekColors.PrimaryContainer, RoundedCornerShape(999.dp)).padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tag, style = MaterialTheme.typography.labelMedium, color = Color(0xFFEDE0FF))
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
                    TextButton(onClick = vm::clearHistory, modifier = Modifier.height(26.dp)) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted)
                    }
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (state.posts.isEmpty() && !state.loading) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "No posts match these tags.", color = if (state.error != null) SnapSeekColors.Danger else SnapSeekColors.TextMuted)
                        if (state.error != null) TextButton(onClick = { vm.loadMore() }) { Text("Retry") }
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
                        PostCard(post, graph, onOpen = { vm.select(post) }, onSave = { vm.save(post) })
                    }
                }
            }

            DownloadTray(
                jobs = jobs,
                onDismissJob = graph.downloads::dismiss,
                onClearFinished = graph.downloads::clearFinished,
                emptyHint = "Click a post for details · hover and press the arrow to save",
            ) {
                if (state.loading) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = SnapSeekColors.PrimaryHover)
                val summary = buildString {
                    append("${state.posts.size} posts")
                    if (state.hiddenByBlacklist > 0) append(" · ${state.hiddenByBlacklist} hidden by blacklist")
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
                vm = vm,
                graph = graph,
                onOpenWeb = onOpenWeb,
            )
        }
    }
}

@Composable
private fun PostCard(post: BooruPost, graph: AppGraph, onOpen: () -> Unit, onSave: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(post.aspectRatio.coerceIn(0.45f, 2.2f))
            .clip(RoundedCornerShape(10.dp))
            .background(SnapSeekColors.CardRaised)
            .hoverable(interaction)
            .clickable(onClick = onOpen),
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
        if (hovered) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(start = 10.dp, end = 4.dp, top = 18.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${post.id}", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1f))
                if (post.score != null) Text("★ ${post.score}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFDE68A), modifier = Modifier.padding(end = 4.dp))
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
    TagCategory.GENERAL -> Color(0xFF93C5FD)
    TagCategory.META -> Color(0xFFFBBF24)
    TagCategory.UNKNOWN -> SnapSeekColors.TextMuted
}

fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> "%.1fk".format(n / 1000.0)
    else -> n.toString()
}
