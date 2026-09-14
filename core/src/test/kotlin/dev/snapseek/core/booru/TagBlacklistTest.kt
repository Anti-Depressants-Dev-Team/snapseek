package dev.snapseek.core.booru

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagBlacklistTest {
    private val list = TagBlacklist.parse(
        """
        # comment line
        loli
        1boy solo
        gore -parody

        -only_negative
        """.trimIndent(),
    )

    @Test
    fun `single tags, and-groups, and negations behave like boorusama`() {
        assertEquals(3, list.size, "a rule with only negations is ignored")
        assertTrue(list.hides(listOf("1girl", "LOLI")))
        assertFalse(list.hides(listOf("1boy")))
        assertTrue(list.hides(listOf("1boy", "solo", "smile")))
        assertTrue(list.hides(listOf("gore")))
        assertFalse(list.hides(listOf("gore", "parody")))
        assertFalse(list.hides(listOf("cat")))
    }

    @Test
    fun `empty blacklist hides nothing and filter keeps order`() {
        assertFalse(TagBlacklist.parse("").hides(listOf("anything")))
        val posts = listOf("a" to listOf("cat"), "b" to listOf("loli"), "c" to listOf("dog"))
        assertEquals(listOf("a", "c"), list.filter(posts) { it.second }.map { it.first })
    }
}
