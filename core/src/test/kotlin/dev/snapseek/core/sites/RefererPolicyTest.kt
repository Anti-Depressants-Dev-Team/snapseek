package dev.snapseek.core.sites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RefererPolicyTest {
    private val policy = RefererPolicy()

    @Test
    fun `pixiv cdn gets the pixiv referer`() {
        assertEquals("https://www.pixiv.net/", policy.refererFor("i.pximg.net"))
        assertEquals("https://www.pixiv.net/", policy.refererForUrl("https://i.pximg.net/img-original/x.jpg"))
    }

    @Test
    fun `deviantart cdns get the deviantart referer`() {
        assertEquals("https://www.deviantart.com/", policy.refererFor("images-wixmp-ed30a86b8c4ca887773594c2.wixmp.com"))
    }

    @Test
    fun `matching is by suffix not substring`() {
        assertNull(policy.refererFor("notpixiv.net.example.com"))
        assertNull(policy.refererFor("example.com"))
        assertNull(policy.refererFor(null))
    }
}
