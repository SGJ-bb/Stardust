package com.aicompanion.search

import android.content.Context
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchEngine(context: Context) {

    private val settings = SettingsManager(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val metaClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** DDG 在国内基本不可达：只给 3 秒，超时立刻放弃，别拖慢整次搜索 */
    private val ddgClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /**
     * 搜索调度（按“实际可达性”排序，不再按配置项硬排）。
     *
     * 实测（国内网络）：DuckDuckGo 的 html 端点直接超时不可达，
     * 原来把它排在第一位，等于每次查询先白等 5s 再回退。
     * 现在的顺序：Bing API（有 key）→ Bing 网页（快）→ 百度（全）→ DDG（兜底）。
     */
    fun search(query: String): SearchResult {
        val t0 = System.currentTimeMillis()
        val r = searchInternal(query)
        AppLogger.i(
            "WebSearchEngine",
            "搜索「${query.take(20)}」源=${r.error ?: "ok"} 条数=${r.results.size} 耗时=${System.currentTimeMillis() - t0}ms"
        )
        return r
    }

    private fun searchInternal(query: String): SearchResult {
        val provider = settings.searchProvider
        val hasApiKey = settings.searchEnabled && settings.searchApiKey.isNotBlank()

        // 1) 配了 Bing API：最稳，直接用
        if (hasApiKey) {
            val r = searchBingApi(query)
            if (r.success && r.results.isNotEmpty()) return r
        }

        // 2) 用户显式选了百度：优先它
        if (provider == "baidu") {
            val r = searchBaidu(query)
            if (r.success && r.results.isNotEmpty()) return r
        }

        // 3) Bing 网页（国内可达、响应快）
        val bing = searchBingFallback(query)
        if (bing.success && bing.results.isNotEmpty()) return bing

        // 4) 百度兜底
        val baidu = searchBaidu(query)
        if (baidu.success && baidu.results.isNotEmpty()) return baidu

        // 5) DuckDuckGo：国内通常不可达，只做最后兜底
        return searchDuckDuckGo(query)
    }

    fun searchAndSummarize(query: String): String {
        val result = search(query)
        if (result.success && result.results.isNotEmpty()) {
            val emptySnippets = result.results.count { it.snippet.isBlank() }
            val sb = StringBuilder()
            sb.appendLine("搜索结果（最新、最相关的内容）：")
            result.results.take(5).forEachIndexed { i, item ->
                val snippet = item.snippet.ifBlank {
                    fetchPageMeta(item.link) ?: ""
                }
                sb.appendLine("${i + 1}. ${item.title}")
                sb.appendLine("   链接：${item.link}")
                if (snippet.isNotBlank()) {
                    sb.appendLine("   摘要：${snippet.take(300)}")
                } else if (item.link.isNotBlank()) {
                    sb.appendLine("   （请根据链接中的网页URL和标题自行判断内容，如无法确定请告知用户）")
                }
            }
            if (emptySnippets > 0) {
                com.aicompanion.util.AppLogger.d("WebSearchEngine", "searchAndSummarize: $emptySnippets/${result.results.size} snippets empty, fetched from pages")
            }
            return sb.toString().trimEnd()
        }

        val baike = searchBaike(query)
        if (baike.success && baike.results.isNotEmpty()) {
            val item = baike.results[0]
            return "${item.title}\n链接：${item.link}\n摘要：${item.snippet.take(500)}"
        }

        return "没搜到相关内容，换个关键词试试"
    }

    private fun fetchPageMeta(url: String): String? {
        if (url.isBlank()) return null
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .get()
                .build()

            metaClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string()?.take(50000) ?: return null

                val metaDesc = Regex("<meta[^>]*name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)
                if (!metaDesc.isNullOrBlank()) return metaDesc

                val ogDesc = Regex("<meta[^>]*property=[\"']og:description[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)
                if (!ogDesc.isNullOrBlank()) return ogDesc

                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun searchBingApi(query: String): SearchResult {
        return try {
            val apiKey = settings.searchApiKey
            val apiUrl = settings.searchApiUrl.ifBlank { "https://api.bing.microsoft.com/v7.0/search" }
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$apiUrl?q=$encodedQuery&mkt=zh-CN&count=5"

            val request = Request.Builder()
                .url(url)
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("User-Agent", "AICompanion/1.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return SearchResult(success = false, results = emptyList(), error = "Bing API HTTP ${response.code}")
                }

                val json = JSONObject(response.body?.string() ?: "{}")
                val webPages = json.optJSONObject("webPages")
                val values = webPages?.optJSONArray("value") ?: JSONArray()
                val results = mutableListOf<SearchResultItem>()

                for (i in 0 until values.length()) {
                    val item = values.getJSONObject(i)
                    results.add(SearchResultItem(
                        title = item.optString("name", ""),
                        snippet = item.optString("snippet", ""),
                        link = item.optString("url", "")
                    ))
                }

                SearchResult(success = results.isNotEmpty(), results = results)
            }
        } catch (e: Exception) {
            searchDuckDuckGo(query)
        }
    }

    private fun searchBaidu(query: String): SearchResult {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.baidu.com/s?wd=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return SearchResult(success = false, results = emptyList(), error = "Bing API HTTP ${response.code}")
                }

                val html = response.body?.string() ?: ""
                val results = parseBaiduResults(html)
                SearchResult(success = results.isNotEmpty(), results = results)
            }
        } catch (e: Exception) {
            SearchResult(success = false, results = emptyList(), error = "百度搜索失败: ${e.message}")
        }
    }

    private fun searchDuckDuckGo(query: String): SearchResult {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            ddgClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return SearchResult(success = false, results = emptyList(), error = "搜索服务暂时不可用")
                }

                val html = response.body?.string() ?: ""
                val results = parseDuckDuckGoResults(html)
                SearchResult(success = true, results = results)
            }
        } catch (e: Exception) {
            SearchResult(success = false, results = emptyList(), error = "搜索出错: ${e.message}")
        }
    }

    private fun searchBingFallback(query: String): SearchResult {
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
                    return SearchResult(success = false, results = emptyList())
                }

                val html = response.body?.string() ?: ""
                val results = parseBingResults(html)
                SearchResult(success = true, results = results)
            }
        } catch (e: Exception) {
            SearchResult(success = false, results = emptyList())
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
                    return SearchResult(success = false, results = emptyList(), error = "百科服务暂时不可用")
                }

                val html = response.body?.string() ?: ""
                val description = extractBaikeDescription(html)

                SearchResult(
                    success = true,
                    results = listOf(SearchResultItem(
                        title = "$query - 百度百科",
                        snippet = description,
                        link = url
                    ))
                )
            }
        } catch (e: Exception) {
            SearchResult(success = false, results = emptyList(), error = "百科搜索出错: ${e.message}")
        }
    }

    private fun parseDuckDuckGoResults(html: String): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        try {
            val resultBlocks = html.split("class=\"result\"").drop(1).take(5)
            for (block in resultBlocks) {
                val titleMatch = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).find(block)
                val snippetMatch = Regex("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).find(block)
                if (titleMatch != null) {
                    results.add(SearchResultItem(
                        title = stripHtml(titleMatch.groupValues[2]),
                        snippet = snippetMatch?.let { stripHtml(it.groupValues[1]) } ?: "",
                        link = titleMatch.groupValues[1]
                    ))
                }
            }
        } catch (_: Exception) {}
        return results.ifEmpty { parseBingResults(html) }
    }

    /**
     * 解析 Bing 结果页。
     *
     * 原来用全局正则「h2 + 后面的第一个 <p>」，实测一页只能命中 2 条，
     * 且标题与摘要会错位（标题和摘要取自不同结果）。
     * 改成按 `class="b_algo"` 分块（每块就是一条结果），块内再取标题/摘要/链接。
     */
    private fun parseBingResults(html: String): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        val seen = mutableSetOf<String>()
        try {
            val blocks = html.split("class=\"b_algo").drop(1).take(12)
            for (block in blocks) {
                val linkMatch = Regex("<a[^>]*href=\"(https?://[^\"]+)\"", RegexOption.IGNORE_CASE)
                    .find(block) ?: continue
                val link = linkMatch.groupValues[1]
                    .substringBefore("&amp;")
                    .let { if (it.startsWith("https://www.bing.com/ck/")) "" else it }
                if (link.isBlank() || !seen.add(link)) continue

                val titleRaw = Regex("<h2[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL).find(block)
                    ?.let { stripHtml(it.groupValues[1]) }
                    .orEmpty()
                val snippetRaw = listOf(
                    "<div[^>]*class=\"b_caption\"[^>]*>(.*?)</div>",
                    "<p[^>]*>(.*?)</p>",
                ).firstNotNullOfOrNull { p ->
                    Regex(p, RegexOption.DOT_MATCHES_ALL).find(block)?.let { stripHtml(it.groupValues[1]) }
                }.orEmpty()

                val title = titleRaw.ifBlank { link.substringAfter("//").substringBefore("/") }
                if (title.isBlank()) continue
                results.add(SearchResultItem(title = title, snippet = snippetRaw, link = link))
                if (results.size >= 8) break
            }
        } catch (_: Exception) {}
        return results
    }

    private fun parseBaiduResults(html: String): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        try {
            val resultBlocks = html.split("class=\"result").drop(1).take(5)
            for (block in resultBlocks) {
                val titleMatch = Regex("<h3[^>]*>.*?<a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).find(block)
                    ?: Regex("<a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).find(block)
                val linkMatch = Regex("href=\"([^\"]*)\"", RegexOption.DOT_MATCHES_ALL).find(block)
                val snippetMatch = Regex("<span[^>]*class=\"content-right_[^\"]*\"[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL).find(block)
                    ?: Regex("<div[^>]*class=\"c-abstract\"[^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL).find(block)

                if (titleMatch != null) {
                    results.add(SearchResultItem(
                        title = stripHtml(titleMatch.groupValues[1]),
                        snippet = snippetMatch?.let { stripHtml(it.groupValues[1]) } ?: "",
                        link = linkMatch?.groupValues?.get(1) ?: ""
                    ))
                }
            }
        } catch (_: Exception) {}
        return results.ifEmpty { parseBingResults(html) }
    }

    private fun extractBaikeDescription(html: String): String {
        return try {
            val pattern = "<meta name=\"description\" content=\"([^\"]*)\"".toRegex()
            pattern.find(html)?.groupValues?.get(1) ?: ""
        } catch (_: Exception) { "" }
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