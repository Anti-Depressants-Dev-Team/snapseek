package dev.snapseek.app.ui.booru

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.core.booru.RemoteCollection
import dev.snapseek.core.booru.matching
import kotlinx.coroutines.delay

private val ROW_HEIGHT = 40.dp
private const val VISIBLE_ROWS = 8
private const val MAX_ROWS = 300

/**
 * The menu for picking one of someone's collections. With a hundred boards a plain list is a scrolling contest,
 * so it opens with the cursor in a filter box: type a few letters, press Enter to take the first match. What is
 * typed doubles as the name when there is nothing to match and a new collection is offered instead.
 *
 * [header] and [footer] hold whatever else belongs in that particular menu (the home feed entry, a refresh item).
 */
@Composable
fun CollectionMenu(
    expanded: Boolean,
    collections: List<RemoteCollection>,
    noun: String,
    onDismiss: () -> Unit,
    onPick: (RemoteCollection) -> Unit,
    createLabel: String? = null,
    /** False when there is nothing sensible to do with an empty name, so the row waits for one to be typed. */
    createWhenEmpty: Boolean = true,
    onCreate: (String) -> Unit = {},
    header: @Composable ColumnScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    var query by remember(expanded) { mutableStateOf("") }
    val filtered = remember(query, collections) { collections.matching(query) }
    val focus = remember(expanded) { FocusRequester() }
    val scroll = rememberScrollState()

    LaunchedEffect(expanded, query) { scroll.scrollTo(0) }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(80)
            runCatching { focus.requestFocus() }
        }
    }

    fun take(collection: RemoteCollection) {
        onDismiss()
        onPick(collection)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(340.dp),
    ) {
        header()

        if (collections.size > 6) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = { Text("Filter ${collections.size} ${noun}s", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(UiIcons.Search, null, tint = SnapSeekColors.TextMuted, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            UiIcons.Close,
                            "Clear the filter",
                            tint = SnapSeekColors.TextMuted,
                            modifier = Modifier.size(16.dp).clickable { query = "" },
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SnapSeekColors.PrimaryHover,
                    unfocusedBorderColor = SnapSeekColors.Border,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> filtered.firstOrNull()?.let { take(it); true } ?: false
                            Key.Escape -> { onDismiss(); true }
                            else -> false
                        }
                    },
            )
        }

        if (filtered.isEmpty()) {
            Text(
                if (collections.isEmpty()) "No ${noun}s yet" else "No $noun matches \"${query.trim()}\"",
                style = MaterialTheme.typography.bodySmall,
                color = SnapSeekColors.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        } else {
            // A lazy list cannot go in a menu: the popup measures its content and lazy layouts have no
            // intrinsic size. A plain scrolling column of a few hundred rows costs nothing anyway.
            Column(Modifier.heightIn(max = ROW_HEIGHT * VISIBLE_ROWS).verticalScroll(scroll)) {
                filtered.take(MAX_ROWS).forEach { collection ->
                    Row(
                        Modifier.fillMaxWidth().height(ROW_HEIGHT).clickable { take(collection) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CollectionRow(collection)
                    }
                }
            }
        }

        val typed = query.trim()
        if (createLabel != null && (createWhenEmpty || typed.isNotEmpty())) {
            HorizontalDivider(color = SnapSeekColors.Border)
            DropdownMenuItem(
                text = {
                    Text(
                        if (typed.isEmpty()) createLabel else "Create \"$typed\"",
                        color = SnapSeekColors.PrimaryHover,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(UiIcons.Plus, null, tint = SnapSeekColors.PrimaryHover, modifier = Modifier.size(14.dp)) },
                onClick = { onDismiss(); onCreate(typed) },
            )
        }

        footer()
    }
}

/** A row of the menu above: name, a lock for secret ones, and how many posts are in it. */
@Composable
fun CollectionRow(c: RemoteCollection) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (c.isPrivate) Icon(UiIcons.Shield, "Secret", tint = SnapSeekColors.TextMuted, modifier = Modifier.size(12.dp))
        c.count?.let { Text(compact(it), style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted) }
    }
}


