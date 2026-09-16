package dev.snapseek.android

import android.content.Context
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.model.OutputFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saving is the whole point of the app and it is the one thing no desktop or JVM run can prove: it ends in the
 * phone's own gallery. This runs on the device, saves a real picture from a real site, and then asks the gallery
 * whether it is there.
 */
@RunWith(AndroidJUnit4::class)
class GallerySaverTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun savesAPictureWhereTheGalleryCanSeeIt() = runBlocking {
        val graph = AndroidGraph.of(context)
        val service = graph.settings.current.services.first { it.id == "safebooru" }
        val client = graph.client(service)

        val post = client.posts("", 0, 5).first()
        val saved = graph.saver.save(
            post = post,
            service = service,
            format = OutputFormat.PNG,
            useOriginal = false,
            userAgent = dev.snapseek.core.booru.BooruHttp.APP_USER_AGENT,
        )

        assertTrue("saved under an odd name: ${saved.name}", saved.name.endsWith(".png"))
        assertTrue("saved somewhere unexpected: ${saved.location}", saved.location.contains("SnapSeek"))

        val found = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(saved.name),
            null,
        )?.use { it.count } ?: 0
        assertTrue("the gallery cannot see ${saved.name}", found > 0)
    }
}
