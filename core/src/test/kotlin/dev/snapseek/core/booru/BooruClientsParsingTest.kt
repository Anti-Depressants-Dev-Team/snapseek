package dev.snapseek.core.booru

import dev.snapseek.core.model.Service
import dev.snapseek.core.model.ServiceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BooruClientsParsingTest {

    @Test
    fun `danbooru posts carry categories and skip posts without a file`() {
        val body = """
            [{"id":7000001,"md5":"0123456789abcdef0123456789abcdef","file_url":"https://cdn.donmai.us/original/01/23/0123.png",
              "large_file_url":"https://cdn.donmai.us/sample/01/23/sample-0123.jpg","preview_file_url":"https://cdn.donmai.us/180x180/01/23/0123.jpg",
              "image_width":2000,"image_height":3000,"tag_string":"1girl solo hatsune_miku vocaloid wokada highres",
              "tag_string_artist":"wokada","tag_string_character":"hatsune_miku","tag_string_copyright":"vocaloid",
              "tag_string_general":"1girl solo","tag_string_meta":"highres","rating":"s","score":120,"source":"https://twitter.com/x","file_ext":"png","has_large":true},
             {"id":7000002,"md5":"ffff","tag_string":"gold_only","rating":"e"}]
        """.trimIndent()
        val posts = DanbooruClient.parsePosts(body)
        val p = posts.single()
        assertEquals(7000001L, p.id)
        assertEquals("sensitive", p.rating)
        assertEquals("png", p.extension)
        assertTrue(p.hasSample)
        assertEquals(TagCategory.ARTIST, p.tagCategories?.get("wokada"))
        assertEquals(TagCategory.CHARACTER, p.tagCategories?.get("hatsune_miku"))
        assertEquals(TagCategory.META, p.tagCategories?.get("highres"))
        assertEquals(6, p.tags.size)

        val suggestions = DanbooruClient.parseSuggestions("""[{"type":"tag-word","label":"hatsune miku","value":"hatsune_miku","category":4,"post_count":123456}]""")
        assertEquals(TagSuggestion("hatsune_miku", 123456, TagCategory.CHARACTER), suggestions.single())
    }

    @Test
    fun `moebooru posts absolutise protocol-relative urls and tag types parse from api v2`() {
        val body = """
            [{"id":1188000,"tags":"landscape scenic","md5":"abcdefabcdefabcdefabcdefabcdefab","file_url":"//files.yande.re/image/abcd/yande.re%201188000.jpg",
              "sample_url":"https://files.yande.re/sample/abcd/sample.jpg","preview_url":"https://assets.yande.re/data/preview/ab/cd/abcd.jpg",
              "jpeg_url":"//files.yande.re/jpeg/abcd/x.jpg","width":4000,"height":2250,"rating":"s","score":30,"source":"","file_ext":"jpg","author":"someone"}]
        """.trimIndent()
        val p = MoebooruClient.parsePosts(body, "https://yande.re").single()
        assertEquals("https://files.yande.re/image/abcd/yande.re%201188000.jpg", p.fileUrl)
        assertEquals("safe", p.rating)
        assertNull(p.source)
        assertTrue(p.hasSample)

        val types = MoebooruClient.parseTagTypes("""{"posts":[{"id":1}],"tags":{"landscape":"general","wokada":"artist","touhou":"copyright","reimu":"character","circle_x":"circle"}}""")
        assertEquals(TagCategory.ARTIST, types["wokada"])
        assertEquals(TagCategory.COPYRIGHT, types["touhou"])
        assertEquals(TagCategory.ARTIST, types["circle_x"])

        val s = MoebooruClient.parseSuggestions("""[{"id":1,"name":"landscape","count":5000,"type":0,"ambiguous":false}]""").single()
        assertEquals(TagSuggestion("landscape", 5000, TagCategory.GENERAL), s)
    }

    @Test
    fun `e621 posts unpack nested file, sample, tags and sources`() {
        val body = """
            {"posts":[{"id":4000000,"file":{"width":1200,"height":900,"ext":"png","md5":"11112222333344445555666677778888","url":"https://static1.e621.net/data/11/11/1111.png"},
              "preview":{"url":"https://static1.e621.net/data/preview/11/11/1111.jpg"},"sample":{"has":true,"url":"https://static1.e621.net/data/sample/11/11/1111.jpg"},
              "score":{"total":44},"tags":{"general":["smile"],"artist":["artist_a"],"copyright":["nintendo"],"character":["isabelle_(animal_crossing)"],"species":["canine"],"meta":["hi_res"],"lore":["female_(lore)"]},
              "rating":"s","sources":["","https://example.com/src"]},
             {"id":4000001,"file":{"url":null,"md5":"x"},"tags":{},"rating":"e"}]}
        """.trimIndent()
        val posts = E621Client.parsePosts(body)
        val p = posts.single()
        assertEquals("safe", p.rating)
        assertEquals(44, p.score)
        assertEquals("https://example.com/src", p.source)
        assertEquals(TagCategory.SPECIES, p.tagCategories?.get("canine"))
        assertEquals(TagCategory.LORE, p.tagCategories?.get("female_(lore)"))
        assertEquals(listOf("artist_a", "nintendo", "isabelle_(animal_crossing)", "canine", "smile", "hi_res", "female_(lore)"), p.tags)

        val s = E621Client.parseSuggestions("""[{"id":1,"name":"canine","post_count":900000,"category":5,"antecedent_name":null}]""").single()
        assertEquals(TagCategory.SPECIES, s.category)
    }

    @Test
    fun `philomena images use representations and derive rating and categories from tags`() {
        val body = """
            {"images":[{"id":3000000,"sha512_hash":"abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
              "view_url":"https://derpicdn.net/img/view/2024/1/1/3000000.png","representations":{"thumb":"//derpicdn.net/img/2024/1/1/3000000/thumb.png","large":"//derpicdn.net/img/2024/1/1/3000000/large.png","full":"//derpicdn.net/img/view/2024/1/1/3000000.png"},
              "width":1600,"height":1200,"tags":["safe","artist:someone","oc:somepony","pony","spoiler:s09e01"],"score":250,"source_url":"https://example.com","format":"png","hidden_from_users":false},
             {"id":3000001,"hidden_from_users":true,"tags":[]}]}
        """.trimIndent()
        val posts = PhilomenaClient.parsePosts(body, "https://derpibooru.org")
        val p = posts.single()
        assertEquals("safe", p.rating)
        assertEquals("https://derpicdn.net/img/2024/1/1/3000000/large.png", p.sampleUrl)
        assertEquals(32, p.md5.length)
        assertEquals(TagCategory.ARTIST, p.tagCategories?.get("artist:someone"))
        assertEquals(TagCategory.CHARACTER, p.tagCategories?.get("oc:somepony"))
        assertEquals(TagCategory.META, p.tagCategories?.get("spoiler:s09e01"))
        assertEquals(TagCategory.GENERAL, p.tagCategories?.get("pony"))

        val s = PhilomenaClient.parseSuggestions("""{"tags":[{"name":"artist:someone","images":12,"category":"origin"},{"name":"pony","images":900000,"category":null}]}""")
        assertEquals(TagCategory.ARTIST, s[0].category)
        assertEquals(TagCategory.GENERAL, s[1].category)

        val client = PhilomenaClient("https://derpibooru.org")
        assertEquals(listOf("twilight sparkle", "safe"), client.splitQuery("twilight sparkle, safe"))
        assertEquals("twilight sparkle, safe", client.joinQuery(listOf("twilight sparkle", "safe")))
    }

    @Test
    fun `szurubooru posts resolve relative urls and read tag categories`() {
        val body = """
            {"results":[{"id":77,"checksum":"deadbeef","checksumMD5":"cafebabecafebabecafebabecafebabe","contentUrl":"data/posts/77_abc.jpg","thumbnailUrl":"data/generated-thumbnails/77_abc.jpg",
              "canvasWidth":800,"canvasHeight":600,"tags":[{"names":["cat","kitty"],"category":"default"},{"names":["someone"],"category":"artist"}],
              "safety":"sketchy","score":3,"source":"https://a.example\nhttps://b.example","type":"image","mimeType":"image/jpeg","user":{"name":"admin"}}],"total":1}
        """.trimIndent()
        val p = SzurubooruClient.parsePosts(body, "https://booru.example").single()
        assertEquals("https://booru.example/data/posts/77_abc.jpg", p.fileUrl)
        assertEquals("cafebabecafebabecafebabecafebabe", p.md5)
        assertEquals("sketchy", p.rating)
        assertEquals("https://a.example", p.source)
        assertEquals(listOf("cat", "someone"), p.tags)
        assertEquals(TagCategory.ARTIST, p.tagCategories?.get("someone"))
        assertEquals(TagCategory.GENERAL, p.tagCategories?.get("cat"))
        assertFalse(p.hasSample)

        val s = SzurubooruClient.parseSuggestions("""{"results":[{"names":["cat"],"category":"default","usages":10}]}""").single()
        assertEquals(TagSuggestion("cat", 10, TagCategory.GENERAL), s)
    }

    @Test
    fun `factory picks the client for each kind and presets are unique`() {
        fun svc(kind: ServiceKind, url: String) = Service(id = "x", name = "x", url = url, kind = kind, login = "u", apiKey = "k")
        assertIs<GelbooruV2Client>(BooruClients.create(svc(ServiceKind.GELBOORU_V2, "https://safebooru.org")))
        assertIs<DanbooruClient>(BooruClients.create(svc(ServiceKind.DANBOORU, "https://danbooru.donmai.us")))
        assertIs<MoebooruClient>(BooruClients.create(svc(ServiceKind.MOEBOORU, "https://yande.re")))
        assertIs<E621Client>(BooruClients.create(svc(ServiceKind.E621, "https://e926.net")))
        assertIs<PhilomenaClient>(BooruClients.create(svc(ServiceKind.PHILOMENA, "https://derpibooru.org")))
        assertIs<SzurubooruClient>(BooruClients.create(svc(ServiceKind.SZURUBOORU, "https://booru.example")))

        assertEquals(BooruPresets.all.size, BooruPresets.all.map { it.host }.toSet().size)
        assertEquals(ServiceKind.MOEBOORU, BooruPresets.byHost("yande.re")?.kind)
        assertEquals("https://danbooru.donmai.us", BooruDetector.normalise("danbooru.donmai.us/posts?tags=x"))
        assertEquals("https://yande.re", BooruDetector.normalise("https://yande.re/post/show/1"))
    }
}
