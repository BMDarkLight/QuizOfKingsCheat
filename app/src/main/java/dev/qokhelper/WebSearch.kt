package dev.qokhelper

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

data class SearchResult(val title: String, val snippet: String, val url: String)

/**
 * Keyless web search run on the phone, for models that call a `web_search` function tool.
 * Tries DuckDuckGo's HTML page first and falls back to Wikipedia if that returns nothing.
 */
object WebSearch {
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) QoKHelper/0.1"
    private val PERSIAN = Regex("[؀-ۿ]")

    /** Results formatted as a numbered list for the model, or a short note if nothing was found. */
    fun search(query: String, limit: Int = 6): String {
        val results = runCatching { duckDuckGo(query) }.getOrDefault(emptyList())
            .ifEmpty { runCatching { wikipedia(query) }.getOrDefault(emptyList()) }
            .take(limit)
        if (results.isEmpty()) return "No results found for: $query"
        return results.mapIndexed { i, r -> "${i + 1}. ${r.title}\n${r.snippet}\n${r.url}" }.joinToString("\n\n")
    }

    private fun duckDuckGo(query: String): List<SearchResult> =
        parseDuckDuckGo(get("https://html.duckduckgo.com/html/?q=${enc(query)}"))

    private fun wikipedia(query: String): List<SearchResult> {
        val lang = if (PERSIAN.containsMatchIn(query)) "fa" else "en"
        val json = get("https://$lang.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=5&srsearch=${enc(query)}")
        return parseWikipedia(json, lang)
    }

    private val RESULT_LINK = Regex("""class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val RESULT_SNIPPET = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    fun parseDuckDuckGo(html: String): List<SearchResult> {
        val links = RESULT_LINK.findAll(html).toList()
        val snippets = RESULT_SNIPPET.findAll(html).map { clean(it.groupValues[1]) }.toList()
        return links.mapIndexed { i, m ->
            SearchResult(
                title = clean(m.groupValues[2]),
                snippet = snippets.getOrElse(i) { "" },
                url = realUrl(decodeEntities(m.groupValues[1])),
            )
        }
    }

    fun parseWikipedia(json: String, lang: String): List<SearchResult> {
        val hits = JSONObject(json).getJSONObject("query").getJSONArray("search")
        return (0 until hits.length()).map {
            val hit = hits.getJSONObject(it)
            val title = hit.getString("title")
            SearchResult(
                title = title,
                snippet = clean(hit.optString("snippet")),
                url = "https://$lang.wikipedia.org/wiki/${enc(title.replace(' ', '_'))}",
            )
        }
    }

    /** DuckDuckGo wraps result links in a redirect; the real URL is in its `uddg` parameter. */
    private fun realUrl(href: String): String {
        val uddg = Regex("""[?&]uddg=([^&]+)""").find(href)?.groupValues?.get(1)
        return if (uddg != null) URLDecoder.decode(uddg, "UTF-8") else href
    }

    private fun clean(html: String) = decodeEntities(html.replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ").trim()

    private fun decodeEntities(s: String) = s
        .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&nbsp;", " ").replace("&amp;", "&")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
