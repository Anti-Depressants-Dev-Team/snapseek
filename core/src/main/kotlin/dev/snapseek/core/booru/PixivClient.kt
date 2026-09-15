package dev.snapseek.core.booru

import dev.snapseek.core.download.CookieSource
import dev.snapseek.core.model.ServiceKind
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Pixiv through the website's AJAX API, which answers anonymously for all-ages work:
 *   search    /ajax/search/artworks/{word}?word=…&order=date_d&mode=all&p=…&s_mode=s_tag_full&type=all
 *   browse    /ranking.php?mode=daily&format=json&p=…            (used when the search is empty)
 *   suggest   /rpc/cps.php?keyword=…
 * Browser cookies from the embedded Chromium are sent along when the user is logged in there, which
 * unlocks R-18 results and the popularity sort. Originals need a Referer; the download pipeline adds it.
 *
 * Pseudo-tags: mode:safe|r18|all   order:date_d|date|popular_d   type:illust|manga|ugoira|all
 */
class PixivClient(
    override val baseUrl: String,
    private val cookies: CookieSource? = null,
    private val http: BooruHttp = BooruHttp.browserLike,
) : BooruClient, AccountCapable {
    private val log = KotlinLogging.logger {}
    private val root = "https://www.pixiv.net"

    @Volatile private var cachedAccount: RemoteAccount? = null
    @Volatile private var csrfToken: String? = null
    @Volatile private var accountChecked = false

    override val kind = ServiceKind.PIXIV
    override val maxPageSize = 60
    override val safeModeTags = listOf("mode:safe")
    override val resolveOriginals = true
    override val searchPlaceholder = "Search Pixiv tags, e.g. 風景 or landscape  ·  mode:safe  ·  mine: for your bookmarks"
    override val supportsEmptyQuery = true
    override val emptyQueryHint = "Search Pixiv above, or connect your account to see the newest work from artists you follow."
    override val loginUrl = "https://accounts.pixiv.net/login?return_to=https%3A%2F%2Fwww.pixiv.net%2F"
    override val collectionNoun = "bookmark tag"
    override val savedQuery = MINE_PREFIX

    override fun displayTag(tag: String): String = when {
        tag == MINE_PREFIX || tag == MINE_PREFIX.trimEnd(':') -> "Everything I bookmarked"
        tag.startsWith(MINE_PREFIX) -> "Bookmarked: ${tag.removePrefix(MINE_PREFIX).trim()}"
        else -> tag
    }

    override fun feedQuery(collection: RemoteCollection): String =
        if (collection.id == ALL_BOOKMARKS) MINE_PREFIX else MINE_PREFIX + collection.name

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val trimmed = tags.trim()
        if (trimmed.startsWith(MINE_PREFIX)) return bookmarks(trimmed.removePrefix(MINE_PREFIX).trim(), page)
        val (words, params) = extractParams(tags, setOf("mode", "order", "type", "s_mode"))
        val word = words.joinToString(" ")
        val headers = headers()
        if (word.isEmpty()) {
            // Logged in, the empty grid is the personal feed; an explicit mode: asks for a ranking instead.
            if (params["mode"] == null && account() != null) return followFeed(page)
            val mode = params["mode"]?.takeIf { it in RANKING_MODES } ?: "daily"
            if (page >= 10) return emptyList()   // the ranking has 500 entries
            return parseRanking(http.get("$root/ranking.php?mode=$mode&format=json&p=${page + 1}", headers))
        }
        val url = "$root/ajax/search/artworks/${word.urlEncoded()}?word=${word.urlEncoded()}" +
            "&order=${params["order"] ?: "date_d"}&mode=${params["mode"] ?: "all"}&p=${page + 1}" +
            "&s_mode=${params["s_mode"] ?: "s_tag_full"}&type=${params["type"] ?: "all"}&lang=en"
        return parseSearch(http.get(url, headers))
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim()
        if (term.isEmpty()) return emptyList()
        return runCatching { parseSuggestions(http.get("$root/rpc/cps.php?keyword=${term.urlEncoded()}&lang=en", headers())).take(limit) }
            .getOrDefault(emptyList())
    }

    override fun postPageUrl(post: BooruPost): String = "$root/artworks/${post.id}"

    private suspend fun headers(): Map<String, String> = buildMap {
        put("Referer", "$root/")
        put("Accept", "application/json")
        cookies?.cookieHeaderFor("$root/")?.let { put("Cookie", it) }
    }

    // ---- the account the embedded browser is logged in as ------------------------------------------------------

    /** New work from the artists this account follows: Pixiv's own "Followed" page, which is its personal feed. */
    private suspend fun followFeed(page: Int): List<BooruPost> {
        val posts = parseFollowFeed(http.get("$root/ajax/follow_latest/illust?p=${page + 1}&mode=all&lang=en", headers()))
        log.info { "Pixiv follow feed page $page: ${posts.size} works" }
        return posts
    }

    /**
     * This account's bookmarks, all of them or just one bookmark tag, so an old save can be found without
     * walking every tag. Pixiv pages these by offset.
     */
    private suspend fun bookmarks(tag: String, page: Int): List<BooruPost> {
        val me = account() ?: throw AccountException("Connect your Pixiv account to see what you bookmarked.")
        val url = "$root/ajax/user/${me.id}/illusts/bookmarks?tag=${tag.urlEncoded()}" +
            "&offset=${page * BOOKMARK_PAGE}&limit=$BOOKMARK_PAGE&rest=show&lang=en"
        val posts = parseBookmarks(http.get(url, headers()))
        log.info { "Pixiv bookmarks page $page for \"$tag\": ${posts.size} works" }
        return posts
    }

    override suspend fun account(forceRefresh: Boolean): RemoteAccount? {
        if (accountChecked && !forceRefresh) return cachedAccount
        return (connect() as? ConnectResult.Connected)?.account
    }

    override fun forgetAccount() {
        cachedAccount = null
        csrfToken = null
        accountChecked = false
    }

    /**
     * Pixiv puts the viewer and a CSRF token in a `global-data` meta tag on every page it serves to a signed-in
     * browser, and serves signed-out visitors a landing page without it. So one page fetch answers both "who is
     * this?" and "what token do writes need?".
     */
    override suspend fun connect(): ConnectResult {
        val cookie = runCatching { cookies?.cookieHeaderFor("$root/") }.getOrNull()?.takeIf { it.isNotBlank() }
        if (cookie == null) {
            log.info { "No Pixiv cookies in the browser yet" }
            return ConnectResult.Waiting
        }
        val html = try {
            http.get("$root/", headers() + ("Accept" to "text/html,*/*;q=0.8"), accept = "text/html,*/*;q=0.8")
        } catch (e: BooruHttpException) {
            log.warn { "Pixiv account probe failed: HTTP ${e.status}" }
            return ConnectResult.Failed("Pixiv answered HTTP ${e.status} when asked who is logged in")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "Pixiv account probe failed" }
            return ConnectResult.Failed(e.message ?: "couldn't reach Pixiv")
        }
        csrfFrom(html)?.let { csrfToken = it }
        val account = accountFromPage(html) ?: fromSessionCookie(cookie)
        if (account == null) {
            log.info { "Pixiv is still serving the signed-out page (${cookie.split(';').size} browser cookies sent)" }
            return ConnectResult.Waiting
        }
        cachedAccount = account
        accountChecked = true
        log.info { "Pixiv session belongs to ${account.username} (id ${account.id})" }
        return ConnectResult.Connected(account)
    }

    /**
     * The token every write needs. It rides along on any signed-in page, so if the connect probe didn't catch one
     * (the page layout changes now and then) fetching a page again is enough, rather than asking for a reconnect.
     */
    private suspend fun csrf(): String? {
        csrfToken?.let { return it }
        val found = runCatching {
            csrfFrom(http.get("$root/", headers() + ("Accept" to "text/html,*/*;q=0.8"), accept = "text/html,*/*;q=0.8"))
        }.onFailure { log.warn(it) { "Couldn't fetch a Pixiv page for the token" } }.getOrNull()
        if (found == null) log.warn { "No CSRF token on the Pixiv page; is the browser still signed in?" }
        csrfToken = found
        return found
    }

    /**
     * A logged-in Pixiv session cookie starts with the account's own id, so even if the page stops carrying the
     * viewer we can still say who this is and read their bookmarks. The public profile endpoint fills in the name.
     */
    private suspend fun fromSessionCookie(cookie: String): RemoteAccount? {
        val id = userIdFromCookie(cookie) ?: return null
        val profile = runCatching { http.get("$root/ajax/user/$id?full=0&lang=en", headers()) }
            .onFailure { log.debug(it) { "Pixiv profile lookup for $id failed" } }
            .getOrNull()
        val body = profile?.let { (parseJson(it) as? JsonObject)?.obj("body") }
        return RemoteAccount(
            id = id,
            username = body?.str("name")?.takeIf { it.isNotBlank() } ?: "pixiv user $id",
            avatarUrl = body?.str("imageBig") ?: body?.str("image"),
        )
    }

    /**
     * Pixiv has no folders: a bookmark carries tags, and the bookmarks page lists them down the side. Those tags
     * are the collections here, with "All bookmarks" standing for saving without any tag.
     */
    override suspend fun collections(): List<RemoteCollection> {
        val me = account() ?: throw AccountException("Connect your Pixiv account first.")
        val body = runCatching { http.get("$root/ajax/user/${me.id}/illusts/bookmark/tags?lang=en", headers()) }
            .onFailure { log.warn(it) { "Couldn't read Pixiv bookmark tags" } }
            .getOrNull()
        val tags = body?.let { parseBookmarkTags(it) }.orEmpty()
        log.info { "Pixiv: ${tags.size} bookmark tags for ${me.username}" }
        return listOf(RemoteCollection(ALL_BOOKMARKS, "All bookmarks")) + tags
    }

    /** Pixiv creates a bookmark tag the first time something is saved under it, so this one is local until then. */
    override suspend fun createCollection(name: String): RemoteCollection {
        account() ?: throw AccountException("Connect your Pixiv account first.")
        val clean = name.trim()
        if (clean.isEmpty()) throw AccountException("A bookmark tag needs a name")
        return RemoteCollection(clean, clean, count = 0)
    }

    override suspend fun saveTo(post: BooruPost, collection: RemoteCollection) {
        account() ?: throw AccountException("Connect your Pixiv account first.")
        val token = csrf() ?: throw AccountException("Pixiv wouldn't hand over the token a bookmark needs. Open Pixiv's website once and try again.")
        val payload = buildJsonObject {
            put("illust_id", post.id.toString())
            put("restrict", if (collection.isPrivate) 1 else 0)
            put("comment", "")
            put("tags", buildJsonArray { if (collection.id != ALL_BOOKMARKS) add(collection.name) })
        }.toString()
        val headers = headers() + mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "x-csrf-token" to token,
            "Referer" to "$root/artworks/${post.id}",
            "Origin" to root,
            "Accept" to "application/json",
        )
        val body = try {
            http.post("$root/ajax/illusts/bookmarks/add", payload, headers)
        } catch (e: BooruHttpException) {
            throw AccountException(e.body?.let(::errorMessage) ?: "Pixiv refused the bookmark (HTTP ${e.status})")
        }
        errorMessage(body)?.let { throw AccountException(it) }
    }

    companion object {
        private val RANKING_MODES = setOf("daily", "weekly", "monthly", "rookie", "original", "male", "female", "daily_r18", "weekly_r18")

        /** Query prefix for this account's own bookmarks; an optional bookmark tag follows it. */
        const val MINE_PREFIX = "mine:"

        /** The collection id standing for "bookmark it, don't tag it". */
        const val ALL_BOOKMARKS = "*"

        private const val BOOKMARK_PAGE = 48

        private val SESSION_COOKIE = Regex("""(?:^|;\s*)PHPSESSID=(\d+)_""")

        /** The account id Pixiv puts at the front of a signed-in session cookie, or null when signed out. */
        fun userIdFromCookie(cookie: String): String? = SESSION_COOKIE.find(cookie)?.groupValues?.get(1)

        private val GLOBAL_DATA = Regex("""<meta[^>]*id="meta-global-data"[^>]*content="([^"]*)"""")

        private const val SELF_KEY = "\"self\":"

        private val CSRF_TOKEN = Regex("""\\*"token\\*":\\*"([0-9a-f]{16,64})""")

        /** The JSON Pixiv embeds for a signed-in browser: the viewer, the CSRF token, premium flags. */
        fun globalData(html: String): JsonObject? {
            val raw = GLOBAL_DATA.find(html)?.groupValues?.get(1) ?: return null
            return parseJson(raw.unescapeHtml()) as? JsonObject
        }

        /**
         * The token writes are signed with. Pixiv used to hand it over in a meta tag and now buries it in the
         * page's data payload, escaped once or twice depending on the page, so match it wherever it turns up.
         */
        fun csrfFrom(html: String): String? = CSRF_TOKEN.find(html)?.groupValues?.get(1)

        /**
         * Who the page belongs to. The viewer sits under `userData`, these days one level further down in `self`,
         * and the payload may be escaped, so unescape first and read the innermost object that has an id.
         */
        fun accountFromPage(html: String): RemoteAccount? {
            globalData(html)?.let { data -> accountFrom(data.obj("userData")?.obj("self") ?: data.obj("userData")) }?.let { return it }
            val plain = html.replace("\\\"", "\"")
            var from = 0
            while (true) {
                val start = plain.indexOf(SELF_KEY, from).takeIf { it >= 0 } ?: return null
                val brace = plain.indexOf('{', start + SELF_KEY.length - 1).takeIf { it >= 0 } ?: return null
                val obj = balancedObject(plain, brace)?.let { parseJson(it) as? JsonObject }
                accountFrom(obj)?.let { return it }
                from = start + SELF_KEY.length
            }
        }

        fun accountFrom(user: JsonObject?): RemoteAccount? {
            val id = user?.str("id")?.takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: return null
            val name = user.str("name")?.takeIf { it.isNotBlank() } ?: user.str("pixivId")?.takeIf { it.isNotBlank() } ?: id
            return RemoteAccount(
                id = id,
                username = name,
                displayName = user.str("pixivId")?.takeIf { it.isNotBlank() && it != name },
                avatarUrl = user.str("profileImgBig") ?: user.str("profileImg"),
            )
        }

        /** The `{…}` starting at [open], respecting nesting and quotes, or null when it never closes. */
        private fun balancedObject(text: String, open: Int): String? {
            var depth = 0
            var inString = false
            var escaped = false
            for (i in open until minOf(text.length, open + 20_000)) {
                val c = text[i]
                when {
                    escaped -> escaped = false
                    c == '\\' && inString -> escaped = true
                    c == '"' -> inString = !inString
                    inString -> Unit
                    c == '{' -> depth++
                    c == '}' -> {
                        depth--
                        if (depth == 0) return text.substring(open, i + 1)
                    }
                }
            }
            return null
        }

        /** {"body":{"public":[{"tag":"景色","cnt":12}],"private":[…]}} — private tags come back marked secret. */
        fun parseBookmarkTags(body: String): List<RemoteCollection> {
            val root = (parseJson(body) as? JsonObject)?.obj("body") ?: return emptyList()
            fun read(key: String, private: Boolean): List<RemoteCollection> {
                val entries = when (val node = root[key]) {
                    is JsonArray -> node.mapNotNull { it as? JsonObject }
                    is JsonObject -> node.values.mapNotNull { it as? JsonObject }
                    else -> emptyList()
                }
                return entries.mapNotNull { o ->
                    val tag = o.str("tag")?.takeIf { it.isNotBlank() && it != "未分類" } ?: return@mapNotNull null
                    RemoteCollection(id = tag, name = tag, count = o.int("cnt"), isPrivate = private)
                }
            }
            return (read("public", false) + read("private", true)).distinctBy { it.id }
        }

        /** {"body":{"works":[…]}} — the same work shape the search returns, plus bookmark state. */
        fun parseBookmarks(body: String): List<BooruPost> =
            worksFrom((parseJson(body) as? JsonObject)?.obj("body")?.arr("works"))

        /** {"body":{"page":{"ids":[…]},"thumbnails":{"illust":[…]}}} — newest work from artists you follow. */
        fun parseFollowFeed(body: String): List<BooruPost> {
            val root = (parseJson(body) as? JsonObject)?.obj("body") ?: return emptyList()
            val works = worksFrom(root.obj("thumbnails")?.arr("illust"))
            val order = root.obj("page")?.arr("ids")?.mapNotNull { (it as? JsonPrimitive)?.let { p -> p.longOrNull ?: p.contentOrNull?.toLongOrNull() } }
            if (order.isNullOrEmpty()) return works
            val byId = works.associateBy { it.id }
            return order.mapNotNull { byId[it] } + works.filter { it.id !in order.toSet() }
        }

        private fun worksFrom(items: JsonArray?): List<BooruPost> = items.orEmpty().mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val id = o.long("id") ?: return@mapNotNull null
            // Hidden or deleted works come back as a stub with no image.
            val thumb = o.str("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            post(
                id = id, thumb = thumb, title = o.str("title"), user = o.str("userName")?.takeIf { it.isNotBlank() },
                tags = o.strList("tags"), width = o.int("width") ?: 0, height = o.int("height") ?: 0,
                pages = o.int("pageCount") ?: 1, xRestrict = o.int("xRestrict") ?: 0, illustType = o.int("illustType") ?: 0,
                score = null,
            )
        }

        /** Pixiv answers {"error":true,"message":"…"} with HTTP 200 as often as not. */
        fun errorMessage(body: String): String? {
            val o = parseJson(body) as? JsonObject ?: return null
            if (o.bool("error") != true) return null
            return o.str("message")?.takeIf { it.isNotBlank() } ?: "Pixiv refused the request"
        }

        private fun String.unescapeHtml(): String = replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

        /** …/c/250x250_80_a2/img-master/img/…/ID_p0_square1200.jpg → …/img-master/img/…/ID_p0_master1200.jpg */
        private val THUMB = Regex("""^(https://i\.pximg\.net)/c/[^/]+/(img-master/img/.+?/\d+_p\d+)_(?:square|master|custom)\d+\.\w+$""")
        private val MASTER = Regex("""^(https://i\.pximg\.net)/(img-master/img/.+?/\d+_p\d+)_master\d+\.\w+$""")

        fun masterOf(url: String): String =
            THUMB.matchEntire(url)?.let { "${it.groupValues[1]}/${it.groupValues[2]}_master1200.jpg" }
                ?: MASTER.matchEntire(url)?.let { "${it.groupValues[1]}/${it.groupValues[2]}_master1200.jpg" }
                ?: url

        fun parseSearch(body: String): List<BooruPost> {
            val data = (parseJson(body) as? JsonObject)?.obj("body")?.obj("illustManga")?.arr("data") ?: return emptyList()
            return data.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.long("id") ?: return@mapNotNull null
                val thumb = o.str("url") ?: return@mapNotNull null
                val user = o.str("userName")?.takeIf { it.isNotBlank() }
                val tags = o.strList("tags")
                post(
                    id = id, thumb = thumb, title = o.str("title"), user = user, tags = tags,
                    width = o.int("width") ?: 0, height = o.int("height") ?: 0,
                    pages = o.int("pageCount") ?: 1, xRestrict = o.int("xRestrict") ?: 0, illustType = o.int("illustType") ?: 0,
                    score = null,
                )
            }
        }

        fun parseRanking(body: String): List<BooruPost> {
            val contents = (parseJson(body) as? JsonObject)?.arr("contents") ?: return emptyList()
            return contents.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val id = o.long("illust_id") ?: return@mapNotNull null
                val thumb = o.str("url") ?: return@mapNotNull null
                post(
                    id = id, thumb = thumb, title = o.str("title"), user = o.str("user_name"), tags = o.strList("tags"),
                    width = o.int("width") ?: 0, height = o.int("height") ?: 0,
                    pages = o.int("illust_page_count") ?: 1, xRestrict = 0, illustType = o.int("illust_type") ?: 0,
                    score = o.int("rating_count"),
                )
            }
        }

        private fun post(id: Long, thumb: String, title: String?, user: String?, tags: List<String>, width: Int, height: Int, pages: Int, xRestrict: Int, illustType: Int, score: Int?): BooruPost {
            val master = masterOf(thumb)
            val categories = buildMap<String, TagCategory> {
                user?.let { put(it, TagCategory.ARTIST) }
                tags.forEach { put(it, TagCategory.GENERAL) }
                if (pages > 1) put("${pages}_pages", TagCategory.META)
                if (illustType == 1) put("manga", TagCategory.META)
                if (illustType == 2) put("ugoira", TagCategory.META)
            }
            return BooruPost(
                id = id,
                md5 = "",
                previewUrl = thumb,
                sampleUrl = master,
                fileUrl = master,
                width = width,
                height = height,
                tags = categories.keys.toList(),
                rating = when (xRestrict) { 0 -> "general"; else -> "explicit" },
                score = score,
                source = null,
                owner = user,
                hasSample = false,
                tagCategories = categories,
                title = title?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        fun parseSuggestions(body: String): List<TagSuggestion> {
            val candidates = (parseJson(body) as? JsonObject)?.arr("candidates") ?: return emptyList()
            return candidates.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val name = o.str("tag_name") ?: return@mapNotNull null
                TagSuggestion(name, o.int("access_count"), TagCategory.GENERAL)
            }
        }

        @Suppress("unused")
        private fun JsonArray.size2() = size
    }
}
