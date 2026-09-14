package dev.snapseek.app.ui.common

import dev.snapseek.core.download.ImageKind
import dev.snapseek.core.download.ImageTranscoder
import dev.snapseek.core.download.TranscodeResult
import dev.snapseek.core.download.UnsupportedImageException
import dev.snapseek.core.download.kind
import dev.snapseek.core.model.OutputFormat
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

/**
 * Transcoder on Skia, which Compose already ships: decodes JPEG, PNG, WebP and GIF (first frame), encodes PNG,
 * JPEG and WebP. GIF output and anything Skia can't decode fall back to the ImageIO transcoder.
 */
class SkiaImageTranscoder(private val fallback: ImageTranscoder) : ImageTranscoder {

    override fun transcode(bytes: ByteArray, target: OutputFormat, fallbackExtension: String?): TranscodeResult {
        val sourceKind = ImageKind.sniff(bytes)
        val targetKind = target.kind ?: sourceKind
        if (target == OutputFormat.ORIGINAL || targetKind == sourceKind || sourceKind == ImageKind.UNKNOWN) {
            return fallback.transcode(bytes, target, fallbackExtension)
        }

        val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull()
            ?: return fallback.transcode(bytes, target, fallbackExtension)

        try {
            if (targetKind == ImageKind.GIF) {
                // Skia has no GIF encoder; hand ImageIO a PNG it can read even when the source was WebP.
                val png = if (sourceKind == ImageKind.WEBP || sourceKind == ImageKind.AVIF) encode(image, EncodedImageFormat.PNG, 100) else bytes
                return fallback.transcode(png, target, fallbackExtension)
            }
            val (format, quality) = when (targetKind) {
                ImageKind.PNG -> EncodedImageFormat.PNG to 100
                ImageKind.JPEG -> EncodedImageFormat.JPEG to 90
                ImageKind.WEBP -> EncodedImageFormat.WEBP to 90
                else -> throw UnsupportedImageException("No encoder for ${targetKind.name}")
            }
            val source = if (targetKind == ImageKind.JPEG) flattenOntoWhite(image) else image
            return TranscodeResult(encode(source, format, quality), targetKind, copiedThrough = false)
        } finally {
            image.close()
        }
    }

    private fun encode(image: Image, format: EncodedImageFormat, quality: Int): ByteArray =
        image.encodeToData(format, quality)?.bytes ?: throw UnsupportedImageException("Encoding to $format failed")

    private fun flattenOntoWhite(image: Image): Image {
        val surface = Surface.makeRasterN32Premul(image.width, image.height)
        return try {
            surface.canvas.clear(Color.WHITE)
            surface.canvas.drawImage(image, 0f, 0f)
            surface.makeImageSnapshot()
        } finally {
            surface.close()
        }
    }
}
