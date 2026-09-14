package dev.snapseek.core.download

import java.nio.file.Path
import java.time.Clock
import kotlin.io.path.exists

/**
 * Turns a template plus metadata into a safe file name, then finds a free path.
 * Collision rule is the one users already know from 1.x: "name (2).png", "name (3).png", ...
 */
class FileNamer(clock: Clock = Clock.systemDefaultZone()) {
    private val templates = FilenameTemplate(clock)

    /** Full file name including extension. If the template names {extension} itself, it is not appended again. */
    fun fileName(template: String, metadata: Map<String, String>, extension: String): String {
        val ext = extension.trim('.')
        val rendered = templates.render(template, metadata + ("extension" to ext))
        val hasExtension = templates.containsToken(template, "extension") || templates.containsToken(template, "ext")
        val stem = if (hasExtension) rendered.substringBeforeLast(".$ext", rendered) else rendered
        val safeStem = stem.sanitized().ifBlank {
            (metadata["sha256"] ?: metadata["hash"] ?: metadata["md5"])?.take(16) ?: "image"
        }
        return "$safeStem.$ext"
    }

    fun nextFree(dir: Path, fileName: String): Path {
        val stem = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        var candidate = dir.resolve(fileName)
        var n = 2
        while (candidate.exists()) {
            candidate = dir.resolve(if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext")
            n++
        }
        return candidate
    }

    private fun String.sanitized(): String =
        replace(ILLEGAL, "_").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(MAX_STEM)

    private companion object {
        const val MAX_STEM = 180
        val ILLEGAL = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    }
}
