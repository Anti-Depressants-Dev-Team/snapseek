package dev.snapseek.core.booru

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GelbooruV2ParsingTest {

    private val safebooruJson = """
        [{"directory":"3456","hash":"0123abcd0123abcd0123abcd0123abcd","height":1400,"id":5551212,"image":"0123abcd.png",
          "change":1700000000,"owner":"someone","parent_id":0,"rating":"general","sample":1,"sample_height":850,"sample_width":600,
          "score":12,"tags":"1girl solo lumine_(genshin_impact) genshin_impact","width":1000,
          "file_url":"https://safebooru.org/images/3456/0123abcd.png",
          "preview_url":"https://safebooru.org/thumbnails/3456/thumbnail_0123abcd.jpg",
          "sample_url":"https://safebooru.org/samples/3456/sample_0123abcd.jpg","source":"https://www.pixiv.net/artworks/1","status":"active","has_notes":false,"comment_count":0},
         {"directory":"7","hash":"ffff","id":"42","image":"ffff.gif","sample":false,"tags":"animated","rating":"s","width":"100","height":"50"}]
    """.trimIndent()

    private val gelbooruJson = """
        {"@attributes":{"limit":2,"offset":0,"count":9999},
         "post":[{"id":9000001,"md5":"abcdefabcdefabcdefabcdefabcdefab","directory":"ab/cd","image":"abcdef.jpg","width":800,"height":600,
                  "rating":"general","score":5,"tags":"cat","sample":0,"file_url":"https://img3.gelbooru.com/images/ab/cd/abcdef.jpg",
                  "preview_url":"https://img3.gelbooru.com/thumbnails/ab/cd/thumbnail_abcdef.jpg","sample_url":"","source":""}]}
    """.trimIndent()

    @Test
    fun `safebooru arrays parse with sample and preview urls and loosely typed fields`() {
        val posts = GelbooruV2Client.parsePosts(safebooruJson, "https://safebooru.org")
        assertEquals(2, posts.size)
        val p = posts[0]
        assertEquals(5551212L, p.id)
        assertEquals("https://safebooru.org/samples/3456/sample_0123abcd.jpg", p.sampleUrl)
        assertEquals("png", p.extension)
        assertEquals(listOf("1girl", "solo", "lumine_(genshin_impact)", "genshin_impact"), p.tags)
        assertEquals("General", p.ratingLabel)
        assertEquals("https://www.pixiv.net/artworks/1", p.source)
        assertTrue(p.hasSample)

        val gif = posts[1]
        assertEquals(42L, gif.id)
        assertEquals("https://safebooru.org/images/7/ffff.gif", gif.fileUrl)
        assertEquals("https://safebooru.org/thumbnails/7/thumbnail_ffff.jpg", gif.previewUrl)
        assertEquals(gif.fileUrl, gif.sampleUrl, "no sample means the sample is the file itself")
        assertTrue(gif.isAnimated)
        assertEquals(2f, gif.aspectRatio)
    }

    @Test
    fun `gelbooru objects parse and an empty sample url falls back to the file`() {
        val posts = GelbooruV2Client.parsePosts(gelbooruJson, "https://gelbooru.com")
        assertEquals(1, posts.size)
        assertEquals("abcdefabcdefabcdefabcdefabcdefab", posts[0].md5)
        assertEquals("", posts[0].sampleUrl.ifEmpty { "" })
        assertNull(posts[0].source)
    }

    @Test
    fun `blank bodies and posts without a hash are skipped`() {
        assertEquals(emptyList(), GelbooruV2Client.parsePosts("", "https://safebooru.org"))
        assertEquals(emptyList(), GelbooruV2Client.parsePosts("""[{"id":1,"image":"x.jpg","directory":"1"}]""", "https://safebooru.org"))
    }

    @Test
    fun `both autocomplete shapes parse`() {
        val simple = GelbooruV2Client.parseSuggestions("""[{"label":"genshin_impact (12345)","value":"genshin_impact"},{"label":"weird","value":"weird"}]""")
        assertEquals(TagSuggestion("genshin_impact", 12345, TagCategory.UNKNOWN), simple[0])
        assertEquals(TagSuggestion("weird", null, TagCategory.UNKNOWN), simple[1])

        val v2 = GelbooruV2Client.parseSuggestions("""[{"type":"tag","label":"hatsune miku","value":"hatsune_miku","post_count":"99","category":"character"}]""")
        assertEquals(TagSuggestion("hatsune_miku", 99, TagCategory.CHARACTER), v2.single())
    }

    @Test
    fun `tag sidebar html yields categories`() {
        val html = """
            <div id="tag-sidebar"><h6>Tags</h6><ul>
              <li class="tag-type-artist tag"><a href="index.php?page=post&amp;s=list&amp;tags=some_artist">some artist</a> <span>12</span></li>
              <li class="tag-type-character tag"><a href="index.php?page=post&amp;s=list&amp;tags=lumine_%28genshin_impact%29">lumine</a></li>
              <li class="tag-type-copyright tag"><a href="index.php?page=post&amp;s=list&amp;tags=genshin_impact">genshin impact</a></li>
              <li class="tag-type-general tag"><a href="index.php?page=post&amp;s=list&amp;tags=1girl">1girl</a></li>
              <li class="tag-type-metadata tag"><a href="index.php?page=post&amp;s=list&amp;tags=highres">highres</a></li>
            </ul></div><ul><li class="tag-type-general"><a href="?tags=not_in_sidebar">x</a></li></ul>
        """.trimIndent()
        val cats = GelbooruV2Client.parseTagSidebar(html)
        assertEquals(TagCategory.ARTIST, cats["some_artist"])
        assertEquals(TagCategory.CHARACTER, cats["lumine_(genshin_impact)"])
        assertEquals(TagCategory.COPYRIGHT, cats["genshin_impact"])
        assertEquals(TagCategory.GENERAL, cats["1girl"])
        assertEquals(TagCategory.META, cats["highres"])
        assertNull(cats["not_in_sidebar"])
    }
}
