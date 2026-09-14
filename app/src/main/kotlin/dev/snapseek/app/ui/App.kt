package dev.snapseek.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.FrameWindowScope
import dev.snapseek.app.AppGraph
import dev.snapseek.app.ui.booru.BooruScreen
import dev.snapseek.app.ui.browser.BrowserScreen
import dev.snapseek.app.ui.history.HistoryScreen
import dev.snapseek.app.ui.home.HomeScreen
import dev.snapseek.app.ui.settings.SettingsScreen
import dev.snapseek.app.ui.shell.TitleBar
import dev.snapseek.app.vm.RootViewModel
import dev.snapseek.app.vm.Screen
import dev.snapseek.browser.TabState

@Composable
fun FrameWindowScope.App(
    graph: AppGraph,
    root: RootViewModel,
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    val screen by root.screen.collectAsState()
    val tab by root.tab.collectAsState()
    val booru by root.booru.collectAsState()
    val tabState: TabState? = tab?.let { t ->
        val s by t.state.collectAsState()
        s
    }
    val engineState by graph.engine.state.collectAsState()
    val jobs by graph.downloads.jobs.collectAsState()
    val notice by root.notice.collectAsState()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TitleBar(
            tabState = tabState,
            sectionTitle = when (screen) {
                is Screen.Booru -> booru?.service?.name
                Screen.Settings -> "Settings"
                Screen.History -> "History"
                else -> null
            },
            engineState = engineState,
            isMaximized = isMaximized,
            onBack = { tab?.back() },
            onForward = { tab?.forward() },
            onReload = { tab?.reload() },
            onHome = root::goHome,
            onDevTools = { tab?.openDevTools() },
            onMinimize = onMinimize,
            onToggleMaximize = onToggleMaximize,
            onClose = onClose,
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val s = screen) {
                is Screen.Browser -> {
                    val t = tab
                    if (t != null) {
                        key(t) {
                            BrowserScreen(
                                tab = t,
                                tabState = tabState ?: TabState(),
                                jobs = jobs,
                                notice = notice,
                                onDismissNotice = root::dismissNotice,
                                onDismissJob = graph.downloads::dismiss,
                                onClearFinished = graph.downloads::clearFinished,
                            )
                        }
                    } else {
                        LaunchedEffect(s) { root.goHome() }
                    }
                }
                is Screen.Booru -> {
                    val vm = booru
                    if (vm != null) {
                        key(vm) { BooruScreen(vm = vm, graph = graph, onOpenWeb = { url -> root.openUrl(url, vm.service.id) }) }
                    } else {
                        LaunchedEffect(s) { root.goHome() }
                    }
                }
                Screen.Home -> HomeScreen(
                    graph = graph,
                    engineState = engineState,
                    onBrowse = root::openService,
                    onOpenWebsite = root::openWebsite,
                    onOpenHistory = root::openHistory,
                    onOpenSettings = root::openSettings,
                    onRetryEngine = root::startEngine,
                )
                Screen.Settings -> SettingsScreen(graph = graph, parentWindow = window, onBack = root::goHome)
                Screen.History -> HistoryScreen(graph = graph, onBack = root::goHome)
            }
        }
    }
}
