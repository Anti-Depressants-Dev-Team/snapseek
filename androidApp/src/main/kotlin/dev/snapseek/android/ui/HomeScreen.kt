package dev.snapseek.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dev.snapseek.android.AndroidGraph
import dev.snapseek.android.platform.CrashLog
import dev.snapseek.core.model.Service
import dev.snapseek.core.update.DownloadState
import dev.snapseek.core.update.Update

/** The list you land on: every site the app knows, newest-looking first, one tap to start browsing. */
@Composable
fun HomeScreen(graph: AndroidGraph, onOpen: (Service) -> Unit) {
    val settings by graph.settings.settings.collectAsState()
    val services = settings.services.filter { it.enabled }
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.last(context)) }
    val update by graph.updater.available.collectAsState()
    val downloadState by graph.updater.download.collectAsState()

    Column(Modifier.fillMaxSize().background(Snap.Background)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)) {
            Text("SnapSeek", style = MaterialTheme.typography.headlineMedium, color = Snap.TextMain, fontWeight = FontWeight.Bold)
            Text(
                "Browse a site and save what you like, in the format you want.",
                style = MaterialTheme.typography.bodyMedium,
                color = Snap.TextMuted,
            )
        }

        update?.let { newer ->
            UpdateCard(
                update = newer,
                download = downloadState,
                onInstall = { graph.updater.install(newer) },
                onSkip = { graph.updater.skip(newer) },
                onDismiss = graph.updater::dismiss,
            )
        }

        crash?.let { report ->
            CrashCard(
                report = report,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SnapSeek crash", report))
                },
                onDismiss = { CrashLog.clear(context); crash = null },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(services, key = { it.id }) { service -> ServiceCard(service) { onOpen(service) } }
        }
    }
}

@Composable
private fun ServiceCard(service: Service, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Snap.Card)
            .border(1.dp, Snap.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(brandColor(service.id)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                service.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(service.name, style = MaterialTheme.typography.titleMedium, color = Snap.TextMain)
            Text(
                service.host ?: service.url,
                style = MaterialTheme.typography.bodySmall,
                color = Snap.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (service.nsfw) {
            Text(
                "18+",
                style = MaterialTheme.typography.labelSmall,
                color = Snap.Danger,
                modifier = Modifier.background(Color(0x33F87171), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private fun brandColor(id: String): Color = when (id) {
    "pinterest" -> Color(0xFFE60023)
    "pixiv" -> Color(0xFF0096FA)
    "deviantart" -> Color(0xFF05CC47)
    "giphy" -> Color(0xFF6157FF)
    "tenor" -> Color(0xFF1E90FF)
    "safebooru", "danbooru_safe" -> Color(0xFF6D28D9)
    else -> Snap.Purple
}

/**
 * What the app shows after it has died: the first lines of the crash, and a button that puts the whole report on
 * the clipboard so it can be pasted to whoever is fixing it.
 */
@Composable
private fun CrashCard(report: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    val headline = report.lineSequence().firstOrNull { it.startsWith("java.") || it.contains("Exception") || it.contains("Error") }
        ?: report.lineSequence().drop(5).firstOrNull()
        ?: "unknown"
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x33F87171))
            .border(1.dp, Snap.Danger, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("The app crashed last time", style = MaterialTheme.typography.titleSmall, color = Snap.Danger, fontWeight = FontWeight.Bold)
        Text(headline.trim(), style = MaterialTheme.typography.bodySmall, color = Snap.TextMain, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCopy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Snap.Danger, contentColor = Color.Black),
            ) { Text("Copy the report") }
            TextButton(onClick = onDismiss) { Text("Dismiss", color = Snap.TextMuted) }
        }
    }
}

/**
 * A new release, offered rather than imposed: the phone build is sideloaded, so updating means downloading an
 * APK and letting Android's own installer replace the app. Progress shows while it downloads.
 */
@Composable
private fun UpdateCard(
    update: Update,
    download: DownloadState,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, Snap.PurpleBright, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("SnapSeek ${update.version} is out", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            when (download) {
                is DownloadState.Failed -> download.reason
                is DownloadState.Done -> "Android is taking over from here."
                is DownloadState.Running -> "Downloading…"
                DownloadState.Idle -> update.notes.lineSequence().firstOrNull { it.isNotBlank() }?.take(100)
                    ?: "Downloads the new app and hands it to Android to install."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (download is DownloadState.Failed) Snap.Danger else Color(0xFFD9C7F5),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (download is DownloadState.Running) {
            LinearProgressIndicator(
                progress = { download.fraction },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color(0x33FFFFFF),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Snap.Purple, contentColor = Color.White),
                ) { Text(if (download is DownloadState.Failed) "Try again" else "Update") }
                TextButton(onClick = onSkip) { Text("Skip this one", color = Color(0xFFD9C7F5)) }
                TextButton(onClick = onDismiss) { Text("Later", color = Snap.TextMuted) }
            }
        }
    }
}
