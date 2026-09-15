package dev.snapseek.core.download

import dev.snapseek.core.model.OutputFormat

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

/** The container an [OutputFormat] asks for; null for ORIGINAL, which keeps whatever came down the wire. */
val OutputFormat.kind: ImageKind?
    get() = when (this) {
        OutputFormat.ORIGINAL -> null
        OutputFormat.PNG -> ImageKind.PNG
        OutputFormat.JPEG -> ImageKind.JPEG
        OutputFormat.WEBP -> ImageKind.WEBP
        OutputFormat.GIF -> ImageKind.GIF
    }

interface ImageTranscoder {
    /** [fallbackExtension] names files whose type can't be sniffed (videos saved as ORIGINAL, for example). */
    fun transcode(bytes: ByteArray, target: OutputFormat, fallbackExtension: String? = null): TranscodeResult
}

