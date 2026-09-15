package dev.snapseek.android.platform

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.snapseek.core.booru.BooruPost
import dev.snapseek.core.download.ImageFetcher
import dev.snapseek.core.download.ImageKind
import dev.snapseek.core.download.ImageSource
import dev.snapseek.core.download.ImageTranscoder
import dev.snapseek.core.model.OutputFormat
import dev.snapseek.core.model.Service
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saving on a phone: the same fetch and convert the desktop does, then the file goes into the shared Pictures
 * folder through MediaStore, where the gallery and every other app can see it. No storage permission is needed
 * for that on Android 10 and up; below it we fall back to the app's own Pictures folder.
 */
class GallerySaver(
    private val context: Context,
    private val fetcher: ImageFetcher,
    private val transcoder: ImageTranscoder,
) {


    class Saved(val name: String, val location: String)

    suspend fun save(
        post: BooruPost,
        service: Service,
        format: OutputFormat,
        useOriginal: Boolean,
        userAgent: String,
    ): Saved = withContext(Dispatchers.IO) {
        val url = if (useOriginal || !post.hasSample) post.fileUrl else post.sampleUrl
        val fetched = fetcher.fetch(
            ImageSource(
                candidates = listOf(url, post.fileUrl, post.sampleUrl).distinct(),
                referer = service.url,
                userAgent = userAgent,
            ),
        )
        val wanted = if (post.isVideo) OutputFormat.ORIGINAL else format
        val result = transcoder.transcode(fetched.bytes, wanted, fallbackExtension = post.fileExtension)
        val name = "${service.id}_${post.displayId}.${result.extension}"
        val where = write(name, result.bytes, result.kind)
        Log.i(TAG, "Saved " + name + " (" + result.bytes.size + " bytes) to " + where)
        Saved(name, where)
    }

    private fun write(name: String, bytes: ByteArray, kind: ImageKind): String {
        val mime = when (kind) {
            ImageKind.PNG -> "image/png"
            ImageKind.JPEG -> "image/jpeg"
            ImageKind.WEBP -> "image/webp"
            ImageKind.GIF -> "image/gif"
            ImageKind.AVIF -> "image/avif"
            ImageKind.BMP -> "image/bmp"
            ImageKind.UNKNOWN -> "application/octet-stream"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("The gallery would not take the file")
            resolver.openOutputStream(uri).use { it?.write(bytes) ?: error("No stream for $uri") }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return "Pictures/$ALBUM"
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), ALBUM).apply { mkdirs() }
        File(dir, name).writeBytes(bytes)
        return dir.absolutePath
    }

    private companion object {
        const val ALBUM = "SnapSeek"
        const val TAG = "SnapSeek"
    }
}
