package dev.snapseek.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The desktop app's black and purple, carried over so the two look like the same product. */
object Snap {
    val Purple = Color(0xFF9333EA)
    val PurpleBright = Color(0xFFA855F7)
    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF141417)
    val Card = Color(0xFF1B1B20)
    val Border = Color(0xFF2A2A31)
    val TextMain = Color(0xFFF4F4F5)
    val TextMuted = Color(0xFF9A9AA5)
    val Danger = Color(0xFFF87171)
}

private val dark = darkColorScheme(
    primary = Snap.Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1D63),
    onPrimaryContainer = Color(0xFFEDE0FF),
    background = Snap.Background,
    onBackground = Snap.TextMain,
    surface = Snap.Surface,
    onSurface = Snap.TextMain,
    surfaceVariant = Snap.Card,
    onSurfaceVariant = Snap.TextMuted,
    outline = Snap.Border,
    error = Snap.Danger,
)

@Composable
fun SnapSeekTheme(content: @Composable () -> Unit) {
    // The app is a dark one by design, on a phone as on the desktop, so it does not follow the system theme.
    MaterialTheme(colorScheme = dark, content = content)
}
