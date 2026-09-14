package dev.snapseek.app.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import dev.snapseek.app.ui.common.UiIcons
import dev.snapseek.app.ui.theme.SnapSeekColors
import dev.snapseek.browser.EngineState
import dev.snapseek.browser.TabState

val TitleBarHeight = 40.dp

/** Custom window chrome: drag anywhere, page navigation when a site is open, window controls on the right. */
@Composable
fun WindowScope.TitleBar(
    tabState: TabState?,
    /** Shown with a Home button when no browser tab is open but the user is inside a section (booru, settings…). */
    sectionTitle: String?,
    engineState: EngineState,
    isMaximized: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onDevTools: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    WindowDraggableArea(Modifier.fillMaxWidth().height(TitleBarHeight)) {
        Row(
            Modifier.fillMaxSize().background(SnapSeekColors.Black),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(14.dp).clip(RoundedCornerShape(4.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF38BDF8)))),
                )
                Text(
                    "SnapSeek",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        brush = Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF38BDF8))),
                    ),
                )
            }

            if (tabState != null) {
                Spacer(Modifier.width(18.dp))
                NavButton(UiIcons.ArrowLeft, "Back", enabled = tabState.canGoBack, onClick = onBack)
                NavButton(UiIcons.ArrowRight, "Forward", enabled = tabState.canGoForward, onClick = onForward)
                NavButton(UiIcons.Refresh, "Reload", enabled = true, onClick = onReload)
                NavButton(UiIcons.Home, "Home", enabled = true, onClick = onHome)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = tabState.title.ifBlank { tabState.url },
                    style = MaterialTheme.typography.labelMedium,
                    color = SnapSeekColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                NavButton(UiIcons.Tool, "Developer tools", enabled = true, onClick = onDevTools)
            } else if (sectionTitle != null) {
                Spacer(Modifier.width(18.dp))
                NavButton(UiIcons.Home, "Home", enabled = true, onClick = onHome)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = SnapSeekColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
                if (engineState is EngineState.Installing || engineState is EngineState.Starting) {
                    Text(
                        "Preparing browser runtime…",
                        style = MaterialTheme.typography.labelSmall,
                        color = SnapSeekColors.TextMuted,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }

            WindowControl(hover = Color(0x1AFFFFFF), onClick = onMinimize, description = "Minimize") {
                Canvas(Modifier.size(10.dp)) {
                    drawLine(Color.White, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.2f)
                }
            }
            WindowControl(hover = Color(0x1AFFFFFF), onClick = onToggleMaximize, description = if (isMaximized) "Restore" else "Maximize") {
                Canvas(Modifier.size(10.dp)) {
                    if (isMaximized) {
                        val inset = 2.5f
                        drawRect(Color.White, Offset(0f, inset), Size(size.width - inset, size.height - inset), style = Stroke(1.2f))
                        drawRect(Color.White, Offset(inset, 0f), Size(size.width - inset, size.height - inset), style = Stroke(1.2f))
                    } else {
                        drawRect(Color.White, style = Stroke(1.2f))
                    }
                }
            }
            WindowControl(hover = SnapSeekColors.WindowCloseHover, onClick = onClose, description = "Close") {
                Icon(UiIcons.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun NavButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = SnapSeekColors.TextMain,
            disabledContentColor = Color(0x40FFFFFF),
        ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun WindowControl(hover: Color, onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .width(46.dp)
            .fillMaxHeight()
            .hoverable(interaction)
            .background(if (hovered) hover else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
