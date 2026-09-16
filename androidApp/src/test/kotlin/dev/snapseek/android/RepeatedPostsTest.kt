package dev.snapseek.android

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import dev.snapseek.android.ui.BrowseScreen
import dev.snapseek.android.ui.SnapSeekTheme
import dev.snapseek.core.booru.BooruClient
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.booru.TagSuggestion
import dev.snapseek.core.model.Service
import dev.snapseek.core.model.ServiceKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pinterest hands the same pin back more than once in a single page of its feed, which is ordinary for it. The
 * grid keys its items by post id, and a list with the same key twice is a hard crash, so a repeat has to be
 * dropped before it reaches the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepeatedPostsTest {

    @get:Rule
    val compose = createComposeRule()

    private val service = Service(
        id = "repeats",
        name = "Repeats",
        url = "https://example.test/",
        kind = ServiceKind.GELBOORU_V2,
    )

    @Test
    fun `a page that repeats a post still draws`() {
        val graph = AndroidGraph.of(ApplicationProvider.getApplicationContext())
        val model = BrowseModel(service, RepeatingClient, graph)

        compose.setContent {
            SnapSeekTheme {
                BrowseScreen(model = model, graph = graph, onBack = {}, onConnectAccount = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Back").assertIsDisplayed()
        val ids = model.state.value.posts.map { it.id }
        assert(ids.size == ids.distinct().size) { "the same post reached the grid twice: $ids" }
    }

    /** Answers every page with a post it has already given out, the way a busy feed does. */
    private object RepeatingClient : BooruClient {
        override val baseUrl = "https://example.test/"
        override val kind = ServiceKind.GELBOORU_V2
        override val maxPageSize = 20
        override val safeModeTags = emptyList<String>()

        override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> =
            if (page > 0) emptyList() else listOf(post(1), post(2), post(1))

        override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> = emptyList()

        override fun postPageUrl(post: BooruPost) = baseUrl

        private fun post(id: Long) = BooruPost(
            id = id,
            md5 = "",
            previewUrl = "https://example.test/$id.png",
            sampleUrl = "https://example.test/$id.png",
            fileUrl = "https://example.test/$id.png",
            width = 100,
            height = 100,
            tags = emptyList(),
            rating = "general",
            score = null,
            source = null,
            owner = null,
            hasSample = false,
        )
    }
}
