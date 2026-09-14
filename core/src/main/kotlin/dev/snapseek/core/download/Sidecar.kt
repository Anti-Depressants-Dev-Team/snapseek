package dev.snapseek.core.download

import dev.snapseek.core.model.SidecarFormat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.writeText

/** Writes a companion file next to a saved image: tags as text, or all metadata as JSON. Same idea as Boorusama. */
object Sidecar {
    fun write(imageFile: Path, format: SidecarFormat, metadata: Map<String, String>, sourceUrl: String, pageUrl: String) {
        when (format) {
            SidecarFormat.OFF -> return
            SidecarFormat.TAGS -> {
                val tags = metadata["tags"]?.takeIf { it.isNotBlank() } ?: return
                imageFile.resolveSibling(imageFile.fileName.toString() + ".txt").writeText(tags.split(' ').joinToString("\n"))
            }
            SidecarFormat.JSON -> {
                val doc = buildJsonObject {
                    metadata.toSortedMap().forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                    put("source_url", JsonPrimitive(sourceUrl))
                    put("page_url", JsonPrimitive(pageUrl))
                    put("saved_at", JsonPrimitive(Instant.now().toString()))
                    put("file", JsonPrimitive(imageFile.fileName.toString()))
                }
                imageFile.resolveSibling(imageFile.fileName.toString() + ".json").writeText(doc.toString())
            }
        }
    }
}
