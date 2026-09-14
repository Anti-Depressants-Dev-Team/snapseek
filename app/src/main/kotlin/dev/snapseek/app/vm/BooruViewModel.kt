package dev.snapseek.app.vm

import dev.snapseek.core.booru.BooruClient
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.booru.TagBlacklist
import dev.snapseek.core.booru.TagCategory
import dev.snapseek.core.booru.TagSuggestion
import dev.snapseek.core.download.DownloadManager
import dev.snapseek.core.download.DownloadRequest
import dev.snapseek.core.model.DownloadQuality
import dev.snapseek.core.model.OutputFormat
import dev.snapseek.core.model.Service
import dev.snapseek.core.services.ServiceRepository
import dev.snapseek.core.settings.SettingsStore
import io.github.oshai.kotlinlogging.KotlinLogging
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

/** One native booru browsing session: a search, its pages of posts, and the post being looked at. */
class BooruViewModel(
    val service: Service,
    private val client: BooruClient,
    private val downloads: DownloadManager,
    private val settings: SettingsStore,
    private val services: ServiceRepository,
    parentScope: CoroutineScope,
) {
    data class State(
        val queryText: String = "",
        val activeQuery: String = "",
        val posts: List<BooruPost> = emptyList(),
        val loading: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
        val hiddenByBlacklist: Int = 0,
        val suggestions: List<TagSuggestion> = emptyList(),
        val selected: BooruPost? = null,
        val selectedCategories: Map<String, TagCategory> = emptyMap(),
        val showOriginal: Boolean = false,
    ) {
        val activeTags: List<String> get() = activeQuery.split(' ').filter { it.isNotBlank() }
        val selectedIndex: Int get() = selected?.let { s -> posts.indexOfFirst { it.id == s.id } } ?: -1
    }

    private val log = KotlinLogging.logger {}
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val history: StateFlow<List<String>> = settings.settings
        .map { it.searchHistory[service.id] ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, settings.current.searchHistory[service.id] ?: emptyList())

    private var page = 0
    private var loadJob: Job? = null
    private var suggestJob: Job? = null
    private var categoriesJob: Job? = null

    init {
        search("")
    }

    fun onQueryChanged(text: String) {
        _state.update { it.copy(queryText = text) }
        suggestJob?.cancel()
        val token = text.substringAfterLast(' ').trimStart('-', '~')
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

    fun acceptSuggestion(tag: TagSuggestion) {
        val text = _state.value.queryText
        val head = text.substringBeforeLast(' ', "")
        val lastToken = text.substringAfterLast(' ')
        val prefix = lastToken.takeWhile { it == '-' || it == '~' }
        val joined = listOf(head, prefix + tag.name).filter { it.isNotEmpty() }.joinToString(" ") + " "
        _state.update { it.copy(queryText = joined, suggestions = emptyList()) }
    }

    fun clearSuggestions() = _state.update { it.copy(suggestions = emptyList()) }

    fun search(query: String = _state.value.queryText) {
        val q = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct().joinToString(" ")
        loadJob?.cancel()
        page = 0
        _state.update { it.copy(queryText = if (q.isEmpty()) "" else "$q ", activeQuery = q, posts = emptyList(), endReached = false, error = null, hiddenByBlacklist = 0, suggestions = emptyList(), selected = null) }
        if (q.isNotEmpty()) services.rememberSearch(service.id, q)
        loadMore()
    }

    fun addTag(tag: String) = search((_state.value.activeTags + tag).joinToString(" "))

    fun removeTag(tag: String) = search(_state.value.activeTags.filterNot { it == tag }.joinToString(" "))

    fun clearHistory() = services.clearSearchHistory(service.id)

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached) return
        _state.update { it.copy(loading = true, error = null) }
        val query = s.activeQuery
        val requestedPage = page
        loadJob = scope.launch {
            try {
                val raw = client.posts(query, requestedPage, PAGE_SIZE)
                val blacklist = TagBlacklist.parse(settings.current.blacklist)
                val visible = blacklist.filter(raw) { it.tags }
                _state.update { cur ->
                    if (cur.activeQuery != query) return@update cur
                    val known = cur.posts.mapTo(HashSet()) { it.id }
                    cur.copy(
                        posts = cur.posts + visible.filterNot { it.id in known },
                        loading = false,
                        endReached = raw.size < PAGE_SIZE,
                        hiddenByBlacklist = cur.hiddenByBlacklist + (raw.size - visible.size),
                    )
                }
                page = requestedPage + 1
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn(e) { "Booru search failed for '$query' page $requestedPage" }
                _state.update { it.copy(loading = false, error = e.message ?: "Search failed") }
            }
        }
    }

    fun select(post: BooruPost) {
        _state.update { it.copy(selected = post, selectedCategories = emptyMap(), showOriginal = false) }
        categoriesJob?.cancel()
        categoriesJob = scope.launch {
            val cats = client.tagCategories(post)
            _state.update { s -> if (s.selected?.id == post.id) s.copy(selectedCategories = cats) else s }
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

    /** Tags of the selected post grouped in booru order; unknown categories go last. */
    fun groupedTags(post: BooruPost, categories: Map<String, TagCategory>): List<Pair<TagCategory, List<String>>> =
        post.tags.groupBy { categories[it] ?: TagCategory.UNKNOWN }
            .entries.sortedBy { it.key.order }
            .map { it.key to it.value.sorted() }

    fun save(post: BooruPost, format: OutputFormat? = null, quality: DownloadQuality? = null) {
        val q = quality ?: settings.current.booruQuality
        val useSample = q == DownloadQuality.SAMPLE && post.hasSample && !post.isVideo
        val cats = if (_state.value.selected?.id == post.id) _state.value.selectedCategories else emptyMap()
        downloads.enqueue(
            DownloadRequest(
                imageUrl = if (useSample) post.sampleUrl else post.fileUrl,
                pageUrl = client.postPageUrl(post),
                format = if (post.isVideo) OutputFormat.ORIGINAL else format,
                serviceId = service.id,
                metadata = metadataFor(post, cats),
                resolve = false,
                referer = service.url,
            ),
        )
    }

    private fun metadataFor(post: BooruPost, categories: Map<String, TagCategory>): Map<String, String> {
        val grouped = post.tags.groupBy { categories[it] ?: TagCategory.UNKNOWN }
        fun cat(c: TagCategory) = grouped[c]?.joinToString(" ") ?: ""
        return buildMap {
            put("id", post.id.toString())
            put("md5", post.md5)
            put("tags", post.tags.joinToString(" "))
            put("artist", cat(TagCategory.ARTIST))
            put("character", cat(TagCategory.CHARACTER))
            put("copyright", cat(TagCategory.COPYRIGHT))
            put("general", cat(TagCategory.GENERAL))
            put("meta", cat(TagCategory.META))
            put("rating", post.rating)
            put("score", post.score?.toString() ?: "")
            put("width", post.width.toString())
            put("height", post.height.toString())
            put("source", post.source ?: "")
            put("search", _state.value.activeQuery)
        }
    }

    fun dispose() = scope.cancel()

    private companion object {
        const val PAGE_SIZE = 40
    }
}
