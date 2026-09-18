package dev.snapseek.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.snapseek.android.platform.AndroidUpdater
import dev.snapseek.core.update.Update
import dev.snapseek.core.update.UpdateChecker
import dev.snapseek.core.update.UpdateDownloader
import dev.snapseek.core.update.UpdateTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The updater on the phone itself. Installing is deliberately left out: that hands the app to Android to be
 * replaced, which would end the test run. Everything up to that point is real, including the download.
 */
@RunWith(AndroidJUnit4::class)
class UpdaterTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val release = """
        {"tag_name":"v99.0.0","draft":false,"prerelease":false,"html_url":"https://example.test/r",
         "body":"A much later version.","assets":[
           {"name":"SnapSeek-99.0.0-android.apk","size":10,"browser_download_url":"https://example.test/a.apk"}]}
    """.trimIndent()

    @Test
    fun aNewerReleaseIsOfferedOnTheHomeScreen() { runBlocking {
        val graph = AndroidGraph.of(context)
        graph.settings.update { it.copy(skippedVersion = "", lastUpdateCheck = 0) }

        val updater = AndroidUpdater(
            context = context,
            settings = graph.settings,
            scope = graph.scope,
            currentVersion = "2.0.0",
            checker = UpdateChecker(fetch = { release }),
        )
        updater.check()
        val offered = waitFor { updater.available.value }
        assertEquals("99.0.0", offered?.version)

        // Skipping it silences that version, and only that version.
        updater.skip(offered!!)
        assertNull(updater.available.value)
        assertEquals("99.0.0", graph.settings.current.skippedVersion)
        updater.check()
        Thread.sleep(1500)
        assertNull("a skipped version came back", updater.available.value)

        graph.settings.update { it.copy(skippedVersion = "") }
    } }

    @Test
    fun theAppKnowsItsOwnVersion() {
        val graph = AndroidGraph.of(context)
        // The check compares against what is installed, so a wrong answer here means it never offers anything.
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        assertTrue("no version on the installed package", !installed.isNullOrBlank())
        assertTrue("the graph was built", graph.settings.current.services.isNotEmpty())
    }

    @Test
    fun aDownloadLandsWhereTheInstallerCanReadIt() { runBlocking {
        // A real file off the internet, small, through the real downloader.
        val update = Update(
            version = "99.0.0",
            notes = "",
            pageUrl = "https://example.test/r",
            downloadUrl = "https://raw.githubusercontent.com/Anti-Depressants-Dev-Team/snapseek/main/README.md",
            fileName = "update-test.txt",
            sizeBytes = 0,
            checksumsUrl = null,
        )
        val into = File(context.cacheDir, "updates")
        val result = UpdateDownloader().download(update, into)

        assertTrue("nothing was written", result.file.isFile && result.file.length() > 0)
        assertEquals(into.absolutePath, result.file.parentFile?.absolutePath)
        result.file.delete()
    } }

    @Test
    fun thePlatformIsAndroid() {
        assertEquals("apk", UpdateTarget.ANDROID.extension)
    }

    private fun <T> waitFor(timeoutMs: Long = 10_000, value: () -> T?): T? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            value()?.let { return it }
            Thread.sleep(100)
        }
        return value()
    }
}
