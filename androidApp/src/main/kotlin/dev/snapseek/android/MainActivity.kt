package dev.snapseek.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.snapseek.android.ui.BrowseScreen
import dev.snapseek.android.ui.HomeScreen
import dev.snapseek.android.ui.LoginScreen
import dev.snapseek.android.ui.Snap
import dev.snapseek.android.ui.SnapSeekTheme
import dev.snapseek.core.model.Service

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = AndroidGraph.of(this)
        setContent {
            SnapSeekTheme {
                Box(Modifier.fillMaxSize().background(Snap.Background).statusBarsPadding().navigationBarsPadding()) {
                    SnapSeekApp(graph)
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Browse(val serviceId: String) : Screen
    data class Login(val serviceId: String) : Screen
}

@Composable
private fun SnapSeekApp(graph: AndroidGraph) {
    var screen: Screen by remember { mutableStateOf(Screen.Home) }
    var model by remember { mutableStateOf<BrowseModel?>(null) }

    fun open(service: Service) {
        model = BrowseModel(service, graph.client(service), graph)
        screen = Screen.Browse(service.id)
    }

    BackHandler(enabled = screen !is Screen.Home) {
        screen = if (screen is Screen.Login) Screen.Browse((screen as Screen.Login).serviceId) else Screen.Home
    }

    when (val current = screen) {
        is Screen.Home -> HomeScreen(graph) { open(it) }

        is Screen.Browse -> {
            val active = model
            if (active == null) {
                screen = Screen.Home
            } else {
                BrowseScreen(
                    model = active,
                    graph = graph,
                    onBack = { screen = Screen.Home },
                    onConnectAccount = { screen = Screen.Login(current.serviceId) },
                )
            }
        }

        is Screen.Login -> {
            val active = model
            if (active == null) {
                screen = Screen.Home
            } else {
                LoginScreen(active) {
                    active.onReturnedFromLogin()
                    screen = Screen.Browse(current.serviceId)
                }
            }
        }
    }
}
