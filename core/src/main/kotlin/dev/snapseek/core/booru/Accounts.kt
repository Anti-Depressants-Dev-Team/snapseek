package dev.snapseek.core.booru

/** Who the site thinks we are, when the embedded browser is logged in there. */
data class RemoteAccount(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

/** A place on the site a post can be saved to: a Pinterest board, a favourites list, a collection. */
data class RemoteCollection(
    val id: String,
    val name: String,
    val count: Int? = null,
    val thumbnailUrl: String? = null,
    val isPrivate: Boolean = false,
    val url: String? = null,
)

class AccountException(message: String) : RuntimeException(message)

/**
 * The answer to "is the browser logged in to this site?". [Waiting] and [Failed] are separate on purpose:
 * one means keep waiting for the user, the other means something is wrong and should be shown.
 */
sealed interface ConnectResult {
    data class Connected(val account: RemoteAccount) : ConnectResult

    /** The site answered as an anonymous visitor: nobody is logged in yet. */
    data object Waiting : ConnectResult

    /** The session exists but the profile couldn't be read, or the site wouldn't answer at all. */
    data class Failed(val reason: String) : ConnectResult
}

/**
 * Sites where a logged-in session unlocks more than browsing: a personal feed, the user's own collections,
 * and saving posts into them. Login itself happens in the website tab; the client reads that session.
 */
interface AccountCapable {
    /** Where to send the user to log in (in the website tab). */
    val loginUrl: String

    /** What this site calls a collection, lowercase singular: "board", "favorite"… */
    val collectionNoun: String get() = "collection"

    /** null when the browser session isn't logged in. Cached; [forceRefresh] re-reads the session. */
    suspend fun account(forceRefresh: Boolean = false): RemoteAccount?

    /**
     * Asks the site who we are right now, bypassing any cache, and says why when the answer isn't a user.
     * This is what the connect flow polls while the login page is open.
     */
    suspend fun connect(): ConnectResult =
        runCatching { account(forceRefresh = true) }
            .fold(
                onSuccess = { if (it != null) ConnectResult.Connected(it) else ConnectResult.Waiting },
                onFailure = { ConnectResult.Failed(it.message ?: it.toString()) },
            )

    /** Forgets a cached session so the next [account] call asks the site again. */
    fun forgetAccount() = Unit

    suspend fun collections(): List<RemoteCollection>

    suspend fun createCollection(name: String): RemoteCollection

    /** Saves the post into the collection on the site. Throws [AccountException] with a readable reason on failure. */
    suspend fun saveTo(post: BooruPost, collection: RemoteCollection)

    /** The query that browses a collection's contents in the grid, for example "board:12345". */
    fun feedQuery(collection: RemoteCollection): String
}
