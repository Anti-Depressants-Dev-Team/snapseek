package dev.snapseek.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.core.model.Service
import dev.snapseek.core.model.ServiceKind
import dev.snapseek.core.model.ServiceType

@Composable
fun ManageServicesDialog(
    services: List<Service>,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (name: String, url: String, iconUrl: String?, kind: ServiceKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SnapSeekColors.Panel,
            border = BorderStroke(1.dp, SnapSeekColors.Border),
            modifier = Modifier.width(460.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (adding) "Add custom service" else "Manage services",
                        style = MaterialTheme.typography.titleLarge,
                        color = SnapSeekColors.TextMain,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) { Icon(UiIcons.Close, "Close", tint = SnapSeekColors.TextMuted) }
                }
                Spacer(Modifier.height(12.dp))

                if (adding) {
                    AddServiceForm(
                        onCancel = { adding = false },
                        onSubmit = { name, url, icon, kind ->
                            onAdd(name, url, icon, kind)
                            adding = false
                        },
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(services, key = { it.id }) { service ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = service.enabled,
                                    onCheckedChange = { onToggle(service.id, it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = SnapSeekColors.Primary),
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(service.name, color = SnapSeekColors.TextMain, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (service.isBooru) "${service.host} · ${service.kind.label}" else service.host,
                                        color = SnapSeekColors.TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (service.type == ServiceType.CUSTOM) {
                                    TextButton(onClick = { onRemove(service.id) }) { Text("Remove", color = SnapSeekColors.Danger) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = SnapSeekColors.Border)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { adding = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
                    ) { Text("Add custom service") }
                }
            }
        }
    }
}

@Composable
private fun AddServiceForm(onCancel: () -> Unit, onSubmit: (String, String, String?, ServiceKind) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ServiceKind.WEB) }
    val valid = name.isNotBlank() && url.isNotBlank() && url.contains('.')

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, placeholder = { Text("E.g. Gelbooru") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, placeholder = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("How to browse it", style = MaterialTheme.typography.labelMedium, color = SnapSeekColors.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = { Text(k.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SnapSeekColors.PrimaryContainer, selectedLabelColor = Color.White, labelColor = SnapSeekColors.TextMuted),
                    )
                }
            }
            Text(
                if (kind == ServiceKind.GELBOORU_V2) "Tag search with previews, using the site's API. Works with gelbooru.com, rule34.xxx, tbib.org, xbooru.com and other Gelbooru 0.2 sites."
                else "Shows the website in the built-in browser. Right-click or Alt+click images to save them.",
                style = MaterialTheme.typography.bodySmall,
                color = SnapSeekColors.TextMuted,
            )
        }
        if (kind == ServiceKind.WEB) {
            OutlinedTextField(value = icon, onValueChange = { icon = it }, label = { Text("Icon URL (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel", color = SnapSeekColors.TextMuted) }
            Button(
                onClick = { onSubmit(name, url, icon.ifBlank { null }, kind) },
                enabled = valid,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
            ) { Text("Add") }
        }
    }
}
