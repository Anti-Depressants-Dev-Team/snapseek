package dev.snapseek.app

import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.snapseek.app.ui.App
import dev.snapseek.app.ui.theme.SnapSeekTheme
import dev.snapseek.app.vm.RootViewModel
import dev.snapseek.browser.EngineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import javax.swing.UIManager

/**
 * Flags (all optional):
 *   --open <serviceId>   open a service as soon as the app is ready (handy for smoke tests)
 *   --search <query>     with --open on a native service: run this search right away
 *   --url <url>          open this page in the embedded browser as soon as it is ready
 *   --connect <serviceId> open a service natively and go straight into its "connect account" login flow
 */
fun main(args: Array<String>) {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true")
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    val graph = AppGraph.create()
    val root = RootViewModel(graph)
    root.startEngine()

    args.indexOf("--connect").takeIf { it >= 0 && it + 1 < args.size }?.let { i ->
        val serviceId = args[i + 1]
        graph.scope.launch {
            graph.engine.state.first { it is EngineState.Ready }
            withContext(Dispatchers.Swing) {
                root.openService(serviceId)
                root.connectAccount()
            }
        }
    }

    args.indexOf("--url").takeIf { it >= 0 && it + 1 < args.size }?.let { i ->
        val url = args[i + 1]
        graph.scope.launch {
            graph.engine.state.first { it is EngineState.Ready }
            withContext(Dispatchers.Swing) { root.openUrl(url) }
        }
    }

    args.indexOf("--open").takeIf { it >= 0 && it + 1 < args.size }?.let { i ->
        val serviceId = args[i + 1]
        val query = args.indexOf("--search").takeIf { it >= 0 && it + 1 < args.size }?.let { args[it + 1] }
        graph.scope.launch {
            val service = graph.services.byId(serviceId)
            if (service?.isBooru != true) graph.engine.state.first { it is EngineState.Ready }
            withContext(Dispatchers.Swing) {
                root.openService(serviceId)
                if (query != null) root.booru.value?.search(query)
            }
        }
    }

    application {
        val windowState = rememberWindowState(
            size = DpSize(1200.dp, 800.dp),
            position = WindowPosition(Alignment.Center),
        )
        @Suppress("DEPRECATION")
        val icon = painterResource("snapseek.png")
        val quit = {
            root.shutdown()
            graph.shutdown()
            exitApplication()
        }

        Window(
            onCloseRequest = quit,
            state = windowState,
            title = "SnapSeek",
            icon = icon,
            undecorated = true,
        ) {
            SnapSeekTheme {
                App(
                    graph = graph,
                    root = root,
                    isMaximized = windowState.placement == WindowPlacement.Maximized,
                    onMinimize = { windowState.isMinimized = true },
                    onToggleMaximize = {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Maximized) WindowPlacement.Floating
                            else WindowPlacement.Maximized
                    },
                    onClose = quit,
                )
            }
        }
    }
}
