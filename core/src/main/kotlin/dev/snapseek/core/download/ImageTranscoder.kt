package dev.snapseek.core.download

import dev.snapseek.core.model.OutputFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** What the bytes actually are, decided by magic numbers rather than by trusting the URL or Content-Type. */
enum class ImageKind(val extension: String, val imageIoName: String?) {
    PNG("png", "png"),
    JPEG("jpg", "jpg"),
    GIF("gif", "gif"),
    WEBP("webp", null),
    BMP("bmp", "bmp"),
    AVIF("avif", null),
    UNKNOWN("bin", null);

    companion object {
        fun sniff(b: ByteArray): ImageKind = when {
            b.size >= 8 && b[0] == 0x89.toByte() && b.matchesAscii(1, "PNG") -> PNG
            b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> JPEG
            b.size >= 6 && b.matchesAscii(0, "GIF8") -> GIF
            b.size >= 12 && b.matchesAscii(0, "RIFF") && b.matchesAscii(8, "WEBP") -> WEBP
            b.size >= 12 && (b.matchesAscii(4, "ftypavif") || b.matchesAscii(4, "ftypavis")) -> AVIF
            b.size >= 2 && b.matchesAscii(0, "BM") -> BMP
            else -> UNKNOWN
        }

        private fun ByteArray.matchesAscii(offset: Int, s: String): Boolean =
            size >= offset + s.length && s.indices.all { this[offset + it] == s[it].code.toByte() }
    }
}

class TranscodeResult(
    val bytes: ByteArray,
    val kind: ImageKind,
    /** True when the input was written out untouched, which is the lossless, fast path. */
    val copiedThrough: Boolean,
    val extension: String = kind.extension,
)

class UnsupportedImageException(message: String) : RuntimeException(message)

interface ImageTranscoder {
    /** [fallbackExtension] names files whose type can't be sniffed (videos saved as ORIGINAL, for example). */
    fun transcode(bytes: ByteArray, target: OutputFormat, fallbackExtension: String? = null): TranscodeResult
}

/**
 * Transcoder built on the JDK's ImageIO: PNG, JPEG, GIF and BMP in and out, no native code.
 * WebP and AVIF sources can be saved as-is (ORIGINAL) but not converted; the Skia transcoder in phase 1 lifts that.
 */
class ImageIoTranscoder : ImageTranscoder {

    override fun transcode(bytes: ByteArray, target: OutputFormat, fallbackExtension: String?): TranscodeResult {
        val sourceKind = ImageKind.sniff(bytes)
        if (sourceKind == ImageKind.UNKNOWN) {
            // Not an image we recognise (a webm from a booru, say). ORIGINAL still saves it byte for byte.
            if (target == OutputFormat.ORIGINAL && fallbackExtension != null) {
                return TranscodeResult(bytes, sourceKind, copiedThrough = true, extension = fallbackExtension)
            }
            throw UnsupportedImageException("The server did not return an image")
        }

        val targetKind = when (target) {
            OutputFormat.ORIGINAL -> sourceKind
            OutputFormat.PNG -> ImageKind.PNG
            OutputFormat.JPEG -> ImageKind.JPEG
            OutputFormat.GIF -> ImageKind.GIF
        }
        if (targetKind == sourceKind) return TranscodeResult(bytes, sourceKind, copiedThrough = true)

        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw UnsupportedImageException("Can't convert ${sourceKind.name.lowercase()} yet; use \"Save original\"")
        val writerName = targetKind.imageIoName
            ?: throw UnsupportedImageException("No ${targetKind.name} encoder available")

        val toWrite = if (targetKind == ImageKind.JPEG) flattenOntoWhite(decoded) else decoded
        val out = ByteArrayOutputStream(bytes.size)
        if (!ImageIO.write(toWrite, writerName, out)) throw UnsupportedImageException("Encoding to ${targetKind.name} failed")
        return TranscodeResult(out.toByteArray(), targetKind, copiedThrough = false)
    }

    private fun flattenOntoWhite(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_RGB || src.type == BufferedImage.TYPE_3BYTE_BGR) return src
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, src.width, src.height)
            g.drawImage(src, 0, 0, null)
        } finally {
            g.dispose()
        }
        return out
    }
}
