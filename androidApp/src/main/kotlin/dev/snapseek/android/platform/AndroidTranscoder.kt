package dev.snapseek.android.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import dev.snapseek.core.download.ImageKind
import dev.snapseek.core.download.ImageTranscoder
import dev.snapseek.core.download.TranscodeResult
import dev.snapseek.core.download.UnsupportedImageException
import dev.snapseek.core.download.kind
import dev.snapseek.core.model.OutputFormat
import java.io.ByteArrayOutputStream

/**
 * The same job the desktop does with Skia, done with the decoders Android already has. Android reads PNG, JPEG,
 * WebP, GIF and (since 12) AVIF, and writes PNG, JPEG and WebP, so the only conversion it can't do is "make me a
 * GIF", which stays a save-the-original.
 */
class AndroidTranscoder : ImageTranscoder {

    override fun transcode(bytes: ByteArray, target: OutputFormat, fallbackExtension: String?): TranscodeResult {
        val source = ImageKind.sniff(bytes)
        if (source == ImageKind.UNKNOWN) {
            if (target == OutputFormat.ORIGINAL && fallbackExtension != null) {
                return TranscodeResult(bytes, source, copiedThrough = true, extension = fallbackExtension)
            }
            throw UnsupportedImageException("The server did not return an image")
        }

        val wanted = target.kind ?: source
        if (wanted == source) return TranscodeResult(bytes, source, copiedThrough = true)
        // An animation would lose everything but its first frame, so it is kept whole instead.
        if (source == ImageKind.GIF) throw UnsupportedImageException("Animations are saved as they are; pick Original")

        val format = when (wanted) {
            ImageKind.PNG -> Bitmap.CompressFormat.PNG
            ImageKind.JPEG -> Bitmap.CompressFormat.JPEG
            ImageKind.WEBP -> webpFormat()
            else -> throw UnsupportedImageException("No ${wanted.name} encoder on Android")
        }

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw UnsupportedImageException("Can't read this ${source.name.lowercase()}; use \"Save original\"")
        val toWrite = if (wanted == ImageKind.JPEG) decoded.flattenOntoWhite() else decoded
        val out = ByteArrayOutputStream(bytes.size)
        val written = toWrite.compress(format, if (wanted == ImageKind.JPEG) 95 else 100, out)
        decoded.recycle()
        if (toWrite !== decoded) toWrite.recycle()
        if (!written) throw UnsupportedImageException("Encoding to ${wanted.name} failed")
        return TranscodeResult(out.toByteArray(), wanted, copiedThrough = false)
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP

    /** JPEG has no transparency, so anything see-through would come out black without this. */
    private fun Bitmap.flattenOntoWhite(): Bitmap {
        if (!hasAlpha()) return this
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@flattenOntoWhite, 0f, 0f, null)
        }
        return flat
    }
}
