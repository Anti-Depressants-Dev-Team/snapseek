package dev.snapseek.core.download

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals

class FileNamerTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-09-14T22:15:30Z"), ZoneOffset.UTC)
    private val namer = FileNamer(fixedClock)
    private val template = FilenameTemplate(fixedClock)
    private val sha = "3fa9c2d1e0b7a6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1"
    private val web = mapOf("service" to "pinterest", "sha256" to sha, "hash8" to sha.take(8), "md5" to "9cf364e77f46183e2ebd75de757488e2", "original" to "y", "extension" to "png")
    private val booru = web + mapOf(
        "service" to "safebooru", "booru" to "safebooru", "id" to "123456",
        "artist" to "artist_x_(abc) artist_2",
        "character" to "lumine_(genshin_impact) lumine_(sweets_paradise)_(genshin_impact) aether_(genshin_impact)",
        "copyright" to "genshin_impact fate/grand_order",
        "tags" to "1girl solo genshin_impact", "rating" to "general", "width" to "2232", "height" to "1000", "score" to "42",
    )

    @Test
    fun `default web template yields service, date and short hash`() {
        assertEquals("pinterest_20260914-221530_3fa9c2d1.png", namer.fileName("{service}_{date}_{hash8}", web, "png"))
    }

    @Test
    fun `default booru template uses id and short md5`() {
        assertEquals("safebooru_123456_9cf364e7.jpg", namer.fileName("{booru}_{id}_{md5:maxlength=8}", booru, "jpg"))
    }

    @Test
    fun `boorusama style options work`() {
        val cases = listOf(
            "{character:nomod,delimiter=comma,limit=2}" to "lumine, lumine",
            "{artist:nomod}" to "artist_x artist_2",
            "{copyright:delimiter=underscore}" to "genshin_impact_fate/grand_order",
            "{rating:single_letter}" to "g",
            "{rating:case=upper}" to "GENERAL",
            "{id:pad_left=8}" to "00123456",
            "{date:format=yyyy-MM-dd}" to "2026-09-14",
            "{tags:maxlength=10}" to "1girl solo",
            "{width}x{height}" to "2232x1000",
            "{unknown_token}" to "",
            "{hash:maxlength=4}" to "3fa9",
        )
        for ((tpl, expected) in cases) {
            assertEquals(expected, template.render(tpl, booru), tpl)
        }
    }

    @Test
    fun `a template naming the extension is not given a second one and unsafe characters are replaced`() {
        assertEquals(
            "lumine_(genshin_impact) (genshin_impact) drawn by artist_x_(abc) artist_2 - 9cf364e77f46183e2ebd75de757488e2.jpg",
            namer.fileName("{character:limit=1} ({copyright:limit=1}) drawn by {artist} - {md5}.{extension}", booru, "jpg"),
        )
        assertEquals("genshin_impact fate_grand_order.png", namer.fileName("{copyright}", booru, "png"))
        assertEquals("a_b_c.png", namer.fileName("a:b?c", web, "png"))
    }

    @Test
    fun `an empty result falls back to the hash`() {
        assertEquals("${sha.take(16)}.png", namer.fileName("{artist}", web, "png"))
    }

    @Test
    fun `collisions get a numbered suffix starting at 2`() {
        val dir = createTempDirectory("snapseek-namer")
        dir.resolve("pic.png").createFile()
        dir.resolve("pic (2).png").createFile()
        assertEquals("pic (3).png", namer.nextFree(dir, "pic.png").name)
        assertEquals("other.png", namer.nextFree(dir, "other.png").name)
    }
}
