package dev.snapseek.android

import android.app.Activity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * There is no phone and no emulator on the machine this is built on, so this is where the app is actually run:
 * Robolectric executes the real framework on the JVM. It won't catch everything a device would, but "the app
 * opens at all" is exactly what it does catch, and that is the failure that shipped in the first build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `the home screen draws with its services`() {
        compose.onNodeWithText("SnapSeek").assertIsDisplayed()
        compose.onAllNodesWithTextSafely("Pinterest").onFirst().assertIsDisplayed()
    }

    @Test
    fun `opening a site draws the grid instead of crashing`() {
        compose.onAllNodesWithTextSafely("Safebooru").onFirst().performClick()
        compose.waitForIdle()
        // The grid has no posts in a test with no network, but its own furniture has to be there.
        compose.onNodeWithText("Back").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextSafely(text: String) =
        onAllNodes(hasText(text, substring = true))
}

/** The graph on its own: built once, with every built-in service able to produce a client. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraphTest {

    @Test
    fun `every built-in service has a client`() {
        val graph = AndroidGraph.of(ApplicationProvider.getApplicationContext())
        val services = graph.settings.current.services.filter { it.enabled }
        assertTrue("no services to show on the home screen", services.isNotEmpty())
        services.forEach { assertNotNull("no client for ${it.id}", graph.client(it)) }
    }

    @Test
    fun `the activity survives being created and destroyed`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity: Activity -> assertNotNull(activity.window) }
        }
    }
}
