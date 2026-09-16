package dev.snapseek.core.booru

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Android's regex engine is ICU, not the JVM's, and ICU refuses a bare `{` or `}` that the JVM quietly treats as
 * a literal. Patterns here are built when a class loads, so one of those is not a bad match later, it is the app
 * dying the moment it touches that client, on the phone only. A JVM test cannot compile these the way Android
 * will, so this reads the source instead and insists every brace is escaped, a quantifier, or in a character class.
 */
class RegexPortabilityTest {

    @Test
    fun `every regex in core compiles on Android too`() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty(), "no sources found to check; is the working directory the module?")

        val offenders = sources.flatMap { file ->
            file.readLines().withIndex().flatMap { (line, text) ->
                patternsIn(text).filter { looseBrace(it) }.map { "${file.name}:${line + 1}  $it" }
            }
        }
        assertTrue(offenders.isEmpty(), "these patterns have a brace Android will reject:\n" + offenders.joinToString("\n"))
    }

    /** The pattern text of every `Regex("…")` or `Regex("""…""")` on one line. */
    private fun patternsIn(line: String): List<String> =
        LITERAL.findAll(line)
            .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            .filter { it.isNotEmpty() }
            .toList()

    private companion object {
        // Regex("""raw""")  or  Regex("escaped"). Written with escapes rather than raw quotes, since the thing
        // being matched is itself full of quotes.
        val LITERAL = Regex("Regex\\(\"{3}(.*?)\"{3}|Regex\\(\"((?:[^\"\\\\]|\\\\.)*)\"")
    }

    /** True when the pattern has a brace that is neither escaped, nor a quantifier, nor inside a character class. */
    private fun looseBrace(pattern: String): Boolean {
        var i = 0
        var inClass = false
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> i++ // whatever follows is escaped, including \p{…} which ICU knows
                c == '[' && !inClass -> inClass = true
                c == ']' && inClass -> inClass = false
                !inClass && c == '{' -> if (!isQuantifier(pattern, i)) return true
                !inClass && c == '}' -> if (!closesQuantifier(pattern, i)) return true
            }
            i++
        }
        return false
    }

    private fun isQuantifier(pattern: String, open: Int): Boolean =
        Regex("""^\{\d+(,\d*)?}""").containsMatchIn(pattern.substring(open))

    /** A `}` is fine only when the `{` that opened it was a quantifier. */
    private fun closesQuantifier(pattern: String, close: Int): Boolean {
        val open = pattern.lastIndexOf('{', close)
        return open >= 0 && (open == 0 || pattern[open - 1] != '\\') && isQuantifier(pattern, open)
    }
}
