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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.snapseek.android.AndroidGraph
import dev.snapseek.core.model.Service

/** The list you land on: every site the app knows, newest-looking first, one tap to start browsing. */
@Composable
fun HomeScreen(graph: AndroidGraph, onOpen: (Service) -> Unit) {
    val settings by graph.settings.settings.collectAsState()
    val services = settings.services.filter { it.enabled }

    Column(Modifier.fillMaxSize().background(Snap.Background)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)) {
            Text("SnapSeek", style = MaterialTheme.typography.headlineMedium, color = Snap.TextMain, fontWeight = FontWeight.Bold)
            Text(
                "Browse a site and save what you like, in the format you want.",
                style = MaterialTheme.typography.bodyMedium,
                color = Snap.TextMuted,
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
