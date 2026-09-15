package dev.snapseek.app.vm

import dev.snapseek.core.booru.AccountCapable
import dev.snapseek.core.booru.BooruClient
import dev.snapseek.core.booru.BooruHttp
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.booru.MissingCredentialsException
import dev.snapseek.core.booru.RemoteAccount
import dev.snapseek.core.booru.RemoteCollection
import dev.snapseek.core.booru.TagBlacklist
import dev.snapseek.core.booru.TagCategory
import dev.snapseek.core.booru.TagSuggestion
import dev.snapseek.core.download.BulkDownloader
import dev.snapseek.core.download.DownloadManager
import dev.snapseek.core.download.DownloadRequest
import dev.snapseek.core.history.BookmarkRepository
import dev.snapseek.core.model.Bookmark
import dev.snapseek.core.model.DownloadQuality
import dev.snapseek.core.model.OutputFormat
import dev.snapseek.core.model.Service
import dev.snapseek.core.model.ServiceKind
import dev.snapseek.core.services.ServiceRepository
import dev.snapseek.core.settings.SettingsStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** One native browsing session on any site: a search, its pages of posts, the post being looked at, and what's selected. */
class BooruViewModel(
    val service: Service,
    val client: BooruClient,
    private val downloads: DownloadManager,
    private val bulk: BulkDownloader,
    private val bookmarks: BookmarkRepository,
    private val settings: SettingsStore,
    private val services: ServiceRepository,
    parentScope: CoroutineScope,
) {
    data class State(
        val queryText: String = "",
        val activeTags: List<String> = emptyList(),
        val posts: List<BooruPost> = emptyList(),
        val loading: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
        val hiddenByBlacklist: Int = 0,
        val suggestions: List<TagSuggestion> = emptyList(),
        val selected: BooruPost? = null,
        val selectedCategories: Map<String, TagCategory> = emptyMap(),
        val showOriginal: Boolean = false,
        val selection: Set<Long> = emptySet(),
        val safeMode: Boolean = false,
        /** True when the site only answers searches and none has been typed yet. */
        val needsQuery: Boolean = false,
    ) {
        val selectedIndex: Int get() = selected?.let { s -> posts.indexOfFirst { it.id == s.id } } ?: -1
        val selectedPosts: List<BooruPost> get() = posts.filter { it.id in selection }
    }

    private val log = KotlinLogging.logger {}
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val _state = MutableStateFlow(State(safeMode = settings.current.booruSafeMode && client.safeModeTags.isNotEmpty()))
    val state: StateFlow<State> = _state.asStateFlow()

    val history: StateFlow<List<String>> = settings.settings
        .map { it.searchHistory[service.id] ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, settings.current.searchHistory[service.id] ?: emptyList())

    /** Post ids of this service that are bookmarked. */
    val bookmarked: StateFlow<Set<Long>> = bookmarks.entries
        .map { list -> list.filter { it.serviceId == service.id }.mapTo(HashSet()) { it.postId } }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val bulkTasks: StateFlow<List<BulkDownloader.Entry>> = bulk.tasks
        .map { list -> list.filter { it.task.serviceId == service.id } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // ---- account (sites that can use the browser's login) ------------------------------------------------------

    val accountClient: AccountCapable? = client as? AccountCapable

    private val _account = MutableStateFlow<RemoteAccount?>(null)
    val account: StateFlow<RemoteAccount?> = _account.asStateFlow()

    private val _collections = MutableStateFlow<List<RemoteCollection>>(emptyList())
    val collections: StateFlow<List<RemoteCollection>> = _collections.asStateFlow()

    private val _lastCollection = MutableStateFlow<RemoteCollection?>(null)
    val lastCollection: StateFlow<RemoteCollection?> = _lastCollection.asStateFlow()

    private val _accountBusy = MutableStateFlow(false)
    val accountBusy: StateFlow<Boolean> = _accountBusy.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    /** Short-lived feedback ("Saved to Inspo"), clears itself after a few seconds. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun refreshAccount(force: Boolean = false) {
        val ac = accountClient ?: return
        scope.launch {
            _accountBusy.value = true
            val found = runCatching { ac.account(force) }.onFailure { log.warn(it) { "Account check failed on ${service.name}" } }.getOrNull()
            val wasConnected = _account.value != null
            _account.value = found
            _accountBusy.value = false
            if (found != null) {
                loadCollections()
                // The empty search is the personal feed once logged in; load it if the grid is still empty.
                val s = _state.value
                if (!wasConnected && s.activeTags.isEmpty() && s.posts.isEmpty() && !s.loading) search("")
            } else {
                _collections.value = emptyList()
            }
        }
    }

    fun loadCollections() {
        val ac = accountClient ?: return
        scope.launch {
            _collections.value = runCatching { ac.collections() }
                .onFailure { log.warn(it) { "Couldn't list ${ac.collectionNoun}s on ${service.name}" }; notice("Couldn't load your ${ac.collectionNoun}s: ${it.message}") }
                .getOrDefault(emptyList())
            if (_lastCollection.value == null) _lastCollection.value = _collections.value.firstOrNull()
        }
    }

    fun openCollection(collection: RemoteCollection) {
        accountClient?.let { search(it.feedQuery(collection)) }
    }

    fun saveToCollection(post: BooruPost, collection: RemoteCollection) = saveToCollection(listOf(post), collection)

    fun saveToCollection(posts: List<BooruPost>, collection: RemoteCollection) {
        val ac = accountClient ?: return
        if (posts.isEmpty()) return
        _lastCollection.value = collection
        scope.launch {
            var ok = 0
            var lastError: String? = null
            for (post in posts) {
                runCatching { ac.saveTo(post, collection) }
                    .onSuccess { ok++ }
                    .onFailure { lastError = it.message; log.warn(it) { "Save to ${collection.name} failed for ${post.id}" } }
            }
            notice(
                when {
                    ok == posts.size && ok == 1 -> "Saved to ${collection.name}"
                    ok == posts.size -> "Saved $ok pins to ${collection.name}"
                    ok == 0 -> "Couldn't save to ${collection.name}: ${lastError ?: "unknown error"}"
                    else -> "Saved $ok of ${posts.size} to ${collection.name}; last error: $lastError"
                },
            )
            if (ok > 0) loadCollections()
        }
    }

    fun saveSelectedToCollection(collection: RemoteCollection) {
        saveToCollection(_state.value.selectedPosts, collection)
        clearSelection()
    }

    /** Creates a collection and, when [thenSave] is given, saves that post into it right away. */
    fun createCollection(name: String, thenSave: BooruPost? = null) {
        val ac = accountClient ?: return
        if (name.isBlank()) return
        scope.launch {
            runCatching { ac.createCollection(name) }
                .onSuccess { created ->
                    _collections.update { listOf(created) + it }
                    _lastCollection.value = created
                    if (thenSave != null) saveToCollection(thenSave, created) else notice("Created ${ac.collectionNoun} ${created.name}")
                }
                .onFailure { notice("Couldn't create ${ac.collectionNoun}: ${it.message}") }
        }
    }

    fun displayTag(tag: String): String = client.displayTag(tag)

    private fun notice(text: String) {
        _notice.value = text
        scope.launch {
            delay(4500)
            if (_notice.value == text) _notice.value = null
        }
    }

    private val separator: String get() = client.tagSeparator
    private val activeQuery: String get() = client.joinQuery(_state.value.activeTags)
    private val effectiveQuery: String
        get() = client.joinQuery(_state.value.activeTags + if (_state.value.safeMode) client.safeModeTags else emptyList())

    private var page = 0
    private var loadJob: Job? = null
    private var suggestJob: Job? = null
    private var categoriesJob: Job? = null

    init {
        search("")
        refreshAccount()
    }

    // ---- searching -------------------------------------------------------------------------------------------

    fun onQueryChanged(text: String) {
        _state.update { it.copy(queryText = text) }
        suggestJob?.cancel()
        val token = lastToken(text).trimStart('-', '~')
        if (token.length < 2) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = scope.launch {
            delay(180)
            val results = runCatching { client.suggest(token) }.getOrDefault(emptyList())
            _state.update { s -> if (s.queryText == text) s.copy(suggestions = results) else s }
        }
    }

    private fun lastToken(text: String): String {
        val sep = separator.trim().ifEmpty { " " }
        return text.substringAfterLast(sep).trim()
    }

    fun acceptSuggestion(tag: TagSuggestion) {
        val text = _state.value.queryText
        val sep = separator.trim().ifEmpty { " " }
        val head = text.substringBeforeLast(sep, "").trimEnd()
        val prefix = lastToken(text).takeWhile { it == '-' || it == '~' }
        val joined = listOf(head, prefix + tag.name).filter { it.isNotEmpty() }.joinToString(separator) + separator
        _state.update { it.copy(queryText = joined, suggestions = emptyList()) }
    }

    fun clearSuggestions() = _state.update { it.copy(suggestions = emptyList()) }

    fun search(query: String = _state.value.queryText) {
        val tags = client.splitQuery(query).distinct()
        loadJob?.cancel()
        page = 0
        val needsQuery = tags.isEmpty() && !client.supportsEmptyQuery
        _state.update {
            it.copy(
                queryText = if (tags.isEmpty()) "" else client.joinQuery(tags) + separator,
                activeTags = tags,
                posts = emptyList(),
                endReached = needsQuery,
                error = null,
                hiddenByBlacklist = 0,
                suggestions = emptyList(),
                selected = null,
                selection = emptySet(),
                needsQuery = needsQuery,
            )
        }
        if (tags.isNotEmpty()) services.rememberSearch(service.id, client.joinQuery(tags))
        if (!needsQuery) loadMore()
    }

    fun addTag(tag: String) = search(client.joinQuery(_state.value.activeTags + tag))

    fun removeTag(tag: String) = search(client.joinQuery(_state.value.activeTags.filterNot { it == tag }))

    fun clearHistory() = services.clearSearchHistory(service.id)

    fun toggleSafeMode() {
        if (client.safeModeTags.isEmpty()) return
        val on = !_state.value.safeMode
        settings.update { it.copy(booruSafeMode = on) }
        _state.update { it.copy(safeMode = on) }
        search(activeQuery)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached) return
        _state.update { it.copy(loading = true, error = null) }
        val query = effectiveQuery
        val tagsAtStart = s.activeTags
        val requestedPage = page
        loadJob = scope.launch {
            try {
                val pageSize = PAGE_SIZE.coerceAtMost(client.maxPageSize)
                val raw = client.posts(query, requestedPage, pageSize)
                val blacklist = TagBlacklist.parse(settings.current.blacklist)
                val visible = blacklist.filter(raw) { it.tags }
                _state.update { cur ->
                    if (cur.activeTags != tagsAtStart) return@update cur
                    val known = cur.posts.mapTo(HashSet()) { it.id }
                    cur.copy(
                        posts = cur.posts + visible.filterNot { it.id in known },
                        loading = false,
                        // Sites disagree about page sizes (Pinterest varies, Wallhaven is fixed at 24), so only an empty page ends the feed.
                        endReached = raw.isEmpty(),
                        hiddenByBlacklist = cur.hiddenByBlacklist + (raw.size - visible.size),
                    )
                }
                page = requestedPage + 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn(e) { "Search failed on ${service.name} for '$query' page $requestedPage" }
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    private fun friendly(e: Exception): String {
        val msg = e.message ?: "Search failed"
        return when {
            e is MissingCredentialsException -> msg
            "HTTP 401" in msg || "HTTP 403" in msg -> "$msg. This site wants an account or a key: add them under Manage services."
            "HTTP 422" in msg && client.kind == ServiceKind.DANBOORU -> "Danbooru limits anonymous searches to two tags."
            "HTTP 429" in msg -> "The site is rate-limiting requests. Wait a moment and retry."
            else -> msg
        }
    }

    // ---- detail ----------------------------------------------------------------------------------------------

    fun select(post: BooruPost) {
        _state.update { it.copy(selected = post, selectedCategories = post.tagCategories ?: emptyMap(), showOriginal = false) }
        if (post.tagCategories != null && post.tags.isNotEmpty() && client.kind != ServiceKind.WALLHAVEN) return
        categoriesJob?.cancel()
        categoriesJob = scope.launch {
            val cats = runCatching { client.tagCategories(post) }.getOrDefault(emptyMap())
            _state.update { s -> if (s.selected?.id == post.id) s.copy(selectedCategories = (post.tagCategories ?: emptyMap()) + cats) else s }
        }
    }

    fun closeDetail() = _state.update { it.copy(selected = null) }
    fun next() = step(+1)
    fun previous() = step(-1)

    private fun step(delta: Int) {
        val s = _state.value
        val i = s.selectedIndex
        if (i < 0) return
        s.posts.getOrNull(i + delta)?.let { select(it) }
        if (i + delta >= s.posts.size - 5) loadMore()
    }

    fun toggleOriginal() = _state.update { it.copy(showOriginal = !it.showOriginal) }
    fun postPageUrl(post: BooruPost): String = client.postPageUrl(post)

    /** Tags of a post grouped in booru order; tags only known from the detail lookup are included. */
    fun groupedTags(post: BooruPost, categories: Map<String, TagCategory>): List<Pair<TagCategory, List<String>>> =
        (post.tags + categories.keys).distinct()
            .groupBy { categories[it] ?: TagCategory.UNKNOWN }
            .entries.sortedBy { it.key.order }
            .map { it.key to it.value.sorted() }

    // ---- selection -------------------------------------------------------------------------------------------

    fun toggleSelect(post: BooruPost) = _state.update { s ->
        s.copy(selection = if (post.id in s.selection) s.selection - post.id else s.selection + post.id)
    }

    fun selectAllLoaded() = _state.update { s -> s.copy(selection = s.posts.mapTo(HashSet()) { it.id }) }
    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun saveSelected(format: OutputFormat? = null, quality: DownloadQuality? = null) {
        _state.value.selectedPosts.forEach { save(it, format, quality) }
        clearSelection()
    }

    // ---- bookmarks -------------------------------------------------------------------------------------------

    fun isBookmarked(post: BooruPost): Boolean = post.id in bookmarked.value

    fun toggleBookmark(post: BooruPost) {
        scope.launch {
            if (isBookmarked(post)) {
                bookmarks.remove(service.id, post.id)
            } else {
                bookmarks.add(
                    Bookmark(
                        id = 0, serviceId = service.id, serviceName = service.name, postId = post.id, hash = post.md5,
                        previewUrl = post.previewUrl, sampleUrl = post.sampleUrl, fileUrl = post.fileUrl, pageUrl = client.postPageUrl(post),
                        width = post.width, height = post.height, tags = post.tags, rating = post.rating, source = post.source, createdAt = Instant.now(),
                    ),
                )
            }
        }
    }

    // ---- saving ----------------------------------------------------------------------------------------------

    fun save(post: BooruPost, format: OutputFormat? = null, quality: DownloadQuality? = null) {
        downloads.enqueue(requestFor(post, format, quality))
    }

    fun startBulk(maxPosts: Int, quality: DownloadQuality, subfolderBySearch: Boolean): String {
        val query = activeQuery.ifBlank { "everything" }
        val task = BulkDownloader.Task(
            serviceId = service.id,
            serviceName = service.name,
            query = effectiveQuery,
            maxPosts = maxPosts.coerceIn(1, 10_000),
            subfolder = if (subfolderBySearch) query.take(60) else null,
        )
        return bulk.start(task, client, TagBlacklist.parse(settings.current.blacklist)) { post -> requestFor(post, null, quality) }
    }

    fun cancelBulk(id: String) = bulk.cancel(id)
    fun dismissBulk(id: String) = bulk.dismiss(id)

    private fun requestFor(post: BooruPost, format: OutputFormat?, quality: DownloadQuality?): DownloadRequest {
        val q = quality ?: settings.current.booruQuality
        val useSample = q == DownloadQuality.SAMPLE && post.hasSample && !post.isVideo
        val cats = post.tagCategories ?: if (_state.value.selected?.id == post.id) _state.value.selectedCategories else emptyMap()
        return DownloadRequest(
            imageUrl = if (useSample) post.sampleUrl else post.fileUrl,
            pageUrl = client.postPageUrl(post),
            format = if (post.isVideo) OutputFormat.ORIGINAL else format,
            serviceId = service.id,
            serviceName = service.name,
            metadata = metadataFor(post, cats),
            resolve = client.resolveOriginals && !useSample,
            referer = service.websiteUrl,
            userAgent = if (client.kind in BROWSER_UA_SITES) BooruHttp.BROWSER_USER_AGENT else BooruHttp.APP_USER_AGENT,
            expectedMd5 = if (!useSample && post.md5.isNotBlank() && client.kind in MD5_SITES) post.md5 else null,
        )
    }

    private fun metadataFor(post: BooruPost, categories: Map<String, TagCategory>): Map<String, String> {
        val grouped = (post.tags + categories.keys).distinct().groupBy { categories[it] ?: TagCategory.UNKNOWN }
        fun cat(c: TagCategory) = grouped[c]?.joinToString(" ") { it.replace(' ', '_') } ?: ""
        return buildMap {
            put("id", post.displayId)
            put("md5", post.md5)
            put("title", post.title ?: "")
            put("tags", (post.tags + categories.keys).distinct().joinToString(" ") { it.replace(' ', '_') })
            put("artist", cat(TagCategory.ARTIST))
            put("character", cat(TagCategory.CHARACTER))
            put("copyright", cat(TagCategory.COPYRIGHT))
            put("general", cat(TagCategory.GENERAL))
            put("meta", cat(TagCategory.META))
            put("species", cat(TagCategory.SPECIES))
            put("lore", cat(TagCategory.LORE))
            put("rating", post.rating)
            put("score", post.score?.toString() ?: "")
            put("width", post.width.toString())
            put("height", post.height.toString())
            put("source", post.source ?: "")
            put("search", activeQuery)
        }
    }

    fun dispose() = scope.cancel()

    private companion object {
        const val PAGE_SIZE = 40
        val MD5_SITES = setOf(ServiceKind.GELBOORU_V2, ServiceKind.DANBOORU, ServiceKind.MOEBOORU, ServiceKind.E621, ServiceKind.PINTEREST, ServiceKind.ZEROCHAN)
        /** CDNs that expect a browser rather than an app: Pinterest, Pixiv, DeviantArt's wixmp, plain web pages. */
        val BROWSER_UA_SITES = setOf(ServiceKind.PINTEREST, ServiceKind.PIXIV, ServiceKind.DEVIANTART, ServiceKind.WEB_GRID, ServiceKind.GIPHY, ServiceKind.TENOR)
    }
}
