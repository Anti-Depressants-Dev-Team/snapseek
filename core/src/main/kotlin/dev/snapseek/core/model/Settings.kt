package dev.snapseek.core.model

import dev.snapseek.core.services.DefaultServices
import dev.snapseek.core.settings.AppPaths
import kotlinx.serialization.Serializable

/** What to fetch from a booru when saving: the upload itself, or the site's downscaled sample. */
@Serializable
enum class DownloadQuality(val label: String) { ORIGINAL("Original"), SAMPLE("Sample") }

/** Companion file written next to each saved image. */
@Serializable
enum class SidecarFormat(val label: String) { OFF("Off"), TAGS("Tags (.txt)"), JSON("Metadata (.json)") }

/** Thumbnail size in the booru grid. */
@Serializable
enum class GridSize(val label: String, val minColumnDp: Int) { SMALL("Small", 150), MEDIUM("Medium", 210), LARGE("Large", 290) }

/**
 * Everything the user can change, in one document. Persisted as settings.json.
 * Every field has a default so older files keep loading when new fields appear.
 */
@Serializable
data class Settings(
    val downloadDir: String = AppPaths.systemDownloads.toString(),
    val darkMode: Boolean = true,
    val adBlock: Boolean = true,
    val defaultFormat: OutputFormat = OutputFormat.PNG,
    val parallelDownloads: Int = 3,
    val skipDuplicates: Boolean = true,
    /** Template for images saved from web pages. See FilenameTemplate for tokens and options. */
    val fileNameTemplate: String = "{service}_{date}_{hash8}",
    /** Template for images saved from the booru browser, where id and tags are known. */
    val booruFileNameTemplate: String = "{booru}_{id}_{md5:maxlength=8}",
    val sidecar: SidecarFormat = SidecarFormat.OFF,
    val booruQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val gridSize: GridSize = GridSize.MEDIUM,
    /** One rule per line; see TagBlacklist. */
    val blacklist: String = "",
    /** Recent booru searches, newest first, keyed by service id. */
    val searchHistory: Map<String, List<String>> = emptyMap(),
    val services: List<Service> = DefaultServices.all,
)
