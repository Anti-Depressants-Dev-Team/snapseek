package dev.snapseek.core.download

import dev.snapseek.core.history.HistoryRepository
import dev.snapseek.core.model.NewHistoryEntry
import dev.snapseek.core.settings.SettingsStore
import dev.snapseek.core.sites.RefererPolicy
import dev.snapseek.core.sites.SourceResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

/**
 * The pipeline: resolve → fetch → hash → (duplicate?) → transcode → name → write → sidecar → record.
 * A fixed pool of workers drains one queue; every state change is visible through [jobs].
 */
class DownloadManager(
    private val resolvers: List<SourceResolver>,
    private val fetcher: ImageFetcher,
    private val transcoder: ImageTranscoder,
    private val namer: FileNamer,
    private val history: HistoryRepository,
    private val settings: SettingsStore,
    scope: CoroutineScope,
    workers: Int = settings.current.parallelDownloads.coerceIn(1, 8),
) {
    private val log = KotlinLogging.logger {}
    private val queue = Channel<DownloadRequest>(Channel.UNLIMITED)
    private val _jobs = MutableStateFlow<Map<String, DownloadJob>>(emptyMap())

    /** Newest first. */
    val jobs: StateFlow<List<DownloadJob>> = _jobs
        .map { m -> m.values.sortedByDescending { it.startedAt } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        repeat(workers) {
            scope.launch(Dispatchers.IO) {
                for (request in queue) run(request)
            }
        }
    }

    fun enqueue(request: DownloadRequest) {
        set(DownloadJob.Queued(request))
        queue.trySend(request)
    }

    fun dismiss(id: String) = _jobs.update { it - id }

    fun clearFinished() = _jobs.update { m -> m.filterValues { it.isActive } }

    private suspend fun run(r: DownloadRequest) {
        val started = _jobs.value[r.id]?.startedAt ?: Instant.now()
        try {
            val s = settings.current
            val source = (if (r.resolve) resolvers.firstNotNullOfOrNull { it.resolve(r.imageUrl, r.pageUrl) } else null)
                ?: ImageSource(listOf(r.imageUrl), referer = r.referer)
            set(DownloadJob.Fetching(r, started, source.candidates.first()))

            val fetched = fetcher.fetch(source)
            val sha = fetched.bytes.sha256Hex()
            val existing = history.findBySha(sha)
            if (existing != null && s.skipDuplicates && existing.file.exists()) {
                set(DownloadJob.Duplicate(r, started, existing))
                return
            }

            set(DownloadJob.Converting(r, started))
            val urlExtension = fetched.url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
            val out = transcoder.transcode(fetched.bytes, r.format ?: s.defaultFormat, urlExtension)

            val metadata = metadataFor(r, fetched, sha, out)
            val template = r.template ?: if (r.metadata.containsKey("id")) s.booruFileNameTemplate else s.fileNameTemplate
            val dir = Path.of(s.downloadDir).also { it.createDirectories() }
            val file = namer.nextFree(dir, namer.fileName(template, metadata, out.extension))
            file.writeBytes(out.bytes)
            runCatching { Sidecar.write(file, s.sidecar, metadata, fetched.url, r.pageUrl) }
                .onFailure { log.warn(it) { "Sidecar for ${file.fileName} failed" } }

            history.record(NewHistoryEntry(file, fetched.url, r.pageUrl, sha, r.serviceId, out.bytes.size.toLong()))
            set(DownloadJob.Saved(r, started, file, out.bytes.size.toLong(), out.copiedThrough))
            log.info { "Saved ${file.fileName} (${out.kind}, ${out.bytes.size} bytes${if (out.copiedThrough) ", copied through" else ""})" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "Download failed for ${r.imageUrl}" }
            set(DownloadJob.Failed(r, started, e.message ?: e::class.simpleName ?: "Download failed"))
        }
    }

    private fun metadataFor(r: DownloadRequest, fetched: FetchedImage, sha: String, out: TranscodeResult): Map<String, String> {
        val service = r.serviceId
            ?: RefererPolicy.hostOf(r.pageUrl)?.removePrefix("www.")?.substringBefore('.')
            ?: "image"
        return buildMap {
            putAll(r.metadata)
            put("service", service)
            putIfAbsent("booru", service)
            put("sha256", sha)
            put("hash8", sha.take(8))
            putIfAbsent("md5", fetched.bytes.md5Hex())
            put("original", runCatching { URI(fetched.url).path.substringAfterLast('/').substringBeforeLast('.') }.getOrDefault(""))
            put("extension", out.extension)
            put("uuid", UUID.randomUUID().toString())
        }
    }

    private fun set(job: DownloadJob) = _jobs.update { it + (job.request.id to job) }
}
