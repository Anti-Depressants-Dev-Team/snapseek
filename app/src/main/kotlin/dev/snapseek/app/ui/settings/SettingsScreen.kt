package dev.snapseek.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.snapseek.app.AppGraph
import dev.snapseek.app.ui.common.FolderChooser
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.core.download.FilenameTemplate
import dev.snapseek.core.model.DownloadQuality
import dev.snapseek.core.model.GridSize
import dev.snapseek.core.model.OutputFormat
import dev.snapseek.core.model.Settings
import dev.snapseek.core.model.SidecarFormat
import java.awt.Window
import java.nio.file.Path

@Composable
fun SettingsScreen(graph: AppGraph, parentWindow: Window?, onBack: () -> Unit) {
    val settings by graph.settings.settings.collectAsState()
    fun edit(transform: (Settings) -> Settings) = graph.settings.update(transform)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(UiIcons.ArrowLeft, "Back", tint = SnapSeekColors.TextMain) }
            Spacer(Modifier.width(8.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = SnapSeekColors.TextMain)
        }
        Spacer(Modifier.height(24.dp))

        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionLabel("Saving")
            SettingCard {
                Text("Download folder", style = MaterialTheme.typography.titleMedium, color = SnapSeekColors.TextMain)
                Spacer(Modifier.height(6.dp))
                Text(settings.downloadDir, style = MaterialTheme.typography.bodyMedium, color = SnapSeekColors.PrimaryHover)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        FolderChooser.pick(parentWindow, Path.of(settings.downloadDir))?.let { chosen -> edit { it.copy(downloadDir = chosen.toString()) } }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SnapSeekColors.Primary, contentColor = Color.White),
                ) { Text("Choose folder") }
            }
            SettingCard {
                Text("Default save format", style = MaterialTheme.typography.titleMedium, color = SnapSeekColors.TextMain)
                Description("Used by Alt+click and the Save button. The right-click menu always offers every format. \"Original\" writes the file exactly as the site served it.")
                Spacer(Modifier.height(10.dp))
                ChipRow(OutputFormat.entries, settings.defaultFormat, { it.label }) { fmt -> edit { it.copy(defaultFormat = fmt) } }
            }
            SettingCard {
                ToggleRow(
                    title = "Skip images you already saved",
                    description = "Compares the downloaded bytes against your history. Turn off to save copies anyway.",
                    checked = settings.skipDuplicates,
                    onChange = { on -> edit { it.copy(skipDuplicates = on) } },
                )
            }
            SettingCard {
                Text("File names", style = MaterialTheme.typography.titleMedium, color = SnapSeekColors.TextMain)
                Description("Templates use {tokens}, optionally with options: {character:nomod,limit=3,delimiter=comma} or {md5:maxlength=8}. The extension is added for you unless the template names {extension}.")
                Spacer(Modifier.height(10.dp))
                TemplateField("From websites", settings.fileNameTemplate) { v -> edit { it.copy(fileNameTemplate = v) } }
                Spacer(Modifier.height(10.dp))
                TemplateField("From boorus", settings.booruFileNameTemplate) { v -> edit { it.copy(booruFileNameTemplate = v) } }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tokens: " + FilenameTemplate.KNOWN_TOKENS.joinToString(" ") { "{$it}" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = SnapSeekColors.TextMuted,
                )
                Text(
                    "Options: maxlength=N · limit=N · delimiter=comma|space|underscore|… · nomod · case=lower|upper|title · format=yyyy-MM-dd (date) · pad_left=N · single_letter",
                    style = MaterialTheme.typography.bodySmall,
                    color = SnapSeekColors.TextMuted,
                )
            }
            SettingCard {
                Text("Sidecar file", style = MaterialTheme.typography.titleMedium, color = SnapSeekColors.TextMain)
                Description("Write a companion file next to each saved image: the tags as text, or every known detail as JSON. Only boorus provide tags.")
                Spacer(Modifier.height(10.dp))
                ChipRow(SidecarFormat.entries, settings.sidecar, { it.label }) { f -> edit { it.copy(sidecar = f) } }
            }

            SectionLabel("Booru browsing")
            SettingCard {
                Text("Download quality", style = MaterialTheme.typography.titleMedium, color = SnapSeekColors.TextMain)
                Description("Original is the uploaded file. Sample is the site's downscaled copy, smaller and faster. You can override this per post.")
                Spacer(Modifier.height(10.dp))
                ChipRow(DownloadQuality.entries, settings.booruQuality, { it.label }) { q -> edit { it.copy(booruQuality = q) } }
            }
            SettingCard {
                Text("Grid size", style = MaterialTheme.typography.titleMedium, color = SnapSeekColors.TextMain)
                Spacer(Modifier.height(10.dp))
                ChipRow(GridSize.entries, settings.gridSize, { it.label }) { g -> edit { it.copy(gridSize = g) } }
            }
            SettingCard {
                Text("Blacklisted tags", style = MaterialTheme.typography.titleMedium, color = SnapSeekColors.TextMain)
                Description("One rule per line. A post is hidden when every tag on a line matches. Prefix a tag with - to require its absence, for example \"gore -parody\".")
                Spacer(Modifier.height(10.dp))
                var text by remember { mutableStateOf(settings.blacklist) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; edit { s -> s.copy(blacklist = it) } },
                    minLines = 4,
                    maxLines = 12,
                    placeholder = { Text("loli\n1boy solo\ngore -parody") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel("Websites")
            SettingCard {
                ToggleRow(
                    title = "Dark mode on websites",
                    description = "Applies Dark Reader to every page. Takes effect on the next page load.",
                    checked = settings.darkMode,
                    onChange = { on -> edit { it.copy(darkMode = on) } },
                )
            }
            SettingCard {
                ToggleRow(
                    title = "Block ads and trackers",
                    description = "Cancels requests to known ad and tracking hosts before they leave the app.",
                    checked = settings.adBlock,
                    onChange = { on -> edit { it.copy(adBlock = on) } },
                )
            }

            SectionLabel("About")
            SettingCard {
                Text(
                    "SnapSeek 2.0.0 (Kotlin rebuild)\nChromium via JCEF · Compose Multiplatform · Dark Reader (MIT)\nBooru browsing inspired by Boorusama\nData: ${graph.paths.dataDir}\nCache: ${graph.paths.cacheDir}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SnapSeekColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = SnapSeekColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Description(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted)
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SnapSeekColors.PrimaryContainer,
                    selectedLabelColor = Color.White,
                    labelColor = SnapSeekColors.TextMuted,
                ),
            )
        }
    }
}

@Composable
private fun TemplateField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SnapSeekColors.PrimaryHover,
    unfocusedBorderColor = SnapSeekColors.Border,
    focusedTextColor = SnapSeekColors.TextMain,
    unfocusedTextColor = SnapSeekColors.TextMain,
)

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SnapSeekColors.Card, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, SnapSeekColors.Border), RoundedCornerShape(14.dp))
            .padding(20.dp),
    ) { content() }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = SnapSeekColors.TextMain)
            Text(description, style = MaterialTheme.typography.bodySmall, color = SnapSeekColors.TextMuted)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = SnapSeekColors.Primary))
    }
}
