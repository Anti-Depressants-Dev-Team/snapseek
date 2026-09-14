package dev.snapseek.core.sites

import java.net.URI

/**
 * Some image CDNs refuse requests without the "right" Referer. This is the one place that knows which.
 * Used by both the in-browser request interceptor and the download fetcher, so they can never disagree.
 */
class RefererPolicy(private val rules: List<Rule> = DEFAULT_RULES) {

    data class Rule(val hostSuffixes: List<String>, val referer: String)

    fun refererFor(host: String?): String? {
        if (host.isNullOrEmpty()) return null
        val h = host.lowercase()
        return rules.firstOrNull { rule -> rule.hostSuffixes.any { h == it || h.endsWith(".$it") } }?.referer
    }

    fun refererForUrl(url: String): String? = refererFor(hostOf(url))

    companion object {
        val DEFAULT_RULES = listOf(
            Rule(listOf("pixiv.net", "pximg.net"), "https://www.pixiv.net/"),
            Rule(listOf("pinimg.com"), "https://www.pinterest.com/"),
            Rule(listOf("deviantart.com", "deviantart.net", "wixmp.com", "dauserusercontent.com"), "https://www.deviantart.com/"),
        )

        fun hostOf(url: String?): String? = url?.let { runCatching { URI(it).host }.getOrNull() }
    }
}
