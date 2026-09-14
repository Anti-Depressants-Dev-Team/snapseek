package dev.snapseek.core.model

import kotlinx.serialization.Serializable

/** What the user asked for. ORIGINAL means "write the bytes exactly as the server sent them". */
@Serializable
enum class OutputFormat(val label: String) {
    PNG("PNG"),
    JPEG("JPEG"),
    WEBP("WebP"),
    GIF("GIF"),
    ORIGINAL("Original");

    val menuLabel: String
        get() = if (this == ORIGINAL) "Save original" else "Save as $label"
}
