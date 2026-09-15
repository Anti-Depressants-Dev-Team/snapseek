package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind
import dev.snapseek.core.sites.ZerochanResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Payloads here are trimmed copies of what the live sites returned on 15 Sep 2026. */
class PlatformClientsParsingTest {

    @Test
    fun `pinterest search yields pins with originals, pinner, board and a bookmark`() {
        val body = """
            {"resource_response":{"status":"success","code":0,"bookmark":"Y2JVSG81V2sxcmNH","data":{"results":[
              {"type":"pin","id":"1055599818","grid_title":"AWW\n( not mine)","description":"random from insta NOT MINE","image_signature":"5487ddafc9df7e0f4417b6fc40e48965",
               "images":{"170x":{"url":"https://i.pinimg.com/170x/54/87/dd/x.jpg","width":170,"height":227},"236x":{"url":"https://i.pinimg.com/236x/54/87/dd/x.jpg"},
                         "736x":{"url":"https://i.pinimg.com/736x/54/87/dd/x.jpg"},"orig":{"url":"https://i.pinimg.com/originals/54/87/dd/5487ddafc9df7e0f4417b6fc40e48965.jpg","width":1200,"height":1600}},
               "pinner":{"username":"crunchypebbles88"},"board":{"name":"cats 🐈‍⬛"},"pin_join":{"visual_annotation":["Cute Cats","Kittens"]},"reaction_counts":{"1":4},"domain":"Uploaded by user"},
              {"type":"story","id":"999"}]}},
             "resource":{"options":{"bookmarks":["Y2JVSG81V2sxcmNH"]}}}
        """.trimIndent()
        val (posts, next) = PinterestClient.parseFeed(body)
        val p = posts.single()
        assertEquals(1055599818L, p.id)
        assertEquals("5487ddafc9df7e0f4417b6fc40e48965", p.md5)
        assertEquals("https://i.pinimg.com/originals/54/87/dd/5487ddafc9df7e0f4417b6fc40e48965.jpg", p.fileUrl)
        assertEquals("https://i.pinimg.com/236x/54/87/dd/x.jpg", p.previewUrl)
        assertEquals(1200, p.width)
        assertEquals("AWW ( not mine)", p.title)
        assertEquals(TagCategory.ARTIST, p.tagCategories?.get("crunchypebbles88"))
        assertEquals(TagCategory.COPYRIGHT, p.tagCategories?.get("cats 🐈‍⬛"))
        assertEquals(TagCategory.GENERAL, p.tagCategories?.get("Cute Cats"))
        assertEquals(4, p.score)
        assertEquals("Y2JVSG81V2sxcmNH", next)

        assertNull(PinterestClient.parseFeed("""{"resource_response":{"data":{"results":[]}},"resource":{"options":{"bookmarks":["-end-"]}}}""").second)
        val client = PinterestClient("https://ru.pinterest.com/")
        assertEquals(listOf("minimalist desk"), client.splitQuery(" minimalist   desk "))
        assertTrue(client.supportsEmptyQuery)
        assertEquals("Pinner", client.categoryLabel(TagCategory.ARTIST))
        assertEquals("board:123", client.feedQuery(RemoteCollection("123", "Cats")))
        assertEquals("Everything I saved", client.displayTag("mine:"))
        assertEquals("Saved: yuno gasai", client.displayTag("mine:yuno gasai"))
    }

    @Test
    fun `pinterest account, boards and errors parse from the shapes the site uses`() {
        val viaContext = PinterestClient.parseAccount("""{"resource_response":{"data":null},"client_context":{"is_authenticated":true,"user":{"id":"42","username":"cem","full_name":"Cem O.","image_medium_url":"https://i.pinimg.com/75x75_RS/a.jpg"}}}""")
        assertEquals(RemoteAccount("42", "cem", "Cem O.", "https://i.pinimg.com/75x75_RS/a.jpg"), viaContext)

        val viaData = PinterestClient.parseAccount("""{"resource_response":{"data":{"id":"42","username":"cem","full_name":"Cem","type":"user"}}}""")
        assertEquals("cem", viaData?.username)
        assertNull(PinterestClient.parseAccount("""{"resource_response":{"data":[]}}"""))

        val html = """<html><script id="__PWS_INITIAL_PROPS__" type="application/json">{"initialReduxState":{"pins":{}},"context":{"user":{"id":"42","username":"cem","full_name":"Cem"},"app_version":"x"}}</script></html>"""
        assertEquals("cem", PinterestClient.parseAccountFromHtml(html)?.username)

        val boards = PinterestClient.parseBoards("""{"resource_response":{"data":[{"type":"board","id":"1001","name":"Inspo","url":"/cem/inspo/","pin_count":12,"privacy":"public","image_thumbnail_url":"https://i.pinimg.com/x.jpg"},{"type":"board","id":"1002","name":"Secret","privacy":"secret","pin_count":0},{"type":"story","id":"z"}]}}""")
        assertEquals(2, boards.size)
        assertEquals(RemoteCollection("1001", "Inspo", 12, "https://i.pinimg.com/x.jpg", false, "/cem/inspo/"), boards[0])
        assertTrue(boards[1].isPrivate)

        assertEquals("Authorization failed.", PinterestClient.errorMessage("""{"resource_response":{"error":{"status":"failure","http_status":401,"code":3,"message":"Authorization failed."},"data":null}}"""))
    }

    @Test
    fun `pixiv reads the account, its bookmark tags and its feeds`() {
        // Pixiv hands a signed-in browser its viewer and CSRF token in one HTML-escaped meta tag.
        val html = """<html><head><meta name="global-data" id="meta-global-data" content="{&quot;token&quot;:&quot;abc123&quot;,&quot;userData&quot;:{&quot;id&quot;:&quot;9876&quot;,&quot;pixivId&quot;:&quot;yabosen&quot;,&quot;name&quot;:&quot;Yabo&quot;,&quot;profileImg&quot;:&quot;https://i.pximg.net/u.jpg&quot;}}"/></head></html>"""
        val data = PixivClient.globalData(html)!!
        assertEquals("abc123", data.str("token"))
        assertEquals(RemoteAccount("9876", "yabosen", "Yabo", "https://i.pximg.net/u.jpg"), PixivClient.accountFrom(data))
        // Signed out there is no such tag, which is how "nobody is logged in" is told apart from a broken read.
        assertNull(PixivClient.globalData("<html><head><title>pixiv</title></head></html>"))

        val tags = PixivClient.parseBookmarkTags("""{"error":false,"body":{"public":[{"tag":"景色","cnt":12},{"tag":"未分類","cnt":3}],"private":[{"tag":"secret","cnt":1}]}}""")
        assertEquals(listOf("景色", "secret"), tags.map { it.name })
        assertEquals(12, tags[0].count)
        assertTrue(tags[1].isPrivate)

        val work = """{"id":"149699675","title":"森の中","illustType":0,"xRestrict":0,"url":"https://i.pximg.net/c/250x250_80_a2/img-master/img/2026/09/15/18/48/15/149699675_p0_square1200.jpg","tags":["風景"],"userName":"けい","width":1754,"height":1240,"pageCount":1}"""
        val bookmarks = PixivClient.parseBookmarks("""{"body":{"works":[$work,{"id":"0","isMasked":true}],"total":2}}""")
        assertEquals(1, bookmarks.size)
        assertEquals("https://i.pximg.net/img-master/img/2026/09/15/18/48/15/149699675_p0_master1200.jpg", bookmarks.single().fileUrl)

        // The follow feed lists the works unordered and the order separately.
        val second = work.replace("149699675", "149699676")
        val feed = PixivClient.parseFollowFeed("""{"body":{"page":{"ids":[149699676,149699675]},"thumbnails":{"illust":[$work,$second]}}}""")
        assertEquals(listOf(149699676L, 149699675L), feed.map { it.id })

        // A signed-in session cookie starts with the account's own id, the fallback when the meta tag is missing.
        assertEquals("9876", PixivClient.userIdFromCookie("first_visit_datetime=x; PHPSESSID=9876_a1b2c3; device_token=y"))
        assertNull(PixivClient.userIdFromCookie("PHPSESSID=anonymous_session; p_ab_id=3"))

        assertEquals("Invalid request.", PixivClient.errorMessage("""{"error":true,"message":"Invalid request.","body":[]}"""))
        assertNull(PixivClient.errorMessage("""{"error":false,"body":{"last_bookmark_id":"1"}}"""))

        val client = PixivClient("https://www.pixiv.net/")
        assertEquals("Everything I bookmarked", client.displayTag("mine:"))
        assertEquals("Bookmarked: 景色", client.displayTag("mine:景色"))
        assertEquals("mine:", client.feedQuery(RemoteCollection(PixivClient.ALL_BOOKMARKS, "All bookmarks")))
        assertEquals("mine:景色", client.feedQuery(RemoteCollection("景色", "景色")))
    }

    @Test
    fun `typing narrows a long collection list, closest names first`() {
        val boards = listOf("Emo/Goth", "Yuno Gasai", "Asthetics", "kaneki ken", "I'm a failure", "Juuzou Suzuya", "gas station")
            .map { RemoteCollection(it, it) }
        fun names(q: String) = boards.matching(q).map { it.name }

        assertEquals(boards, boards.matching("   "))
        // Starts-with wins over a word that starts with it, which wins over a match in the middle.
        assertEquals(listOf("gas station", "Yuno Gasai"), names("gas"))
        // Words may be typed in any order, and case never matters.
        assertEquals(listOf("Yuno Gasai"), names("GASAI yuno"))
        assertEquals(listOf("Emo/Goth"), names("goth"))
        assertEquals(emptyList<String>(), names("nothing here"))
    }

    @Test
    fun `an anonymous pinterest visitor is not mistaken for an account`() {
        // What the live site answers with no session: client_context.user exists but holds only an anonymous id.
        val anonymous = """{"resource_response":{"data":{"results":[]}},"client_context":{"is_authenticated":false,"unauth_id":"5187ebc4","user":{"unauth_id":"5187ebc4","ip_country":"RO","ip_region":"IF"}}}"""
        assertNull(PinterestClient.parseAccount(anonymous))
        assertNull(PinterestClient.parseAccountFromHtml("""<html><script type="application/json">{"context":{"user":{"unauth_id":"5187ebc4"}}}</script></html>"""))

        // Paging tokens: a real one continues, the two "nothing left" spellings stop.
        assertEquals("LT4xNDI1", PinterestClient.nextBookmark("""{"resource":{"options":{"bookmarks":["LT4xNDI1"]}}}"""))
        assertNull(PinterestClient.nextBookmark("""{"resource":{"options":{"bookmarks":["-end-"]}}}"""))
        assertNull(PinterestClient.nextBookmark("""{"resource":{"options":{"bookmarks":["Y2JOb25lO2Vz"]}}}"""))
        assertNull(PinterestClient.nextBookmark("""{"resource_response":{"status":"success"}}"""))
    }

    @Test
    fun `pixiv search and ranking map thumbnails to master renders`() {
        val search = """
            {"error":false,"body":{"illustManga":{"data":[
              {"isAdContainer":true},
              {"id":"149699675","title":"森の中","illustType":0,"xRestrict":0,"url":"https://i.pximg.net/c/250x250_80_a2/img-master/img/2026/09/15/18/48/15/149699675_p0_square1200.jpg",
               "tags":["オリジナル","風景","landscape"],"userId":"1","userName":"志熊けい","width":1754,"height":1240,"pageCount":3,"aiType":1}],"total":21163,"lastPage":10}}}
        """.trimIndent()
        val p = PixivClient.parseSearch(search).single()
        assertEquals(149699675L, p.id)
        assertEquals("https://i.pximg.net/img-master/img/2026/09/15/18/48/15/149699675_p0_master1200.jpg", p.fileUrl)
        assertEquals("general", p.rating)
        assertEquals(TagCategory.ARTIST, p.tagCategories?.get("志熊けい"))
        assertEquals(TagCategory.META, p.tagCategories?.get("3_pages"))
        assertEquals("森の中", p.title)

        val ranking = """{"contents":[{"title":"x","url":"https://i.pximg.net/c/480x960/img-master/img/2026/09/13/00/37/35/149596619_p0_master1200.jpg","illust_id":149596619,"tags":["百合"],"width":874,"height":1226,"illust_page_count":"2","illust_type":"0","user_name":"千種","rating_count":4200}],"mode":"daily"}"""
        val r = PixivClient.parseRanking(ranking).single()
        assertEquals("https://i.pximg.net/img-master/img/2026/09/13/00/37/35/149596619_p0_master1200.jpg", r.fileUrl)
        assertEquals(4200, r.score)

        val s = PixivClient.parseSuggestions("""{"candidates":[{"tag_name":"宝石の国","access_count":"114701574","tag_translation":"Land of the Lustrous","type":"tag_translation"}]}""").single()
        assertEquals(TagSuggestion("宝石の国", 114701574, TagCategory.GENERAL), s)

        val (words, params) = extractParams("landscape mode:safe order:popular_d", setOf("mode", "order"))
        assertEquals(listOf("landscape"), words)
        assertEquals(mapOf("mode" to "safe", "order" to "popular_d"), params)
    }

    @Test
    fun `deviantart rss items become posts with author and category`() {
        val xml = """
            <rss><channel><item><title>cheese cats </title><link>https://www.deviantart.com/graypillow/art/cheese-cats-1376736521</link>
            <media:rating>nonadult</media:rating><media:credit role="author" scheme="urn:ebu">GrayPillow</media:credit>
            <media:credit role="author" scheme="urn:ebu">https://a.deviantart.net/avatars/g/r/graypillow.jpg?13</media:credit>
            <media:category label="Digital Art">digitalart</media:category>
            <media:thumbnail url="https://images-wixmp.example/t150.jpg" height="150" width="123"/>
            <media:thumbnail url="https://images-wixmp.example/t300.jpg" height="366" width="300"/>
            <media:content url="https://images-wixmp.example/pre.jpg?token=abc&amp;x=1" height="988" width="809" medium="image"/>
            </item><item><title>a poem</title><link>https://www.deviantart.com/x/art/poem-5</link><media:content url="https://x/y" medium="document"/></item></channel></rss>
        """.trimIndent()
        val posts = DeviantArtClient.parseRss(xml)
        val p = posts.single()
        assertEquals(1376736521L, p.id)
        assertEquals("https://images-wixmp.example/pre.jpg?token=abc&x=1", p.fileUrl)
        assertEquals("https://images-wixmp.example/t300.jpg", p.previewUrl)
        assertEquals(809, p.width)
        assertEquals("general", p.rating)
        assertEquals(TagCategory.ARTIST, p.tagCategories?.get("GrayPillow"))
        assertEquals(TagCategory.COPYRIGHT, p.tagCategories?.get("Digital Art"))
        assertEquals("cheese cats", p.title)
    }

    @Test
    fun `wallhaven results and detail tags parse`() {
        val body = """{"data":[{"id":"mlo379","url":"https://wallhaven.cc/w/mlo379","path":"https://w.wallhaven.cc/full/ml/wallhaven-mlo379.jpg","thumbs":{"large":"https://th.wallhaven.cc/lg/ml/mlo379.jpg","original":"https://th.wallhaven.cc/orig/ml/mlo379.jpg","small":"https://th.wallhaven.cc/small/ml/mlo379.jpg"},
            "dimension_x":6000,"dimension_y":4000,"purity":"sfw","category":"general","file_type":"image/jpeg","favorites":12}],"meta":{"current_page":1,"last_page":2095,"per_page":24}}"""
        val p = WallhavenClient.parseSearch(body).single()
        assertEquals("mlo379", p.sourceId)
        assertEquals("mlo379".toLong(36), p.id)
        assertEquals("safe", p.rating)
        assertEquals("jpeg", p.extension)
        assertEquals("https://th.wallhaven.cc/small/ml/mlo379.jpg", p.previewUrl)
        val tags = WallhavenClient.parseDetailTags("""{"data":{"id":"mlo379","category":"general","tags":[{"id":1,"name":"landscape","category":"Nature"},{"id":2,"name":"mountains","category":"Nature"}]}}""")
        assertEquals(TagCategory.GENERAL, tags["landscape"])
        assertEquals(TagCategory.COPYRIGHT, tags["general"])
    }

    @Test
    fun `zerochan listing swaps avif thumbnails for jpeg and points at the full png`() {
        val body = """{"items":[{"id":4034550,"width":970,"height":6061,"md5":"5afa0a30ef0cfaf7eabd36228691ae35","thumbnail":"https://s3.zerochan.net/240/00/41/4034550.avif","source":"https://www.pixiv.net/en/artworks/112368643","tag":"Genshin Impact","tags":["Female","Fanart"]}]}"""
        val p = ZerochanClient.parseListing(body).single()
        assertEquals("https://s3.zerochan.net/240/00/41/4034550.jpg", p.previewUrl)
        assertEquals("https://s3.zerochan.net/600/00/41/4034550.jpg", p.sampleUrl)
        assertEquals("https://static.zerochan.net/Genshin.Impact.full.4034550.png", p.fileUrl)
        assertEquals(TagCategory.COPYRIGHT, p.tagCategories?.get("Genshin Impact"))
        assertEquals("5afa0a30ef0cfaf7eabd36228691ae35", p.md5)

        val resolved = ZerochanResolver.resolve(p.fileUrl, "")!!
        assertEquals(listOf("https://static.zerochan.net/Genshin.Impact.full.4034550.png", "https://static.zerochan.net/Genshin.Impact.full.4034550.jpg", "https://static.zerochan.net/Genshin.Impact.full.4034550.gif"), resolved.candidates)

        val s = ZerochanClient.parseSuggestions("Genshin Impact|Game|-\nLumine|Character|Genshin Impact\n")
        assertEquals(TagCategory.COPYRIGHT, s[0].category)
        assertEquals(TagSuggestion("Lumine", null, TagCategory.CHARACTER), s[1])
        assertEquals(listOf("Genshin Impact", "Lumine"), ZerochanClient("https://www.zerochan.net").splitQuery("Genshin Impact, Lumine"))
    }

    @Test
    fun `giphy and tenor payloads parse and missing keys are explained`() {
        val giphy = """{"data":[{"type":"gif","id":"3o7TKSjRrfIPjeiVyM","url":"https://giphy.com/gifs/cat-3o7TKSjRrfIPjeiVyM","username":"catlady","rating":"g","title":"Happy Cat GIF",
            "images":{"original":{"url":"https://media.giphy.com/media/3o7/giphy.gif","width":"480","height":"270"},"fixed_width_still":{"url":"https://media.giphy.com/media/3o7/200w_s.gif"},"downsized":{"url":"https://media.giphy.com/media/3o7/giphy-downsized.gif"}}}],"meta":{"status":200}}"""
        val g = GiphyClient.parseGifs(giphy).single()
        assertEquals("3o7TKSjRrfIPjeiVyM", g.sourceId)
        assertEquals("gif", g.extension)
        assertEquals("general", g.rating)
        assertEquals(TagCategory.ARTIST, g.tagCategories?.get("catlady"))
        assertTrue(g.tags.contains("happy"))

        val tenor = """{"results":[{"id":"16989471","title":"","content_description":"Thumbs Up GIF","itemurl":"https://tenor.com/view/thumbs-up-gif-16989471","tags":["thumbs up","yes"],
            "media_formats":{"gif":{"url":"https://media.tenor.com/x/tenor.gif","dims":[498,280]},"tinygifpreview":{"url":"https://media.tenor.com/x/tiny.png"},"mediumgif":{"url":"https://media.tenor.com/x/medium.gif"}}}],"next":"CAgQ"}"""
        val (t, next) = TenorClient.parseResults(tenor)
        assertEquals("CAgQ", next)
        assertEquals(498, t.single().width)
        assertEquals("Thumbs Up GIF", t.single().title)
        assertEquals("https://media.tenor.com/x/tiny.png", t.single().previewUrl)

        val e = runCatching { kotlinx.coroutines.runBlocking { GiphyClient("https://giphy.com").posts("cats", 0, 10) } }.exceptionOrNull()
        assertTrue(e is MissingCredentialsException, "got $e")
        assertTrue(e!!.message!!.contains("developers.giphy.com"))
    }

    @Test
    fun `stock photo payloads parse`() {
        val u = UnsplashClient.parsePhotos("""{"results":[{"id":"abc123","width":4000,"height":6000,"description":null,"alt_description":"foggy forest","likes":10,"urls":{"raw":"https://images.unsplash.com/raw","full":"https://images.unsplash.com/full","regular":"https://images.unsplash.com/regular","small":"https://images.unsplash.com/small"},"links":{"html":"https://unsplash.com/photos/abc123"},"user":{"name":"Ann Smith","username":"ann"},"tags":[{"title":"forest"}]}]}""").single()
        assertEquals("abc123", u.sourceId)
        assertEquals("foggy forest", u.title)
        assertEquals(TagCategory.ARTIST, u.tagCategories?.get("Ann Smith"))
        assertEquals(TagCategory.GENERAL, u.tagCategories?.get("forest"))

        val p = PexelsClient.parsePhotos("""{"photos":[{"id":2014422,"width":3024,"height":3024,"url":"https://www.pexels.com/photo/2014422/","photographer":"Joey","alt":"Brown Rocks","src":{"original":"https://images.pexels.com/photos/2014422/pexels-photo.jpeg","large2x":"https://images.pexels.com/l2x.jpeg","medium":"https://images.pexels.com/m.jpeg"}}]}""").single()
        assertEquals(2014422L, p.id)
        assertEquals("https://images.pexels.com/l2x.jpeg", p.sampleUrl)

        val px = PixabayClient.parseHits("""{"hits":[{"id":195893,"pageURL":"https://pixabay.com/photos/blossom-195893/","tags":"blossom, bloom, flower","previewURL":"https://cdn.pixabay.com/photo/p.jpg","webformatURL":"https://cdn.pixabay.com/photo/w_640.jpg","largeImageURL":"https://cdn.pixabay.com/photo/l_1280.jpg","imageWidth":4000,"imageHeight":2250,"user":"Josch13","likes":334}]}""").single()
        assertEquals(listOf("Josch13", "blossom", "bloom", "flower"), px.tags)
        assertEquals("https://cdn.pixabay.com/photo/l_1280.jpg", px.fileUrl)
        assertEquals(334, px.score)
    }

    @Test
    fun `web grid scraper picks the largest candidate and skips icons`() {
        val html = """
            <html><body>
              <a href="/wallpapers/abc"><img data-src="/images/hd/abc-1920.jpg" alt="Mountain lake" width="400" height="225"></a>
              <img srcset="https://cdn.example/x-400.jpg 400w, https://cdn.example/x-1600.jpg 1600w" alt="Sunset">
              <img src="/static/img/tools/logo.png" width="80" height="80">
              <img src="/tiny.png" width="16" height="16">
              <img src="data:image/gif;base64,R0lGOD">
              <img data-src="/images/hd/abc-1920.jpg">
            </body></html>
        """.trimIndent()
        val posts = WebGridClient.extractImages(html, "https://wallpapers.com/mountain")
        assertEquals(2, posts.size)
        assertEquals("https://wallpapers.com/images/hd/abc-1920.jpg", posts[0].fileUrl)
        assertEquals("https://wallpapers.com/wallpapers/abc", posts[0].source)
        assertEquals("Mountain lake", posts[0].title)
        assertEquals("https://cdn.example/x-1600.jpg", posts[1].fileUrl)

        val client = WebGridClient("https://wallpapers.com/{q}")
        assertFalse(client.supportsEmptyQuery)
        assertTrue(WebGridClient("https://example.com/gallery").supportsEmptyQuery)
    }

    @Test
    fun `factory covers every kind and hosts map to kinds`() {
        ServiceKind.entries.filter { it != ServiceKind.WEB }.forEach { kind ->
            val svc = dev.snapseek.core.model.Service(id = "x", name = "x", url = "https://example.com/{q}", kind = kind, apiKey = "k", login = "u")
            assertEquals(kind, BooruClients.create(svc).kind, "factory for $kind")
        }
        assertEquals(ServiceKind.PINTEREST, BooruPresets.kindForHost("ru.pinterest.com"))
        assertEquals(ServiceKind.PINTEREST, BooruPresets.kindForHost("www.pinterest.co.uk"))
        assertEquals(ServiceKind.PIXIV, BooruPresets.kindForHost("www.pixiv.net"))
        assertEquals(ServiceKind.DANBOORU, BooruPresets.kindForHost("safebooru.donmai.us"))
        assertEquals(ServiceKind.MOEBOORU, BooruPresets.kindForHost("yande.re"))
        assertNull(BooruPresets.kindForHost("example.com"))
        assertEquals(BooruPresets.all.size, BooruPresets.all.map { it.url }.toSet().size)
    }
}
