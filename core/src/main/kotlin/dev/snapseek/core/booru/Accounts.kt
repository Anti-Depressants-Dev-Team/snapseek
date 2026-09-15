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

    suspend fun collections(): List<RemoteCollection>

    suspend fun createCollection(name: String): RemoteCollection

    /** Saves the post into the collection on the site. Throws [AccountException] with a readable reason on failure. */
    suspend fun saveTo(post: BooruPost, collection: RemoteCollection)

    /** The query that browses a collection's contents in the grid, for example "board:12345". */
    fun feedQuery(collection: RemoteCollection): String
}
