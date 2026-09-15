package dev.snapseek.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.android.AndroidGraph
import dev.snapseek.android.BrowseModel
import dev.snapseek.core.booru.BooruPost
import kotlinx.coroutines.flow.distinctUntilChanged

/** The grid: a search box, a masonry of results that keeps loading as you scroll, and the account behind it. */
@Composable
fun BrowseScreen(
    model: BrowseModel,
    graph: AndroidGraph,
    onBack: () -> Unit,
    onConnectAccount: () -> Unit,
) {
    val state by model.state.collectAsState()
    val grid = rememberLazyStaggeredGridState()

    LaunchedEffect(grid, state.posts.size) {
        androidx.compose.runtime.snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (state.posts.isNotEmpty() && last >= state.posts.size - 8) model.loadMore() }
    }
    LaunchedEffect(state.activeTags) { grid.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(Snap.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back", color = Snap.TextMuted) }
            Text(
                model.service.name,
                style = MaterialTheme.typography.titleMedium,
                color = Snap.TextMain,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.accountClient != null) AccountButton(state, model, onConnectAccount)
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = model::onQueryChanged,
            singleLine = true,
            placeholder = { Text(model.client.searchPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Snap.TextMuted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { model.search() }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Snap.PurpleBright,
                unfocusedBorderColor = Snap.Border,
                focusedContainerColor = Snap.Card,
                unfocusedContainerColor = Snap.Card,
                focusedTextColor = Snap.TextMain,
                unfocusedTextColor = Snap.TextMain,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )

        if (state.activeTags.isNotEmpty()) {
            Text(
                state.activeTags.joinToString(", ") { model.displayTag(it) },
                style = MaterialTheme.typography.labelMedium,
                color = Snap.PurpleBright,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        state.notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(160.dp),
                state = grid,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalItemSpacing = 8.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.posts, key = { it.id }) { post ->
                    PostCard(post, graph) { model.open(post) }
                }
            }

            if (state.posts.isEmpty() && !state.loading) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        state.error
                            ?: if (state.needsQuery) "Search ${model.service.name} above."
                            else model.client.emptyQueryHint ?: "Nothing matches that.",
                        color = if (state.error != null) Snap.Danger else Snap.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                    if (state.error != null) TextButton(onClick = { model.loadMore() }) { Text("Try again") }
                }
            }

            if (state.loading) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.BottomCenter).padding(24.dp).size(28.dp),
                    color = Snap.PurpleBright,
                    strokeWidth = 3.dp,
                )
            }
        }
    }

    state.open?.let { post ->
        PostSheet(post = post, model = model, graph = graph, onClose = { model.open(null) })
    }
}

@Composable
private fun PostCard(post: BooruPost, graph: AndroidGraph, onClick: () -> Unit) {
    val ratio = if (post.width > 0 && post.height > 0) {
        (post.width.toFloat() / post.height).coerceIn(0.5f, 1.8f)
    } else {
        0.75f
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        RemoteImage(url = post.previewUrl, loader = graph.images, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun AccountButton(state: BrowseModel.State, model: BrowseModel, onConnect: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val account = state.account
    Box {
        TextButton(onClick = { if (account == null) onConnect() else menu = true }) {
            Text(
                account?.username ?: "Connect",
                color = if (account != null) Snap.PurpleBright else Snap.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(if (account != null) 110.dp else 80.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("My feed") }, onClick = { menu = false; model.search("") })
            model.accountClient?.savedQuery?.let { saved ->
                DropdownMenuItem(text = { Text("Everything I saved") }, onClick = { menu = false; model.search(saved) })
            }
            state.collections.take(30).forEach { collection ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(collection.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            collection.count?.let {
                                Spacer(Modifier.width(8.dp))
                                Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = Snap.TextMuted)
                            }
                        }
                    },
                    onClick = { menu = false; model.openCollection(collection) },
                )
            }
            DropdownMenuItem(text = { Text("Refresh", color = Snap.TextMuted) }, onClick = { menu = false; model.refreshAccount(force = true) })
        }
    }
}
