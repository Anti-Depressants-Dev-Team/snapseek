package dev.snapseek.core.adblock

/**
 * Domain-level blocking: a request is blocked when its host, or any parent domain of it, is on the list.
 * Accepts hosts-file lines ("0.0.0.0 ads.example.com"), bare domains, and ABP "||example.com^" rules,
 * which covers the bulk of every popular list without a full filter-syntax engine.
 */
class HostBlocklist(hosts: Collection<String>) {
    private val hosts: Set<String> = hosts.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }.toHashSet()

    val size: Int get() = hosts.size

    fun blocks(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        var h = host.lowercase()
        while (true) {
            if (h in hosts) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    companion object {
        val EMPTY = HostBlocklist(emptyList())

        fun parse(text: String): HostBlocklist = HostBlocklist(text.lineSequence().mapNotNull(::parseLine).toList())

        fun fromResource(path: String): HostBlocklist =
            HostBlocklist::class.java.getResourceAsStream(path)?.use { parse(it.reader().readText()) } ?: EMPTY

        internal fun parseLine(raw: String): String? {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return null
            if (line.startsWith("||") && line.endsWith("^")) {
                return line.substring(2, line.length - 1).lowercase().takeIf { it.matches(DOMAIN) }
            }
            val parts = line.split(WHITESPACE)
            val candidate = if (parts.size >= 2 && parts[0].matches(ADDRESS)) parts[1] else parts[0]
            return candidate.lowercase().takeIf { it.matches(DOMAIN) && it != "localhost" && !it.endsWith(".localdomain") }
        }

        private val DOMAIN = Regex("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$")
        private val ADDRESS = Regex("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1|::1?|::)$")
        private val WHITESPACE = Regex("\\s+")
    }
}
