package dev.snapseek.core.update

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun `a version is newer only when its numbers are`() {
        assertTrue(UpdateChecker.isNewer("2.1.0", "2.0.0"))
        assertTrue(UpdateChecker.isNewer("2.0.1", "2.0.0"))
        // Ten is after nine, which a string comparison would get wrong.
        assertTrue(UpdateChecker.isNewer("2.10.0", "2.9.0"))
        assertTrue(UpdateChecker.isNewer("v2.1.0", "2.0.0"))
        assertTrue(UpdateChecker.isNewer("2.1", "2.0.9"))

        assertFalse(UpdateChecker.isNewer("2.0.0", "2.0.0"))
        assertFalse(UpdateChecker.isNewer("1.9.9", "2.0.0"))
        assertFalse(UpdateChecker.isNewer("2.0.0", "2.0.1"))
        // Nobody should be nagged because a tag was typed oddly.
        assertFalse(UpdateChecker.isNewer("nightly", "2.0.0"))
    }

    @Test
    fun `the right file is picked out of a release`() {
        val body = release(
            tag = "v2.1.0",
            assets = listOf(
                "SnapSeek-2.1.0-windows.msi",
                "SnapSeek-2.1.0-windows-portable.zip",
                "SnapSeek-2.1.0-amd64.deb",
                "SnapSeek-2.1.0-x86_64.rpm",
                "SnapSeek-2.1.0-macos-arm64.dmg",
                "SnapSeek-2.1.0-android.apk",
                "SHA256SUMS.txt",
            ),
        )

        assertEquals("SnapSeek-2.1.0-windows.msi", UpdateChecker.parse(body, UpdateTarget.WINDOWS_INSTALLER)?.fileName)
        assertEquals("SnapSeek-2.1.0-amd64.deb", UpdateChecker.parse(body, UpdateTarget.DEBIAN)?.fileName)
        assertEquals("SnapSeek-2.1.0-x86_64.rpm", UpdateChecker.parse(body, UpdateTarget.FEDORA)?.fileName)
        assertEquals("SnapSeek-2.1.0-macos-arm64.dmg", UpdateChecker.parse(body, UpdateTarget.MACOS)?.fileName)
        assertEquals("SnapSeek-2.1.0-android.apk", UpdateChecker.parse(body, UpdateTarget.ANDROID)?.fileName)

        val update = UpdateChecker.parse(body, UpdateTarget.WINDOWS_INSTALLER)!!
        assertEquals("2.1.0", update.version)
        assertTrue(update.checksumsUrl!!.endsWith("SHA256SUMS.txt"))
        assertEquals(1234L, update.sizeBytes)
    }

    @Test
    fun `a signed android build beats the unsigned one`() {
        val body = release("v2.1.0", listOf("SnapSeek-2.1.0-android-unsigned.apk", "SnapSeek-2.1.0-android.apk"))
        assertEquals("SnapSeek-2.1.0-android.apk", UpdateChecker.parse(body, UpdateTarget.ANDROID)?.fileName)
    }

    @Test
    fun `drafts, prereleases and releases without your platform are ignored`() {
        assertNull(UpdateChecker.parse(release("v2.1.0", listOf("SnapSeek.msi"), draft = true), UpdateTarget.WINDOWS_INSTALLER))
        assertNull(UpdateChecker.parse(release("v2.1.0", listOf("SnapSeek.msi"), prerelease = true), UpdateTarget.WINDOWS_INSTALLER))
        // A release that shipped no rpm should not offer one.
        assertNull(UpdateChecker.parse(release("v2.1.0", listOf("SnapSeek.msi")), UpdateTarget.FEDORA))
        assertNull(UpdateChecker.parse("not json at all", UpdateTarget.MACOS))
    }

    @Test
    fun `a checksum is found by file name`() {
        val sums = """
            aaaa1111  SnapSeek-2.1.0-windows.msi
            bbbb2222  SnapSeek-2.1.0-android.apk
        """.trimIndent()
        assertEquals("bbbb2222", UpdateChecker.checksumFor(sums, "SnapSeek-2.1.0-android.apk"))
        assertNull(UpdateChecker.checksumFor(sums, "SnapSeek-2.1.0-x86_64.rpm"))
    }

    private fun release(tag: String, assets: List<String>, draft: Boolean = false, prerelease: Boolean = false): String {
        val entries = assets.joinToString(",") {
            """{"name":"$it","size":1234,"browser_download_url":"https://example.test/$it"}"""
        }
        return """
            {"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"html_url":"https://example.test/releases/$tag",
             "body":"what changed","assets":[$entries]}
        """.trimIndent()
    }
}

/** The whole path, from what GitHub sends to what the app is told, with the network stood in for. */
class UpdateCheckEndToEndTest {

    private val release = """
        {"tag_name":"v2.3.0","draft":false,"prerelease":false,
         "html_url":"https://example.test/releases/v2.3.0","body":"Faster grid.\nFixed saving.",
         "assets":[
           {"name":"SnapSeek-2.3.0-windows.msi","size":90000000,"browser_download_url":"https://example.test/SnapSeek-2.3.0-windows.msi"},
           {"name":"SnapSeek-2.3.0-android.apk","size":12000000,"browser_download_url":"https://example.test/SnapSeek-2.3.0-android.apk"},
           {"name":"SHA256SUMS.txt","size":200,"browser_download_url":"https://example.test/SHA256SUMS.txt"}]}
    """.trimIndent()

    @Test
    fun `an older app is offered the new release`() = runBlocking {
        val checker = UpdateChecker(fetch = { release })
        val update = checker.check(currentVersion = "2.0.0", target = UpdateTarget.WINDOWS_INSTALLER)
        assertEquals("2.3.0", update?.version)
        assertEquals("SnapSeek-2.3.0-windows.msi", update?.fileName)
        assertTrue(update?.notes?.startsWith("Faster grid") == true)
    }

    @Test
    fun `an up to date app is told nothing`() = runBlocking {
        val checker = UpdateChecker(fetch = { release })
        assertNull(checker.check(currentVersion = "2.3.0", target = UpdateTarget.ANDROID))
        assertNull(checker.check(currentVersion = "9.0.0", target = UpdateTarget.ANDROID))
    }

    @Test
    fun `a release with nothing for this platform offers nothing`() = runBlocking {
        val checker = UpdateChecker(fetch = { release })
        assertNull(checker.check(currentVersion = "1.0.0", target = UpdateTarget.FEDORA))
    }

    @Test
    fun `github being unreachable is not an error anyone sees`() = runBlocking {
        assertNull(UpdateChecker(fetch = { null }).check("1.0.0", UpdateTarget.WINDOWS_INSTALLER))
        assertNull(UpdateChecker(fetch = { error("no network") }).check("1.0.0", UpdateTarget.WINDOWS_INSTALLER))
    }
}
