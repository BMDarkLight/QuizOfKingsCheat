package dev.qokhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class WebSearchTest {

    @Test
    fun parsesDuckDuckGoResults() {
        val html = """
            <div class="result results_links">
              <h2 class="result__title">
                <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Ffa.wikipedia.org%2Fwiki%2F%D8%AA%D9%87%D8%B1%D8%A7%D9%86&amp;rut=abc">تهران - <b>ویکی‌پدیا</b></a>
              </h2>
              <a class="result__snippet" href="//duckduckgo.com/l/?uddg=x">تهران پایتخت ایران است &amp; بزرگ&#x27;ترین شهر</a>
            </div>
            <div class="result results_links">
              <a rel="nofollow" class="result__a" href="https://example.com/b">Second</a>
              <a class="result__snippet" href="https://example.com/b">Snippet <b>two</b></a>
            </div>
        """.trimIndent()

        assertEquals(
            listOf(
                SearchResult("تهران - ویکی‌پدیا", "تهران پایتخت ایران است & بزرگ'ترین شهر", "https://fa.wikipedia.org/wiki/تهران"),
                SearchResult("Second", "Snippet two", "https://example.com/b"),
            ),
            WebSearch.parseDuckDuckGo(html),
        )
    }

    @Test
    fun parsesWikipediaResults() {
        val json = """{"query":{"search":[{"title":"Mount Everest","snippet":"<span class=\"searchmatch\">Everest</span> is Earth's highest"}]}}"""
        assertEquals(
            listOf(SearchResult("Mount Everest", "Everest is Earth's highest", "https://en.wikipedia.org/wiki/Mount_Everest")),
            WebSearch.parseWikipedia(json, "en"),
        )
    }
}
