package dev.snapseek.core.booru

import dev.snapseek.core.model.ServiceKind

/** Danbooru-style tag categories plus the two e621 adds. The order is the one boorus (and Boorusama) list tags in. */
enum class TagCategory(val order: Int, val label: String) {
    ARTIST(0, "Artist"),
    COPYRIGHT(1, "Copyright"),
    CHARACTER(2, "Character"),
    SPECIES(3, "Species"),
    GENERAL(4, "General"),
    META(5, "Meta"),
    LORE(6, "Lore"),
    UNKNOWN(7, "Tags");

    companion object {
        /** Accepts Danbooru/Gelbooru/Moebooru numeric types and the names most sites use. */
        fun parse(raw: String?): TagCategory = when (raw?.lowercase()?.trim()) {
            "artist", "1", "circle", "origin", "creator" -> ARTIST
            "copyright", "3", "series", "source" -> COPYRIGHT
            "character", "4", "oc", "char" -> CHARACTER
            "species", "body-type" -> SPECIES
            "general", "tag", "0", "default", "content-fanmade", "content-official" -> GENERAL
            "meta", "metadata", "5", "faults", "6", "deprecated", "rating", "spoiler", "style" -> META
            "lore" -> LORE
            else -> UNKNOWN
        }

        /** e621's numeric categories differ from Danbooru's. */
        fun parseE621(raw: Int?): TagCategory = when (raw) {
            0 -> GENERAL
            1 -> ARTIST
            3 -> COPYRIGHT
            4 -> CHARACTER
            5 -> SPECIES
            7 -> META
            8 -> LORE
            else -> UNKNOWN
        }
    }
}

data class BooruPost(
    val id: Long,
    /** The site's content hash: md5 on most boorus, sha1 on Szurubooru, the first 32 hex of sha512 on Philomena. */
    val md5: String,
    val previewUrl: String,
    val sampleUrl: String,
    val fileUrl: String,
    val width: Int,
    val height: Int,
    val tags: List<String>,
    /** Normalised to a lowercase word: general, safe, sensitive, questionable, explicit, or "" when unknown. */
    val rating: String,
    val score: Int?,
    val source: String?,
    val owner: String?,
    val hasSample: Boolean,
    /** Filled when the post payload itself names tag categories (Danbooru, e621, Szurubooru). */
    val tagCategories: Map<String, TagCategory>? = null,
    val fileExtension: String? = null,
) {
    val extension: String
        get() = fileExtension?.lowercase()?.takeIf { it.isNotBlank() }
            ?: fileUrl.substringBefore('?').substringAfterLast('.', "").lowercase().ifEmpty { "jpg" }

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f

    val isVideo: Boolean get() = extension in setOf("webm", "mp4", "mov")
    val isAnimated: Boolean get() = extension == "gif" || isVideo

    val ratingLabel: String
        get() = rating.ifBlank { "unrated" }.replaceFirstChar { it.uppercase() }

    companion object {
        /**
         * Turns a site's rating into a word. Letters are ambiguous ("s" is Sensitive on Danbooru but Safe on
         * Moebooru and older Gelbooru forks), so callers say what their "s" means.
         */
        fun normaliseRating(raw: String?, sMeans: String = "safe"): String = when (raw?.lowercase()?.trim()) {
            null, "" -> ""
            "g", "general" -> "general"
            "s" -> sMeans
            "safe" -> "safe"
            "sensitive" -> "sensitive"
            "q", "questionable" -> "questionable"
            "e", "explicit" -> "explicit"
            "suggestive" -> "suggestive"
            "sketchy" -> "sketchy"
            "unsafe" -> "unsafe"
            else -> raw.lowercase()
        }
    }
}

data class TagSuggestion(val name: String, val postCount: Int?, val category: TagCategory)

data class BooruCredentials(val login: String, val apiKey: String)

/** Everything the app can talk to a booru about, implemented once per API family. Pages are 0-based here. */
interface BooruClient {
    val baseUrl: String
    val kind: ServiceKind

    /** Largest page the site accepts. */
    val maxPageSize: Int

    /** How tags are separated in a query: a space on most sites, a comma on Philomena. */
    val tagSeparator: String get() = " "

    /** Tags appended to every search when safe mode is on. */
    val safeModeTags: List<String>

    suspend fun posts(tags: String, page: Int, limit: Int): List<BooruPost>
    suspend fun suggest(prefix: String, limit: Int = 12): List<TagSuggestion>

    /** Tag → category for one post. Sites that put categories in the post payload answer from memory. */
    suspend fun tagCategories(post: BooruPost): Map<String, TagCategory> = post.tagCategories ?: emptyMap()

    fun postPageUrl(post: BooruPost): String

    /** Splits a query the way this site expects, dropping blanks. */
    fun splitQuery(query: String): List<String> =
        if (tagSeparator.isBlank()) query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        else query.split(tagSeparator.trim()).map { it.trim() }.filter { it.isNotEmpty() }

    fun joinQuery(tags: List<String>): String = tags.joinToString(tagSeparator)
}

class BooruHttpException(val url: String, val status: Int) : RuntimeException("HTTP $status for $url")
