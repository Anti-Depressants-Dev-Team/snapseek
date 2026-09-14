package dev.snapseek.core.booru

/** Danbooru-style tag categories. The order is the one boorus (and Boorusama) list tags in. */
enum class TagCategory(val order: Int, val label: String) {
    ARTIST(0, "Artist"),
    COPYRIGHT(1, "Copyright"),
    CHARACTER(2, "Character"),
    GENERAL(3, "General"),
    META(4, "Meta"),
    UNKNOWN(5, "Tags");

    companion object {
        fun parse(raw: String?): TagCategory = when (raw?.lowercase()?.trim()) {
            "artist", "1" -> ARTIST
            "copyright", "3" -> COPYRIGHT
            "character", "4" -> CHARACTER
            "general", "tag", "0" -> GENERAL
            "meta", "metadata", "5" -> META
            else -> UNKNOWN
        }
    }
}

data class BooruPost(
    val id: Long,
    val md5: String,
    val previewUrl: String,
    val sampleUrl: String,
    val fileUrl: String,
    val width: Int,
    val height: Int,
    val tags: List<String>,
    val rating: String,
    val score: Int?,
    val source: String?,
    val owner: String?,
    val hasSample: Boolean,
) {
    val extension: String
        get() = fileUrl.substringBefore('?').substringAfterLast('.', "").lowercase().ifEmpty { "jpg" }

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f

    val isVideo: Boolean get() = extension in setOf("webm", "mp4")
    val isAnimated: Boolean get() = extension == "gif" || isVideo

    val ratingLabel: String
        get() = when (rating.lowercase()) {
            "g", "general" -> "General"
            "s", "safe", "sensitive" -> "Sensitive"
            "q", "questionable" -> "Questionable"
            "e", "explicit" -> "Explicit"
            else -> rating.ifBlank { "Unrated" }
        }
}

data class TagSuggestion(val name: String, val postCount: Int?, val category: TagCategory)

/** Everything the app can talk to a booru about. Implemented per API family; Gelbooru v0.2 comes first. */
interface BooruClient {
    val baseUrl: String
    suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost>
    suspend fun suggest(prefix: String, limit: Int = 12): List<TagSuggestion>
    /** Tag → category for one post. Boorus without a tag API get this by reading the post page. */
    suspend fun tagCategories(post: BooruPost): Map<String, TagCategory>
    fun postPageUrl(post: BooruPost): String
}
