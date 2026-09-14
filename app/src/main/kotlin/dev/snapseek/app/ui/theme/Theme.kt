package dev.snapseek.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** SnapSeek's identity from 1.x, translated to Material 3 tokens: AMOLED black, purple primary, quiet borders. */
object SnapSeekColors {
    val Black = Color(0xFF000000)
    val Panel = Color(0xFF0E0E10)
    val Card = Color(0xFF121214)
    val CardRaised = Color(0xFF18181B)
    val Border = Color(0x14FFFFFF)
    val BorderGlow = Color(0x66A855F7)
    val Primary = Color(0xFF9333EA)
    val PrimaryHover = Color(0xFFA855F7)
    val PrimaryContainer = Color(0xFF3B1466)
    val Danger = Color(0xFFEF4444)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFF59E0B)
    val TextMain = Color(0xFFF8FAFC)
    val TextMuted = Color(0xFFA698BA)
    val WindowCloseHover = Color(0xFFE81123)
}

private val scheme = darkColorScheme(
    primary = SnapSeekColors.Primary,
    onPrimary = Color.White,
    primaryContainer = SnapSeekColors.PrimaryContainer,
    onPrimaryContainer = Color(0xFFEDE0FF),
    secondary = SnapSeekColors.PrimaryHover,
    onSecondary = Color.White,
    background = SnapSeekColors.Black,
    onBackground = SnapSeekColors.TextMain,
    surface = SnapSeekColors.Panel,
    onSurface = SnapSeekColors.TextMain,
    surfaceVariant = SnapSeekColors.Card,
    onSurfaceVariant = SnapSeekColors.TextMuted,
    surfaceContainer = SnapSeekColors.Card,
    surfaceContainerHigh = SnapSeekColors.CardRaised,
    surfaceContainerHighest = Color(0xFF1F1F23),
    outline = Color(0x33FFFFFF),
    outlineVariant = SnapSeekColors.Border,
    error = SnapSeekColors.Danger,
    onError = Color.White,
)

private val typography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

@Composable
fun SnapSeekTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
