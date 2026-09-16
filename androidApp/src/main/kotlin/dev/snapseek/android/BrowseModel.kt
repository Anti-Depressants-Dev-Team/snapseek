package dev.snapseek.android

import dev.snapseek.core.booru.AccountCapable
import dev.snapseek.core.booru.BooruClient
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.booru.ConnectResult
import dev.snapseek.core.booru.MissingCredentialsException
import dev.snapseek.core.booru.RemoteAccount
import dev.snapseek.core.booru.RemoteCollection
import dev.snapseek.core.booru.TagBlacklist
import dev.snapseek.core.model.OutputFormat
import dev.snapseek.core.model.Service
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One site being browsed on the phone: the search, its pages, what is open, and the account behind it. */
class BrowseModel(
    val service: Service,
    val client: BooruClient,
    private val graph: AndroidGraph,
) {
    data class State(
        val query: String = "",
        val activeTags: List<String> = emptyList(),
        val posts: List<BooruPost> = emptyList(),
        val loading: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
        val needsQuery: Boolean = false,
        val open: BooruPost? = null,
        val notice: String? = null,
        val account: RemoteAccount? = null,
        val collections: List<RemoteCollection> = emptyList(),
    )


    private val scope = CoroutineScope(graph.scope.coroutineContext + SupervisorJob(graph.scope.coroutineContext[Job]))

    /** Leaving the screen stops whatever this session had in flight. */
    fun dispose() = scope.cancel()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val accountClient: AccountCapable? = client as? AccountCapable

    private var page = 0
    private var loadJob: Job? = null
    private var searched = false

    init {
        if (accountClient != null) refreshAccount() else search("")
    }

    fun onQueryChanged(text: String) = _state.update { it.copy(query = text) }

    fun search(query: String = _state.value.query) {
        val tags = client.splitQuery(query).distinct()
        searched = true
        loadJob?.cancel()
        page = 0
        val needsQuery = tags.isEmpty() && !client.supportsEmptyQuery
        _state.update {
            it.copy(
                query = if (tags.isEmpty()) "" else client.joinQuery(tags),
                activeTags = tags,
                posts = emptyList(),
                endReached = needsQuery,
                error = null,
                needsQuery = needsQuery,
                open = null,
            )
        }
        if (tags.isNotEmpty()) graph.services.rememberSearch(service.id, client.joinQuery(tags))
        if (!needsQuery) loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.endReached) return
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch {
            val blacklist = TagBlacklist.parse(graph.settings.current.blacklist)
            val result = runCatching { client.posts(client.joinQuery(current.activeTags), page, client.maxPageSize) }
            result.onSuccess { raw ->
                val known = current.posts.mapTo(HashSet()) { it.id }
                val fresh = raw.filter { it.id !in known && !blacklist.hides(it.tags) }
                page++
                // The grid is keyed by post id and the same key twice is a crash, so the list is kept unique no
                // matter what the site sends: a feed handing the same post back inside one page is ordinary.
                _state.update { s ->
                    s.copy(posts = (s.posts + fresh).distinctBy { it.id }, loading = false, endReached = raw.isEmpty())
                }
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                Log.w(TAG, "Loading " + service.name + " failed", failure)
                val message = when (failure) {
                    is MissingCredentialsException -> failure.message
                    else -> failure.message ?: "Couldn't reach ${service.name}"
                }
                _state.update { it.copy(loading = false, error = message) }
            }
        }
    }

    fun open(post: BooruPost?) = _state.update { it.copy(open = post) }

    fun save(post: BooruPost, format: OutputFormat = graph.settings.current.defaultFormat) {
        scope.launch {
            notice("Saving…")
            runCatching {
                graph.saver.save(
                    post = post,
                    service = service,
                    format = format,
                    useOriginal = true,
                    userAgent = dev.snapseek.core.booru.BooruHttp.BROWSER_USER_AGENT,
                )
            }.onSuccess { notice("Saved to ${it.location}") }
                .onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Saving post " + post.id + " failed", it)
                    notice("Couldn't save: ${it.message}")
                }
        }
    }

    // ---- account -----------------------------------------------------------------------------------------

    fun refreshAccount(force: Boolean = false) {
        val ac = accountClient ?: return
        scope.launch {
            if (force) ac.forgetAccount()
            val result = runCatching { if (force) ac.connect() else ac.account()?.let { ConnectResult.Connected(it) } ?: ConnectResult.Waiting }
                .getOrElse { failure ->
                    if (failure is CancellationException) throw failure
                    ConnectResult.Failed(failure.message ?: "couldn't reach ${service.name}")
                }
            apply(result)
        }
    }

    fun apply(result: ConnectResult) {
        when (result) {
            is ConnectResult.Connected -> {
                val fresh = _state.value.account?.id != result.account.id
                _state.update { it.copy(account = result.account) }
                if (fresh) {
                    notice("Connected as ${result.account.username}")
                    loadCollections()
                    if (_state.value.activeTags.isEmpty()) search("")
                }
            }
            is ConnectResult.Waiting -> {
                _state.update { it.copy(account = null, collections = emptyList()) }
                if (!searched) search("")
            }
            is ConnectResult.Failed -> {
                _state.update { it.copy(account = null, collections = emptyList()) }
                notice(result.reason)
                if (!searched) search("")
            }
        }
    }

    /** Called when the login screen closes: the cookie jar may have gained a session while it was open. */
    fun onReturnedFromLogin() = refreshAccount(force = true)

    private fun loadCollections() {
        val ac = accountClient ?: return
        scope.launch {
            val found = runCatching { ac.collections() }.getOrElse {
                if (it is CancellationException) throw it
                emptyList()
            }
            _state.update { it.copy(collections = found) }
        }
    }

    fun openCollection(collection: RemoteCollection) {
        accountClient?.let { search(it.feedQuery(collection)) }
    }

    fun saveToCollection(post: BooruPost, collection: RemoteCollection) {
        val ac = accountClient ?: return
        scope.launch {
            runCatching { ac.saveTo(post, collection) }
                .onSuccess { notice("Saved to ${collection.name}") }
                .onFailure {
                    if (it is CancellationException) throw it
                    notice("Couldn't save to ${collection.name}: ${it.message}")
                }
        }
    }

    fun displayTag(tag: String): String = client.displayTag(tag)

    private companion object {
        const val TAG = "SnapSeek"
    }

    private fun notice(text: String) {
        _state.update { it.copy(notice = text) }
        scope.launch {
            delay(3500)
            _state.update { if (it.notice == text) it.copy(notice = null) else it }
        }
    }
}
