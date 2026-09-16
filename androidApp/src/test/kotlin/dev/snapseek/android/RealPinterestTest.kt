package dev.snapseek.android

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.snapseek.android.ui.BrowseScreen
import dev.snapseek.android.ui.SnapSeekTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The screens above run on made-up data. This one runs the real Pinterest client against the real site and lets
 * the answer reach the real grid, because that is where the phone died and the made-up data never did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealPinterestTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the pinterest screen survives a real feed`() {
        val graph = AndroidGraph.of(ApplicationProvider.getApplicationContext())
        val pinterest = graph.settings.current.services.first { it.id == "pinterest" }
        val model = BrowseModel(pinterest, graph.client(pinterest), graph)

        compose.setContent {
            SnapSeekTheme { BrowseScreen(model = model, graph = graph, onBack = {}, onConnectAccount = {}) }
        }

        model.search("cats")
        settle(model)

        val posts = model.state.value.posts
        println("PINTEREST posts=${posts.size} error=${model.state.value.error}")
        check(posts.map { it.id }.distinct().size == posts.size) { "the same post arrived twice" }
        if (posts.isEmpty() && model.state.value.error != null) {
            println("PINTEREST could not be reached from this machine: ${model.state.value.error}")
        }

        // Scrolling on is where a second page arrives, and where a repeat between pages would show up.
        model.loadMore()
        settle(model)
        val after = model.state.value.posts
        println("PINTEREST after another page=${after.size}")
        check(after.map { it.id }.distinct().size == after.size) { "the same post arrived twice across pages" }
    }

    /** Waits for the model to stop loading, then lets Compose draw whatever arrived. */
    private fun settle(model: BrowseModel, timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            val state = model.state.value
            if (!state.loading && (state.posts.isNotEmpty() || state.error != null || state.endReached)) break
            Thread.sleep(200)
        }
        compose.waitForIdle()
    }
}
