package dev.snapseek.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.snapseek.app.AppGraph
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.core.booru.BooruPresets
import dev.snapseek.core.model.Service
import dev.snapseek.core.model.ServiceKind
import dev.snapseek.core.model.ServiceType
import kotlinx.coroutines.launch

private val NO_KEY_KINDS = setOf(ServiceKind.WEB, ServiceKind.MOEBOORU, ServiceKind.PINTEREST, ServiceKind.PIXIV, ServiceKind.DEVIANTART, ServiceKind.ZEROCHAN, ServiceKind.WEB_GRID)

/** Three tabs: what you have, sites you can add in one click, and any other site by URL. */
@Composable
fun ManageServicesDialog(graph: AppGraph, services: List<Service>, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SnapSeekColors.Panel,
            border = BorderStroke(1.dp, SnapSeekColors.Border),
            modifier = Modifier.width(600.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Services", style = MaterialTheme.typography.titleLarge, color = SnapSeekColors.TextMain, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(UiIcons.Close, "Close", tint = SnapSeekColors.TextMuted) }
                }
                Spacer(Modifier.height(8.dp))
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = SnapSeekColors.PrimaryHover) {
                    listOf("Your services", "Add a site", "Add by URL").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }, selectedContentColor = SnapSeekColors.TextMain, unselectedContentColor = SnapSeekColors.TextMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                when (tab) {
                    0 -> YourServices(graph, services)
                    1 -> AddPreset(graph, services, onAdded = { tab = 0 })
                    else -> AddByUrl(graph, onAdded = { tab = 0 })
                }
            }
        }
    }
}

@Composable
private fun YourServices(graph: AppGraph, services: List<Service>) {
    var editing by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(services, key = { it.id }) { service ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = service.enabled,
                        onCheckedChange = { graph.services.setEnabled(service.id, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = SnapSeekColors.Primary),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(service.name, color = SnapSeekColors.TextMain, style = MaterialTheme.typography.bodyLarge)
                            if (service.nsfw) Pill("NSFW", SnapSeekColors.Danger)
                            if (service.credentials != null) Pill("KEY", SnapSeekColors.Success)
                            else if (service.kind.needsKey) Pill("KEY NEEDED", SnapSeekColors.Warning)
                        }
                        Text(
                            if (service.isBooru) "${service.host} · ${service.kind.label}" else service.host,
                            color = SnapSeekColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (service.kind !in NO_KEY_KINDS) {
                        IconButton(onClick = { editing = if (editing == service.id) null else service.id }) {
                            Icon(UiIcons.Key, "Account / API key", tint = if (editing == service.id || service.kind.needsKey && service.credentials == null) SnapSeekColors.PrimaryHover else SnapSeekColors.TextMuted)
                        }
                    }
                    if (service.type == ServiceType.CUSTOM) {
                        IconButton(onClick = { graph.services.removeCustom(service.id) }) { Icon(UiIcons.Trash, "Remove", tint = SnapSeekColors.Danger) }
                    }
                }
                if (editing == service.id) {
                    CredentialsForm(service, onSave = { login, key -> graph.services.setCredentials(service.id, login, key); editing = null })
                }
            }
        }
    }
}

private data class KeyHint(val loginLabel: String?, val keyLabel: String, val hint: String)

private fun keyHint(kind: ServiceKind): KeyHint = when (kind) {
    ServiceKind.GELBOORU_V2 -> KeyHint("User ID", "API key", "gelbooru.com: Account → Options → API Access Credentials.")
    ServiceKind.DANBOORU -> KeyHint("Login", "API key", "Profile → API keys. Lifts the two-tag limit and shows more posts.")
    ServiceKind.E621 -> KeyHint("Login", "API key", "Account → Manage API Access.")
    ServiceKind.PHILOMENA -> KeyHint(null, "API key", "Account settings → API key. Applies your own content filter.")
    ServiceKind.SZURUBOORU -> KeyHint("Username", "Token", "Account → Login tokens.")
    ServiceKind.WALLHAVEN -> KeyHint(null, "API key", "wallhaven.cc → Settings → Account → API key. Needed for NSFW purity only.")
    ServiceKind.GIPHY -> KeyHint(null, "API key", "developers.giphy.com → Create an App (free) → API key.")
    ServiceKind.TENOR -> KeyHint(null, "API key", "Google Cloud console → enable the Tenor API → Credentials → API key (free).")
    ServiceKind.UNSPLASH -> KeyHint(null, "Access Key", "unsplash.com/developers → your app → Access Key (free, 50 requests an hour).")
    ServiceKind.PEXELS -> KeyHint(null, "API key", "pexels.com/api → Your API key (free).")
    ServiceKind.PIXABAY -> KeyHint(null, "API key", "pixabay.com/api/docs shows your key while logged in (free).")
    else -> KeyHint("Login", "API key", "")
}

@Composable
private fun CredentialsForm(service: Service, onSave: (String?, String?) -> Unit) {
    var login by remember(service.id) { mutableStateOf(service.login ?: "") }
    var key by remember(service.id) { mutableStateOf(service.apiKey ?: "") }
    val hint = keyHint(service.kind)
    Column(Modifier.padding(start = 66.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(hint.hint, style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hint.loginLabel != null) {
                OutlinedTextField(value = login, onValueChange = { login = it }, label = { Text(hint.loginLabel) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(hint.keyLabel) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1.4f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(login, key) }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White)) { Text("Save") }
            if (service.credentials != null) TextButton(onClick = { onSave(null, null) }) { Text("Forget", color = SnapSeekColors.Danger) }
        }
    }
}

@Composable
private fun AddPreset(graph: AppGraph, services: List<Service>, onAdded: () -> Unit) {
    var showNsfw by remember { mutableStateOf(false) }
    val presets = BooruPresets.all.filter { showNsfw || !it.nsfw }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Known sites, browsed natively with search, previews and one-click saving.", style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted, modifier = Modifier.weight(1f))
            Text("Show adult sites", style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted)
            Spacer(Modifier.width(6.dp))
            Switch(checked = showNsfw, onCheckedChange = { showNsfw = it }, colors = SwitchDefaults.colors(checkedTrackColor = SnapSeekColors.Primary))
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(presets, key = { it.url }) { preset ->
                val existing = services.firstOrNull { it.url.trimEnd('/').equals(preset.url.trimEnd('/'), ignoreCase = true) || (it.kind == preset.kind && it.host.equals(preset.host.removePrefix("www."), ignoreCase = true)) }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(preset.name, color = SnapSeekColors.TextMain, style = MaterialTheme.typography.bodyLarge)
                            Pill(preset.kind.label, SnapSeekColors.PrimaryHover)
                            if (preset.nsfw) Pill("NSFW", SnapSeekColors.Danger)
                            if (preset.needsKey) Pill("FREE KEY", SnapSeekColors.Warning)
                        }
                        Text(listOfNotNull(preset.host, preset.note).joinToString(" · "), color = SnapSeekColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (existing != null) {
                        Text("Added", style = MaterialTheme.typography.labelMedium, color = SnapSeekColors.Success)
                    } else {
                        OutlinedButton(
                            onClick = { graph.services.addPreset(preset); onAdded() },
                            border = BorderStroke(1.dp, SnapSeekColors.Border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SnapSeekColors.TextMain),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(UiIcons.Plus, null, modifier = Modifier.width(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddByUrl(graph: AppGraph, onAdded: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ServiceKind.WEB) }
    var nsfw by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var detectNote by remember { mutableStateOf<String?>(null) }
    var kindMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val valid = name.isNotBlank() && url.isNotBlank() && url.contains('.') && (kind != ServiceKind.WEB_GRID || url.contains("{q}") || !url.contains("{"))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, placeholder = { Text("E.g. ArtStation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; detectNote = null },
                label = { Text(if (kind == ServiceKind.WEB_GRID) "URL template with {q} and optional {page}" else "URL") },
                placeholder = { Text(if (kind == ServiceKind.WEB_GRID) "https://example.com/search/{q}?page={page}" else "https://…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    detecting = true
                    scope.launch {
                        val found = runCatching { graph.detector.detect(url) }.getOrNull()
                        detecting = false
                        if (found != null) {
                            kind = found
                            detectNote = "Looks like ${found.label}. ${found.description}"
                            if (name.isBlank()) name = BooruPresets.byHost(url.removePrefix("https://").removePrefix("http://").trimEnd('/').substringBefore('/'))?.name
                                ?: url.removePrefix("https://").removePrefix("http://").substringBefore('/').removePrefix("www.")
                        } else {
                            kind = ServiceKind.WEB
                            detectNote = "No known API answered. It will open as a website; pick \"Image grid from a page\" to scrape it instead."
                        }
                    }
                },
                enabled = url.contains('.') && !detecting,
                border = BorderStroke(1.dp, SnapSeekColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SnapSeekColors.TextMain),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (detecting) CircularProgressIndicator(Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp) else Text("Detect")
            }
        }
        detectNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.PrimaryHover) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How to browse it", style = MaterialTheme.typography.labelMedium, color = SnapSeekColors.TextMuted)
            Box {
                OutlinedButton(
                    onClick = { kindMenu = true },
                    border = BorderStroke(1.dp, SnapSeekColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SnapSeekColors.TextMain),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(kind.label, modifier = Modifier.weight(1f))
                    if (kind.needsKey) Pill("FREE KEY", SnapSeekColors.Warning)
                    Spacer(Modifier.width(8.dp))
                    Icon(UiIcons.ChevronDown, null, modifier = Modifier.width(16.dp))
                }
                DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }, modifier = Modifier.widthIn(max = 520.dp)) {
                    ServiceKind.entries.forEach { k ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(k.label, color = if (k == kind) SnapSeekColors.PrimaryHover else SnapSeekColors.TextMain)
                                    Text(k.description, style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted)
                                }
                            },
                            onClick = { kind = k; kindMenu = false },
                        )
                    }
                }
            }
            Text(kind.description, style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted)
        }
        if (kind == ServiceKind.WEB) {
            OutlinedTextField(value = icon, onValueChange = { icon = it }, label = { Text("Icon URL (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = nsfw, onCheckedChange = { nsfw = it }, colors = SwitchDefaults.colors(checkedTrackColor = SnapSeekColors.Primary))
                Spacer(Modifier.width(10.dp))
                Text("Mark as adult content", color = SnapSeekColors.TextMain, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = { graph.services.addCustom(name, url, icon.ifBlank { null }, kind, nsfw); onAdded() },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
        ) { Text("Add") }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
