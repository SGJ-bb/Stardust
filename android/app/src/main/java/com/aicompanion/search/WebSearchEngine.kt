package com.aicompanion.search

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun search(query: String): SearchResult {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://cn.bing.com/search?q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use SearchResult(
                        success = false,
                        results = emptyList(),
                        error = "搜索服务暂时不可用"
                    )
                }

                val html = response.body?.string() ?: ""
                val results = parseBingResults(html)

                SearchResult(success = true, results = results)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SearchResult(
                success = false,
                results = emptyList(),
                error = "搜索出错: ${e.message}"
            )
        }
    }

    fun searchBaike(query: String): SearchResult {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://baike.baidu.com/item/$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use SearchResult(
                        success = false,
                        results = emptyList(),
                        error = "百科服务暂时不可用"
                    )
                }

                val html = response.body?.string() ?: ""
                val description = extractBaikeDescription(html)

                SearchResult(
                    success = true,
                    results = listOf(
                        SearchResultItem(
                            title = "$query - 百度百科",
                            snippet = description,
                            link = url
                        )
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SearchResult(
                success = false,
                results = emptyList(),
                error = "百科搜索出错: ${e.message}"
            )
        }
    }

    fun searchWithFallback(query: String): String {
        val result = search(query)
        return if (result.success && result.results.isNotEmpty()) {
            result.results.joinToString("\n") { "${it.title}: ${it.snippet}" }
        } else {
            ""
        }
    }

    private fun parseBingResults(html: String): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        try {
            val titlePattern = "<h2[^>]*>.*?<a[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>.*?</h2>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val snippetPattern = "<p[^>]*>(.*?)</p>".toRegex(RegexOption.DOT_MATCHES_ALL)

            val titles = titlePattern.findAll(html).take(5).toList()
            val snippets = snippetPattern.findAll(html).take(5).toList()

            for (i in titles.indices) {
                val titleMatch = titles[i]
                val link = titleMatch.groupValues[1]
                val title = stripHtml(titleMatch.groupValues[2])
                val snippet = if (i < snippets.size) stripHtml(snippets[i].groupValues[1]) else ""

                results.add(SearchResultItem(title = title, snippet = snippet, link = link))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun extractBaikeDescription(html: String): String {
        return try {
            val pattern = "<meta name=\"description\" content=\"([^\"]*)\"".toRegex()
            val match = pattern.find(html)
            match?.groupValues?.get(1) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun stripHtml(html: String): String {
        return html.replace("<[^>]*>".toRegex(), "")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}

data class SearchResult(
    val success: Boolean,
    val results: List<SearchResultItem>,
    val error: String? = null
)

data class SearchResultItem(
    val title: String,
    val snippet: String,
    val link: String
)
