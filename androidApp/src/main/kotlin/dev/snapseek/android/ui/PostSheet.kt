package dev.snapseek.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.android.AndroidGraph
import dev.snapseek.android.BrowseModel
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.model.OutputFormat

/** One post, full size, with the two things you came for: save it, or put it in one of your collections. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostSheet(post: BooruPost, model: BrowseModel, graph: AndroidGraph, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by model.state.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    var formats by remember { mutableStateOf(false) }
    var collections by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = Snap.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                RemoteImage(
                    url = if (post.hasSample) post.sampleUrl else post.fileUrl,
                    loader = graph.images,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            post.title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = Snap.TextMain, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOfNotNull(
                    post.owner,
                    if (post.width > 0) "${post.width} × ${post.height}" else null,
                    post.rating.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Snap.TextMuted,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { model.save(post); onClose() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Snap.Purple),
                ) { Text("Save as ${settings.defaultFormat.name.lowercase()}") }

                Box {
                    OutlinedButton(onClick = { formats = true }, shape = RoundedCornerShape(12.dp)) { Text("…") }
                    DropdownMenu(expanded = formats, onDismissRequest = { formats = false }) {
                        OutputFormat.entries.forEach { format ->
                            DropdownMenuItem(
                                text = { Text(format.name.lowercase()) },
                                onClick = { formats = false; model.save(post, format); onClose() },
                            )
                        }
                    }
                }
            }

            if (state.account != null && state.collections.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { collections = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Save to my ${model.accountClient?.collectionNoun ?: "collection"}s")
                    }
                    DropdownMenu(expanded = collections, onDismissRequest = { collections = false }) {
                        state.collections.take(40).forEach { collection ->
                            DropdownMenuItem(
                                text = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { collections = false; model.saveToCollection(post, collection); onClose() },
                            )
                        }
                    }
                }
            }

            if (post.tags.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    post.tags.take(20).forEach { tag ->
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = Snap.TextMain,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Snap.Card)
                                .clickable { model.search(tag); onClose() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
