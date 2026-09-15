package dev.snapseek.core.booru

import dev.snapseek.core.download.CookieSource
import dev.snapseek.core.model.ServiceKind
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Pinterest through the same internal "resource" endpoints its website uses (as gallery-dl does):
 *   GET  /resource/BaseSearchResource/get/     search           GET  /resource/UserHomefeedResource/get/  home feed (logged in)
 *   GET  /resource/BoardsResource/get/         my boards        GET  /resource/BoardFeedResource/get/     one board's pins
 *   POST /resource/RepinResource/create/       save a pin       POST /resource/BoardResource/create/      new board
 * Anonymous search needs no key. Everything personal reads the login the embedded browser holds: the session
 * cookies come from [cookies] and the CSRF header has to match the csrftoken cookie. Pages chain by bookmark token.
 */
class PinterestClient(
    override val baseUrl: String,
    private val cookies: CookieSource? = null,
    private val http: BooruHttp = BooruHttp.browserLike,
) : BooruClient, AccountCapable {
    private val log = KotlinLogging.logger {}
    private val root = baseUrl.trimEnd('/').let { if (it.contains("pinterest")) it else "https://www.pinterest.com" }
    private val anonymousCsrf = UUID.randomUUID().toString().replace("-", "")
    private val cursors = CursorCache()
    private val boardNames = ConcurrentHashMap<String, String>()
    private val boardUrls = ConcurrentHashMap<String, String>()

    @Volatile private var cachedAccount: RemoteAccount? = null
    @Volatile private var accountChecked = false

    override val kind = ServiceKind.PINTEREST
    override val maxPageSize = 25
    override val safeModeTags = emptyList<String>()
    override val supportsEmptyQuery = true
    override val emptyQueryHint = "Search Pinterest above. Connect your account to see your home feed here and save pins to your boards."
    override val searchPlaceholder = "Search Pinterest, e.g. watercolor landscape  ·  mine:yuno searches everything you saved"
    override val loginUrl = "$root/login/"
    override val savedQuery = MINE_PREFIX
    override val collectionNoun = "board"

    /** A Pinterest search is a phrase, not a tag list. */
    override fun splitQuery(query: String): List<String> = query.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()

    override fun displayTag(tag: String): String = when {
        tag.startsWith(BOARD_PREFIX) -> "Board: ${boardNames[tag.removePrefix(BOARD_PREFIX)] ?: tag.removePrefix(BOARD_PREFIX)}"
        tag == MINE_PREFIX.trim() || tag == MINE_PREFIX -> "Everything I saved"
        tag.startsWith(MINE_PREFIX) -> "Saved: ${tag.removePrefix(MINE_PREFIX).trim()}"
        else -> tag
    }

    override fun categoryLabel(category: TagCategory): String = when (category) {
        TagCategory.ARTIST -> "Pinner"
        TagCategory.COPYRIGHT -> "Board"
        TagCategory.GENERAL -> "Related"
        else -> category.label
    }

    override fun feedQuery(collection: RemoteCollection): String = BOARD_PREFIX + collection.id

    // ---- browsing --------------------------------------------------------------------------------------------

    override suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost> {
        val q = tags.trim()
        return when {
            q.isEmpty() -> homeFeed(page)
            q.startsWith(BOARD_PREFIX) -> boardFeed(q.removePrefix(BOARD_PREFIX), page)
            q.startsWith(MINE_PREFIX) -> myPins(q.removePrefix(MINE_PREFIX).trim(), page)
            else -> search(q, page)
        }
    }

    private suspend fun search(q: String, page: Int): List<BooruPost> {
        val bookmark = cursors.cursorFor("search|$q", page)
        if (bookmark == CursorCache.MISSING) return emptyList()
        val options = buildJsonObject {
            put("query", q)
            put("scope", "pins")
            put("rs", "typed")
            if (bookmark != null) put("bookmarks", buildJsonArray { add(bookmark) })
        }
        val sourceUrl = "/search/pins/?q=${q.urlEncoded()}&rs=typed"
        val (posts, next) = parseFeed(call("BaseSearchResource", sourceUrl, options))
        cursors.store("search|$q", page + 1, next)
        return posts
    }

    /**
     * Everything this account has saved, across every board at once, so finding an old pin doesn't mean opening
     * boards one by one. An empty term lists the profile's saved pins; a term searches inside them.
     */
    private suspend fun myPins(term: String, page: Int): List<BooruPost> {
        val me = account() ?: throw AccountException("Connect your Pinterest account to search what you saved.")
        val key = "mine|$term"
        val bookmark = cursors.cursorFor(key, page)
        if (bookmark == CursorCache.MISSING) return emptyList()
        val body = if (term.isEmpty()) {
            val options = buildJsonObject {
                put("username", me.username)
                put("field_set_key", "grid_item")
                put("is_own_profile_pins", true)
                if (bookmark != null) put("bookmarks", buildJsonArray { add(bookmark) })
            }
            call("UserPinsResource", "/${me.username}/_saved/", options, handler = "www/[username]/_saved.js")
        } else {
            val options = buildJsonObject {
                put("query", term)
                put("scope", "my_pins")
                put("rs", "typed")
                if (bookmark != null) put("bookmarks", buildJsonArray { add(bookmark) })
            }
            call("BaseSearchResource", "/search/my_pins/?q=${term.urlEncoded()}&rs=typed", options)
        }
        val (posts, next) = parseFeed(body)
        cursors.store(key, page + 1, next)
        log.info { "Pinterest saved pins page $page for \"$term\": ${posts.size} pins" }
        return posts
    }

    private suspend fun homeFeed(page: Int): List<BooruPost> {
        if (account() == null) return emptyList()
        val bookmark = cursors.cursorFor("home", page)
        if (bookmark == CursorCache.MISSING) return emptyList()
        val options = buildJsonObject {
            put("field_set_key", "hf_grid")
            put("in_nux", false)
            put("prependPartner", false)
            put("prependUserNews", false)
            put("static_feed", false)
            if (bookmark != null) put("bookmarks", buildJsonArray { add(bookmark) })
        }
        val (posts, next) = parseFeed(call("UserHomefeedResource", "/", options, handler = "www/index.js"))
        cursors.store("home", page + 1, next)
        log.info { "Pinterest home feed page $page: ${posts.size} pins" }
        return posts
    }

    private suspend fun boardFeed(boardId: String, page: Int): List<BooruPost> {
        val bookmark = cursors.cursorFor("board|$boardId", page)
        if (bookmark == CursorCache.MISSING) return emptyList()
        if (!boardUrls.containsKey(boardId)) runCatching { collections() }
        val boardUrl = boardUrls[boardId] ?: "/"
        val options = buildJsonObject {
            put("board_id", boardId)
            put("board_url", boardUrl)
            put("page_size", 25)
            put("prepend", false)
            if (bookmark != null) put("bookmarks", buildJsonArray { add(bookmark) })
        }
        val (posts, next) = parseFeed(call("BoardFeedResource", boardUrl, options, handler = "www/[username]/[slug].js"))
        cursors.store("board|$boardId", page + 1, next)
        return posts
    }

    override suspend fun suggest(prefix: String, limit: Int): List<TagSuggestion> {
        val term = prefix.trim()
        if (term.length < 2 || term.startsWith(BOARD_PREFIX)) return emptyList()
        val options = buildJsonObject {
            put("term", term)
            put("pin_scope", "pins")
            put("count", limit)
            put("enable_autocomplete_redirects", false)
            put("show_full_search_url", false)
        }
        return runCatching { parseSuggestions(call("AdvancedTypeaheadResource", "/search/pins/?q=${term.urlEncoded()}", options)).take(limit) }
            .getOrDefault(emptyList())
    }

    override fun postPageUrl(post: BooruPost): String = "$root/pin/${post.id}/"

    // ---- account ---------------------------------------------------------------------------------------------

    override suspend fun account(forceRefresh: Boolean): RemoteAccount? {
        if (accountChecked && !forceRefresh) return cachedAccount
        return when (val result = connect()) {
            is ConnectResult.Connected -> result.account
            is ConnectResult.Waiting -> null
            is ConnectResult.Failed -> null
        }
    }

    override fun forgetAccount() {
        cachedAccount = null
        accountChecked = false
    }

    /**
     * Asks Pinterest who we are. Every resource response carries `client_context`: `is_authenticated` says whether
     * the cookies we sent belong to somebody, and `client_context.user` is that somebody. A plain search is the
     * cheapest call that answers both, and it works anonymously too, so one request separates "nobody logged in yet"
     * from "logged in but Pinterest won't say who".
     */
    override suspend fun connect(): ConnectResult {
        val session = session()
        if (session.cookieCount == 0) {
            return ConnectResult.Waiting.also { log.info { "No Pinterest cookies in the browser yet" } }
        }
        val body = try {
            val options = buildJsonObject { put("query", "a"); put("scope", "pins"); put("rs", "typed") }
            call("BaseSearchResource", "/search/pins/?q=a", options)
        } catch (e: BooruHttpException) {
            log.warn { "Pinterest account probe failed: HTTP ${e.status}" }
            return ConnectResult.Failed("Pinterest answered HTTP ${e.status} when asked who is logged in")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "Pinterest account probe failed" }
            return ConnectResult.Failed(e.message ?: "couldn't reach Pinterest")
        }

        parseAccount(body)?.let { return finish(it) }

        val authenticated = (parseJson(body) as? JsonObject)?.obj("client_context")?.bool("is_authenticated") == true
        if (!authenticated) {
            cachedAccount = null
            accountChecked = true
            log.info { "Pinterest still sees an anonymous visitor (${session.cookieCount} browser cookies sent)" }
            return ConnectResult.Waiting
        }
        // Authenticated but the search response withheld the profile: read it off the home page instead.
        runCatching { parseAccountFromHtml(http.get("$root/", session.headers("/", "www/index.js"), accept = "text/html,*/*;q=0.8")) }
            .onFailure { log.debug(it) { "Home page account parse failed" } }
            .getOrNull()
            ?.let { return finish(it) }
        return ConnectResult.Failed("Pinterest says the session is signed in but wouldn't return the profile")
    }

    private fun finish(account: RemoteAccount): ConnectResult.Connected {
        cachedAccount = account
        accountChecked = true
        log.info { "Pinterest session belongs to @${account.username}" }
        return ConnectResult.Connected(account)
    }

    override suspend fun collections(): List<RemoteCollection> {
        val me = account() ?: throw AccountException("Not connected to Pinterest. Log in on the website tab first.")
        val sourceUrl = "/${me.username}/"
        val all = mutableListOf<RemoteCollection>()
        var bookmark: String? = null
        // Boards come 25 at a time whatever page_size says once you pass a bookmark, so walk the pages.
        for (page in 0 until 12) {
            val options = buildJsonObject {
                put("username", me.username)
                put("page_size", 100)
                put("privacy_filter", "all")
                put("sort", "last_pinned_to")
                put("field_set_key", "profile_grid_item")
                put("filter_stories", false)
                put("group_by", "visibility")
                put("include_archived", false)
                bookmark?.let { put("bookmarks", buildJsonArray { add(it) }) }
            }
            val body = call("BoardsResource", sourceUrl, options, handler = "www/[username].js")
            all += parseBoards(body)
            bookmark = nextBookmark(body) ?: break
        }
        val boards = all.distinctBy { it.id }
        boards.forEach { b ->
            boardNames[b.id] = b.name
            b.url?.let { boardUrls[b.id] = it }
        }
        log.info { "Pinterest: ${boards.size} boards for @${me.username}" }
        return boards
    }

    override suspend fun createCollection(name: String): RemoteCollection {
        val me = account() ?: throw AccountException("Not connected to Pinterest.")
        val options = buildJsonObject {
            put("name", name.trim())
            put("privacy", "public")
        }
        val body = postResource("BoardResource", "create", "/${me.username}/", options, handler = "www/[username].js")
        val data = (parseJson(body) as? JsonObject)?.obj("resource_response")?.obj("data")
            ?: throw AccountException(errorMessage(body) ?: "Pinterest didn't create the board")
        val board = boardFrom(data) ?: throw AccountException("Pinterest answered without a board id")
        boardNames[board.id] = board.name
        board.url?.let { boardUrls[board.id] = it }
        return board
    }

    override suspend fun saveTo(post: BooruPost, collection: RemoteCollection) {
        account() ?: throw AccountException("Not connected to Pinterest. Log in on the website tab first.")
        val options = buildJsonObject {
            put("pin_id", post.id.toString())
            put("board_id", collection.id)
            put("description", "")
            put("link", post.source ?: "")
            put("is_video", post.isVideo)
            put("image_signature", post.md5)
        }
        val body = try {
            postResource("RepinResource", "create", "/pin/${post.id}/", options, handler = "www/pin/[id].js")
        } catch (e: BooruHttpException) {
            throw AccountException(e.body?.let(::errorMessage) ?: "Pinterest refused the save (HTTP ${e.status})")
        }
        val response = (parseJson(body) as? JsonObject)?.obj("resource_response")
        val ok = response?.obj("data")?.str("id") != null || response?.str("status") == "success"
        if (!ok) throw AccountException(errorMessage(body) ?: "Pinterest refused the save")
    }

    // ---- plumbing --------------------------------------------------------------------------------------------

    private inner class Session(val cookie: String, val csrf: String, val cookieCount: Int) {
        fun headers(sourceUrl: String, handler: String): Map<String, String> = mapOf(
            "Accept" to "application/json, text/javascript, */*, q=0.01",
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to "$root$sourceUrl",
            "Origin" to root,
            "X-Requested-With" to "XMLHttpRequest",
            "X-APP-VERSION" to "a89153f",
            "X-CSRFToken" to csrf,
            "X-Pinterest-AppState" to "active",
            "X-Pinterest-Source-Url" to sourceUrl,
            "X-Pinterest-PWS-Handler" to handler,
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "Cookie" to cookie,
        )
    }

    private suspend fun session(): Session {
        val browserCookies = runCatching { cookies?.cookieHeaderFor("$root/") }
            .onFailure { log.debug(it) { "Couldn't read Pinterest cookies from the browser" } }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        val csrfFromBrowser = browserCookies?.let { CSRF_COOKIE.find(it)?.groupValues?.get(1) }
        val csrf = csrfFromBrowser ?: anonymousCsrf
        val cookie = when {
            browserCookies == null -> "csrftoken=$anonymousCsrf"
            csrfFromBrowser != null -> browserCookies
            else -> "$browserCookies; csrftoken=$anonymousCsrf"
        }
        val count = browserCookies?.split(';')?.count { it.isNotBlank() } ?: 0
        return Session(cookie, csrf, count)
    }


    private suspend fun call(resource: String, sourceUrl: String, options: JsonObject, handler: String = SEARCH_HANDLER): String {
        val data = buildJsonObject { put("options", options); put("context", buildJsonObject {}) }.toString()
        val url = "$root/resource/$resource/get/?source_url=${sourceUrl.urlEncoded()}&data=${data.urlEncoded()}"
        return http.get(url, session().headers(sourceUrl, handler))
    }

    private suspend fun postResource(
        resource: String,
        action: String,
        sourceUrl: String,
        options: JsonObject,
        handler: String = SEARCH_HANDLER,
    ): String {
        val data = buildJsonObject { put("options", options); put("context", buildJsonObject {}) }.toString()
        val form = "source_url=${sourceUrl.urlEncoded()}&data=${data.urlEncoded()}"
        return http.post("$root/resource/$resource/$action/", form, session().headers(sourceUrl, handler))
    }

    companion object {
        const val BOARD_PREFIX = "board:"

        /** Query prefix that searches the account's own saved pins instead of all of Pinterest. */
        const val MINE_PREFIX = "mine:"
        private const val SEARCH_HANDLER = "www/search/[scope].js"
        private val CSRF_COOKIE = Regex("""(?:^|;\s*)csrftoken=([^;]+)""")

        /** Returns the pins and the bookmark for the next page, or null when the feed is exhausted. */
        fun parseFeed(body: String): Pair<List<BooruPost>, String?> {
            val rootObj = parseJson(body) as? JsonObject ?: return emptyList<BooruPost>() to null
            val response = rootObj.obj("resource_response") ?: return emptyList<BooruPost>() to null
            val dataEl = response["data"]
            val results: JsonArray = when (dataEl) {
                is JsonArray -> dataEl
                is JsonObject -> dataEl.arr("results") ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            val posts = results.mapNotNull { (it as? JsonObject)?.let(::pinFrom) }
            return posts to nextBookmark(body)
        }

        /** The paging token Pinterest echoes back, or null when that feed has no more pages. */
        fun nextBookmark(body: String): String? {
            val rootObj = parseJson(body) as? JsonObject ?: return null
            val bookmark = rootObj.obj("resource")?.obj("options")?.arr("bookmarks")?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: rootObj.obj("resource_response")?.str("bookmark")
            return bookmark?.takeIf { it.isNotBlank() && it != "-end-" && !it.startsWith("Y2JOb25lO") }
        }

        @Deprecated("Renamed", ReplaceWith("parseFeed(body)"))
        fun parseSearch(body: String): Pair<List<BooruPost>, String?> = parseFeed(body)

        private fun pinFrom(o: JsonObject): BooruPost? {
            if (o.str("type")?.let { it != "pin" } == true) return null
            val id = o.long("id") ?: return null
            val images = o.obj("images") ?: return null
            val orig = images.obj("orig") ?: return null
            val fileUrl = orig.str("url") ?: return null
            fun render(key: String) = images.obj(key)?.str("url")
            val preview = render("236x") ?: render("170x") ?: fileUrl
            val sample = render("736x") ?: render("474x") ?: fileUrl

            val pinner = o.obj("pinner")?.str("username")?.takeIf { it.isNotBlank() }
            val board = o.obj("board")?.str("name")?.takeIf { it.isNotBlank() }
            val annotations = o.obj("pin_join")?.strList("visual_annotation").orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
            val categories = buildMap<String, TagCategory> {
                pinner?.let { put(it, TagCategory.ARTIST) }
                board?.let { put(it, TagCategory.COPYRIGHT) }
                annotations.forEach { put(it, TagCategory.GENERAL) }
            }
            val title = (o.str("grid_title") ?: o.str("title"))?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
                ?: o.str("description")?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
            val reactions = o.obj("reaction_counts")?.values?.sumOf { (it as? JsonPrimitive)?.intOrNull ?: 0 }
            return BooruPost(
                id = id,
                md5 = o.str("image_signature") ?: "",
                previewUrl = preview,
                sampleUrl = sample,
                fileUrl = fileUrl,
                width = orig.int("width") ?: 0,
                height = orig.int("height") ?: 0,
                tags = categories.keys.toList(),
                rating = "",
                score = reactions?.takeIf { it > 0 } ?: o.int("repin_count"),
                source = o.str("link")?.takeIf { it.isNotBlank() },
                owner = pinner,
                hasSample = sample != fileUrl,
                tagCategories = categories,
                title = title,
            )
        }

        /** AdvancedTypeahead returns {"resource_response":{"data":{"items":[{"label":…}]}}}. Shape is best-effort. */
        fun parseSuggestions(body: String): List<TagSuggestion> {
            val data = (parseJson(body) as? JsonObject)?.obj("resource_response")?.get("data")
            val items: JsonArray = when (data) {
                is JsonArray -> data
                is JsonObject -> data.arr("items") ?: JsonArray(emptyList())
                else -> return emptyList()
            }
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val label = (o.str("label") ?: o.str("query") ?: o.str("term"))?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                TagSuggestion(label, null, TagCategory.GENERAL)
            }.distinctBy { it.name.lowercase() }
        }

        /** Looks for the logged-in user in a resource response: client_context.user, then resource_response.data. */
        fun parseAccount(body: String): RemoteAccount? {
            val rootObj = parseJson(body) as? JsonObject ?: return null
            val candidates = listOfNotNull(
                rootObj.obj("client_context")?.obj("user"),
                rootObj.obj("resource_response")?.obj("data")?.obj("user"),
                rootObj.obj("resource_response")?.obj("data"),
            )
            return candidates.firstNotNullOfOrNull(::accountFrom)
        }

        /** The home page embeds the viewer inside a JSON script tag; find the first "user" object with a username. */
        fun parseAccountFromHtml(html: String): RemoteAccount? {
            val scripts = SCRIPT_JSON.findAll(html).map { it.groupValues[1] }.toList()
            for (script in scripts) {
                val json = runCatching { parseJson(script.unescapeXml()) }.getOrNull() ?: continue
                findUser(json)?.let { return it }
            }
            return null
        }

        private fun findUser(element: JsonElement, depth: Int = 0): RemoteAccount? {
            if (depth > 12) return null
            when (element) {
                is JsonObject -> {
                    element.obj("user")?.let(::accountFrom)?.let { return it }
                    element.obj("viewer")?.let(::accountFrom)?.let { return it }
                    for ((_, v) in element) findUser(v, depth + 1)?.let { return it }
                }
                is JsonArray -> for (v in element) findUser(v, depth + 1)?.let { return it }
                else -> Unit
            }
            return null
        }

        private fun accountFrom(o: JsonObject): RemoteAccount? {
            val username = o.str("username")?.takeIf { it.isNotBlank() } ?: return null
            val id = o.str("id") ?: username
            return RemoteAccount(
                id = id,
                username = username,
                displayName = o.str("full_name")?.takeIf { it.isNotBlank() },
                avatarUrl = o.str("image_medium_url") ?: o.str("image_large_url") ?: o.str("image_small_url"),
            )
        }

        fun parseBoards(body: String): List<RemoteCollection> {
            val response = (parseJson(body) as? JsonObject)?.obj("resource_response") ?: return emptyList()
            val items: JsonArray = when (val d = response["data"]) {
                is JsonArray -> d
                is JsonObject -> d.arr("boards") ?: d.arr("results") ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            return items.mapNotNull { (it as? JsonObject)?.let(::boardFrom) }
        }

        private fun boardFrom(o: JsonObject): RemoteCollection? {
            if (o.str("type")?.let { it != "board" } == true) return null
            val id = o.str("id") ?: return null
            val name = o.str("name")?.takeIf { it.isNotBlank() } ?: return null
            return RemoteCollection(
                id = id,
                name = name,
                count = o.int("pin_count"),
                thumbnailUrl = o.str("image_thumbnail_url") ?: o.str("image_cover_url") ?: o.obj("cover_pin")?.obj("images")?.obj("236x")?.str("url"),
                isPrivate = o.str("privacy")?.let { it != "public" } ?: false,
                url = o.str("url"),
            )
        }

        fun errorMessage(body: String): String? =
            (parseJson(body) as? JsonObject)?.obj("resource_response")?.obj("error")?.let { it.str("message") ?: it.str("message_detail") }

        private val SCRIPT_JSON = Regex("""<script[^>]*(?:id="__PWS_(?:INITIAL_PROPS|DATA)__"|type="application/json")[^>]*>(\{.*?})</script>""", RegexOption.DOT_MATCHES_ALL)
    }
}
