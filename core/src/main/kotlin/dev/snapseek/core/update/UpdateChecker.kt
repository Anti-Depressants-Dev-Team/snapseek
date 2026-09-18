package dev.snapseek.core.update

import dev.snapseek.core.net.Http
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Which file of a release this machine can actually install. */
enum class UpdateTarget(val extension: String) {
    WINDOWS_INSTALLER("msi"),
    DEBIAN("deb"),
    FEDORA("rpm"),
    MACOS("dmg"),
    ANDROID("apk"),
}

/** A release newer than what is running, and the one file from it worth downloading. */
data class Update(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Where the release's checksum list lives, so a download can be checked before it is run. */
    val checksumsUrl: String?,
)

/**
 * Asks GitHub what the newest release is and whether it beats what is running.
 *
 * Nothing is downloaded or installed here: this only answers "is there something newer, and which file". The
 * apps decide what to do with the answer, because installing means something different on a phone than on a
 * desktop. A check that fails for any reason returns null; an update check is never worth interrupting anyone for.
 */
class UpdateChecker(
    private val repository: String = DEFAULT_REPOSITORY,
    private val userAgent: String = "SnapSeek",
    /** Swapped out in tests; everywhere else this is the one HTTP client. */
    private val fetch: suspend (String) -> String? = { url ->
        val response = Http.request(
            url,
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "X-GitHub-Api-Version" to "2022-11-28",
                "User-Agent" to userAgent,
            ),
            readTimeoutMs = 15_000,
        )
        if (response.ok) response.text() else null
    },
) {
    private val log = KotlinLogging.logger {}

    suspend fun check(currentVersion: String, target: UpdateTarget): Update? {
        val body = runCatching { fetch("https://api.github.com/repos/$repository/releases/latest") }
            .onFailure { log.info(it) { "Couldn't reach GitHub for an update check" } }
            .getOrNull() ?: return null

        val update = parse(body, target) ?: return null
        if (!isNewer(update.version, currentVersion)) {
            log.info { "Running $currentVersion, latest is ${update.version}: nothing to do" }
            return null
        }
        log.info { "Update available: ${update.version} (${update.fileName})" }
        return update
    }

    companion object {
        const val DEFAULT_REPOSITORY = "Anti-Depressants-Dev-Team/snapseek"

        /** Pulls the release and the one asset that suits [target] out of GitHub's answer. */
        fun parse(body: String, target: UpdateTarget): Update? {
            val release = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
            if (release.bool("draft") == true || release.bool("prerelease") == true) return null
            val version = release.str("tag_name")?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: return null
            val assets = release["assets"] as? JsonArray ?: return null

            val suffix = ".${target.extension}"
            val asset = assets.filterIsInstance<JsonObject>()
                .filter { it.str("name")?.endsWith(suffix, ignoreCase = true) == true }
                // An unsigned Android build is published under the same extension; prefer the signed one.
                .minByOrNull { if (it.str("name")?.contains("unsigned") == true) 1 else 0 }
                ?: return null

            val checksums = assets.filterIsInstance<JsonObject>()
                .firstOrNull { it.str("name").equals("SHA256SUMS.txt", ignoreCase = true) }
                ?.str("browser_download_url")

            return Update(
                version = version,
                notes = release.str("body").orEmpty().trim(),
                pageUrl = release.str("html_url").orEmpty(),
                downloadUrl = asset.str("browser_download_url") ?: return null,
                fileName = asset.str("name") ?: return null,
                sizeBytes = (asset["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
                checksumsUrl = checksums,
            )
        }

        /**
         * True when [candidate] is a later version than [current]. Compares the numbers in order, so 2.10.0 beats
         * 2.9.0, and treats anything unparseable as "not newer" rather than nagging someone over a typo.
         */
        fun isNewer(candidate: String, current: String): Boolean {
            val new = numbers(candidate) ?: return false
            val old = numbers(current) ?: return true
            for (i in 0 until maxOf(new.size, old.size)) {
                val a = new.getOrElse(i) { 0 }
                val b = old.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }

        /** The checksum for [fileName] out of a SHA256SUMS.txt, or null when it isn't listed. */
        fun checksumFor(sums: String, fileName: String): String? = sums.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) parts[0].lowercase() to parts[1].removePrefix("*").trim() else null
            }
            .firstOrNull { it.second == fileName }
            ?.first

        private fun numbers(version: String): List<Int>? {
            val core = version.trim().removePrefix("v").takeWhile { it.isDigit() || it == '.' }
            val parts = core.split('.').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
            return parts.takeIf { it.isNotEmpty() }
        }

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
    }
}
