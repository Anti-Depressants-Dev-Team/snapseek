package dev.snapseek.core.download

import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * File name templates with Boorusama's token grammar: {token} or {token:option,option=value,…}.
 *
 * Tokens come from the download's metadata map. Always present: service, booru, date, hash, hash8, md5,
 * original, extension, uuid. Booru downloads add id, tags, artist, character, copyright, general, meta,
 * rating, score, width, height, source.
 *
 * Options (each applies where it makes sense, unknown ones are ignored):
 *   maxlength=N      cut the value to N characters
 *   limit=N          keep only the first N space-separated items (tag lists)
 *   delimiter=X      join items with X; aliases: comma, space, underscore, hyphen, dot, plus
 *   nomod            strip "_(modifier)" suffixes from tags, so lumine_(genshin_impact) becomes lumine
 *   case=lower|upper|title
 *   format=PATTERN   java DateTimeFormatter pattern for {date}
 *   pad_left=N       zero-pad numbers to N digits
 *   single_letter    first letter only (ratings: general → g)
 */
class FilenameTemplate(private val clock: Clock = Clock.systemDefaultZone()) {

    fun render(template: String, metadata: Map<String, String>): String =
        TOKEN.replace(template) { m ->
            val name = m.groupValues[1].lowercase()
            val options = parseOptions(m.groupValues[2])
            apply(name, valueOf(name, metadata, options), options)
        }

    fun containsToken(template: String, token: String): Boolean =
        TOKEN.findAll(template).any { it.groupValues[1].equals(token, ignoreCase = true) }

    private fun valueOf(name: String, metadata: Map<String, String>, options: Map<String, String>): String = when (name) {
        "date" -> {
            val pattern = options["format"]?.takeIf { it.isNotBlank() } ?: DEFAULT_DATE
            val formatter = runCatching { DateTimeFormatter.ofPattern(pattern) }.getOrDefault(DateTimeFormatter.ofPattern(DEFAULT_DATE))
            LocalDateTime.now(clock).format(formatter)
        }
        else -> metadata[name] ?: metadata[ALIASES[name] ?: ""] ?: ""
    }

    private fun apply(name: String, raw: String, options: Map<String, String>): String {
        var items = raw.split(' ').filter { it.isNotEmpty() }
        if (options.containsKey("nomod")) items = items.map { it.replace(MODIFIER, "") }.filter { it.isNotEmpty() }
        options["limit"]?.toIntOrNull()?.let { if (it >= 0) items = items.take(it) }

        var value = when (val d = options["delimiter"]) {
            null -> if (name in LIST_TOKENS) items.joinToString(" ") else raw
            else -> items.joinToString(DELIMITERS[d] ?: d)
        }
        if (options.containsKey("single_letter")) value = value.take(1)
        options["pad_left"]?.toIntOrNull()?.let { width -> if (value.all(Char::isDigit) && value.isNotEmpty()) value = value.padStart(width, '0') }
        when (options["case"]?.lowercase()) {
            "lower" -> value = value.lowercase()
            "upper" -> value = value.uppercase()
            "title" -> value = value.split(' ').joinToString(" ") { w -> w.replaceFirstChar { c -> c.titlecase() } }
        }
        options["maxlength"]?.toIntOrNull()?.let { if (it >= 0 && value.length > it) value = value.take(it) }
        return value
    }

    private fun parseOptions(spec: String): Map<String, String> {
        if (spec.isBlank()) return emptyMap()
        return spec.split(',').mapNotNull { part ->
            val p = part.trim()
            if (p.isEmpty()) return@mapNotNull null
            val eq = p.indexOf('=')
            if (eq < 0) p.lowercase() to "true" else p.substring(0, eq).trim().lowercase() to p.substring(eq + 1).trim()
        }.toMap()
    }

    companion object {
        const val DEFAULT_DATE = "yyyyMMdd-HHmmss"
        private val TOKEN = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?\}""")
        private val MODIFIER = Regex("""_\([^)]*\)""")
        private val LIST_TOKENS = setOf("tags", "artist", "character", "copyright", "general", "meta")
        private val ALIASES = mapOf("booru" to "service", "site" to "service", "ext" to "extension", "hash" to "sha256")
        private val DELIMITERS = mapOf(
            "comma" to ", ", "space" to " ", "underscore" to "_", "hyphen" to "-", "dot" to ".", "plus" to "+", "none" to "",
        )

        /** Every token name the UI can offer in a help text. */
        val KNOWN_TOKENS = listOf(
            "service", "date", "hash8", "hash", "md5", "original", "extension", "uuid",
            "id", "tags", "artist", "character", "copyright", "general", "meta", "rating", "score", "width", "height", "source",
        )
    }
}
