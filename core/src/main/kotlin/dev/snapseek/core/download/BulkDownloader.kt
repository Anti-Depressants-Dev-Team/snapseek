package dev.snapseek.core.download

import dev.snapseek.core.booru.BooruClient
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.booru.TagBlacklist
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * "Download everything matching this search": walks the result pages and feeds each post to the
 * [DownloadManager]. Boorusama calls this the bulk downloader. One task per search; cancel any time.
 */
class BulkDownloader(private val downloads: DownloadManager, private val scope: CoroutineScope) {
    private val log = KotlinLogging.logger {}

    data class Task(
        val id: String = UUID.randomUUID().toString(),
        val serviceId: String,
        val serviceName: String,
        val query: String,
        val maxPosts: Int,
        val subfolder: String?,
    )

    sealed interface Status {
        data class Running(val pages: Int, val seen: Int, val enqueued: Int, val hidden: Int) : Status
        data class Done(val enqueued: Int, val hidden: Int, val reason: String) : Status
        data class Cancelled(val enqueued: Int) : Status
        data class Failed(val enqueued: Int, val message: String) : Status
    }

    data class Entry(val task: Task, val status: Status) {
        val isActive: Boolean get() = status is Status.Running
        val enqueued: Int
            get() = when (val s = status) {
                is Status.Running -> s.enqueued
                is Status.Done -> s.enqueued
                is Status.Cancelled -> s.enqueued
                is Status.Failed -> s.enqueued
            }
    }

    private val _tasks = MutableStateFlow<List<Entry>>(emptyList())
    val tasks: StateFlow<List<Entry>> = _tasks.asStateFlow()
    private val jobs = ConcurrentHashMap<String, Job>()

    fun start(
        task: Task,
        client: BooruClient,
        blacklist: TagBlacklist,
        requestFor: (BooruPost) -> DownloadRequest,
    ): String {
        set(Entry(task, Status.Running(0, 0, 0, 0)))
        jobs[task.id] = scope.launch {
            var pages = 0
            var seen = 0
            var enqueued = 0
            var hidden = 0
            var reason = "reached the limit"
            try {
                var page = 0
                while (enqueued < task.maxPosts) {
                    val raw = client.posts(task.query, page, client.maxPageSize)
                    pages++
                    seen += raw.size
                    if (raw.isEmpty()) {
                        reason = "no more posts"
                        break
                    }
                    val visible = blacklist.filter(raw) { it.tags }
                    hidden += raw.size - visible.size
                    for (post in visible) {
                        if (enqueued >= task.maxPosts) break
                        downloads.enqueue(requestFor(post).copy(subfolder = task.subfolder, batchId = task.id))
                        enqueued++
                    }
                    set(Entry(task, Status.Running(pages, seen, enqueued, hidden)))
                    if (raw.size < client.maxPageSize) {
                        reason = "no more posts"
                        break
                    }
                    page++
                    delay(400)   // be polite to the site between pages
                }
                set(Entry(task, Status.Done(enqueued, hidden, reason)))
                log.info { "Bulk '${task.query}' on ${task.serviceName}: $enqueued queued, $hidden hidden, $reason" }
            } catch (e: CancellationException) {
                set(Entry(task, Status.Cancelled(enqueued)))
                throw e
            } catch (e: Exception) {
                log.warn(e) { "Bulk '${task.query}' failed" }
                set(Entry(task, Status.Failed(enqueued, e.message ?: "failed")))
            } finally {
                jobs.remove(task.id)
            }
        }
        return task.id
    }

    fun cancel(id: String) {
        jobs[id]?.cancel()
    }

    fun dismiss(id: String) = _tasks.update { list -> list.filterNot { it.task.id == id } }

    private fun set(entry: Entry) = _tasks.update { list -> listOf(entry) + list.filterNot { it.task.id == entry.task.id } }
}
