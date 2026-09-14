package dev.snapseek.core.booru

/**
 * Boorusama-style blacklist: one rule per line, each rule a space-separated group of tags that must ALL be present
 * for the post to be hidden. A tag prefixed with "-" must be absent. Lines starting with # are comments.
 *
 *   loli            hides every post tagged loli
 *   1boy solo       hides posts that are both 1boy and solo
 *   gore -parody    hides gore unless it is also tagged parody
 */
class TagBlacklist private constructor(private val rules: List<Rule>) {

    private class Rule(val required: Set<String>, val forbidden: Set<String>)

    val isEmpty: Boolean get() = rules.isEmpty()
    val size: Int get() = rules.size

    fun hides(tags: Collection<String>): Boolean {
        if (rules.isEmpty()) return false
        val set = tags.mapTo(HashSet(tags.size)) { it.lowercase() }
        return rules.any { rule -> rule.required.all { it in set } && rule.forbidden.none { it in set } }
    }

    fun <T> filter(items: List<T>, tagsOf: (T) -> Collection<String>): List<T> =
        if (rules.isEmpty()) items else items.filterNot { hides(tagsOf(it)) }

    companion object {
        val EMPTY = TagBlacklist(emptyList())

        fun parse(text: String): TagBlacklist {
            val rules = text.lineSequence()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val parts = line.split(WHITESPACE).filter { it.isNotEmpty() && it != "-" }
                    val required = parts.filterNot { it.startsWith("-") }.toSet()
                    val forbidden = parts.filter { it.startsWith("-") }.map { it.drop(1) }.toSet()
                    if (required.isEmpty()) null else Rule(required, forbidden)
                }
                .toList()
            return TagBlacklist(rules)
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
