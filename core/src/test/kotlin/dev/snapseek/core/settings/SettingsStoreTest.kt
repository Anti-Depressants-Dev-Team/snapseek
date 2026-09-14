package dev.snapseek.core.settings

import dev.snapseek.core.model.OutputFormat
import dev.snapseek.core.services.DefaultServices
import dev.snapseek.core.services.ServiceRepository
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStoreTest {

    @Test
    fun `changes survive a reload`() {
        val file = createTempDirectory("snapseek-settings").resolve("settings.json")
        val store = SettingsStore(file)
        store.update { it.copy(darkMode = false, defaultFormat = OutputFormat.ORIGINAL) }

        val reloaded = SettingsStore(file)
        assertFalse(reloaded.current.darkMode)
        assertEquals(OutputFormat.ORIGINAL, reloaded.current.defaultFormat)
        assertEquals(DefaultServices.all.size, reloaded.current.services.size)
    }

    @Test
    fun `a corrupt file is set aside instead of crashing`() {
        val dir = createTempDirectory("snapseek-settings")
        val file = dir.resolve("settings.json")
        file.writeText("{ this is not json")
        val store = SettingsStore(file)
        assertTrue(store.current.darkMode)
        assertTrue(dir.resolve("settings.json.bad").exists())
    }

    @Test
    fun `service repository adds missing built-ins and keeps custom ones`() {
        val file = createTempDirectory("snapseek-settings").resolve("settings.json")
        val store = SettingsStore(file)
        store.update { it.copy(services = it.services.filterNot { s -> s.id == "giphy" }) }

        val repo = ServiceRepository(store)
        val custom = repo.addCustom("My Site", "mysite.example", null)
        assertEquals("https://mysite.example", custom.url)
        assertTrue(repo.current.any { it.id == "giphy" })
        assertTrue(repo.current.any { it.id == custom.id })

        repo.setEnabled("pixiv", false)
        repo.setUrl("pinterest", "https://www.pinterest.com/")
        assertFalse(repo.byId("pixiv")!!.enabled)
        assertEquals("https://www.pinterest.com/", repo.byId("pinterest")!!.url)

        repo.removeCustom(custom.id)
        repo.removeCustom("pixiv")
        assertFalse(repo.current.any { it.id == custom.id })
        assertTrue(repo.current.any { it.id == "pixiv" }, "built-ins can be disabled but never removed")
    }
}
