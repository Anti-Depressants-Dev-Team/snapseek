package dev.snapseek.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostBlocklistTest {

    @Test
    fun `parses hosts-file lines, bare domains and abp domain rules`() {
        val list = HostBlocklist.parse(
            """
            # comment
            0.0.0.0 ads.example.com
            127.0.0.1 localhost
            tracker.example.net   # trailing comment
            ||banner.example.org^
            ! abp comment
            [Adblock Plus 2.0]
            not a domain line at all
            """.trimIndent(),
        )
        assertEquals(3, list.size)
        assertTrue(list.blocks("ads.example.com"))
        assertTrue(list.blocks("tracker.example.net"))
        assertTrue(list.blocks("banner.example.org"))
        assertFalse(list.blocks("localhost"))
    }

    @Test
    fun `subdomains of a blocked domain are blocked, parents are not`() {
        val list = HostBlocklist(listOf("doubleclick.net"))
        assertTrue(list.blocks("ad.doubleclick.net"))
        assertTrue(list.blocks("DOUBLECLICK.NET"))
        assertFalse(list.blocks("net"))
        assertFalse(list.blocks("doubleclick.network"))
        assertFalse(list.blocks(null))
    }

    @Test
    fun `seed list ships with the app`() {
        val seed = HostBlocklist.fromResource("/adblock/seed-hosts.txt")
        assertTrue(seed.size > 40)
        assertTrue(seed.blocks("pagead2.googlesyndication.com"))
        assertFalse(seed.blocks("i.pinimg.com"))
    }
}
