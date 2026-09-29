package com.novelai.assistant.data.fetcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 番茄小说抓取器：
 * - 目录：web 页面 fanqienovel.com/page/{bookId}（需桌面 UA，TLS 指纹可能拦截）
 * - 正文：第三方中转 API（/api/raw_full?item_id=），地址可配置
 */
@Singleton
class FanqieFetcher @Inject constructor() {

    var relayBaseUrl: String = DEFAULT_RELAY

    suspend fun fetchBook(bookIdOrUrl: String): Result<FetchedBook> = withContext(Dispatchers.IO) {
        runCatching {
            val id = extractBookId(bookIdOrUrl)
                ?: error("无法识别书籍 ID，请输入数字 ID 或 fanqienovel.com/page/xxx 链接")
            val url = "https://fanqienovel.com/page/$id"
            val doc = Jsoup.connect(url)
                .userAgent(DESKTOP_UA)
                .timeout(20_000)
                .get()

            val title = doc.selectFirst("h1")?.text()?.trim()
                ?: doc.title().substringBefore("-").trim().ifBlank { "未命名" }
            val author = doc.selectFirst(".author-name-text, .author a, [class*=author]")?.text()?.trim() ?: ""

            val chapters = mutableListOf<FetchedChapter>()
            for (a in doc.select("div.chapter a, .chapter-item a, a[href*=/reader/]")) {
                val href = a.absUrl("href").ifBlank { "https://fanqienovel.com${a.attr("href")}" }
                val itemId = Regex("/reader/(\\d+)").find(href)?.groupValues?.get(1) ?: continue
                val t = a.text().trim()
                if (t.isNotBlank()) chapters.add(FetchedChapter(itemId, t, ""))
            }
            if (chapters.isEmpty()) error("目录解析失败：页面可能需要验证或结构已变化")
            FetchedBook(id, title, author, chapters)
        }
    }

    /** 正文走中转 API，返回 {code:200, data:{content:"<p>..."}} */
    suspend fun fetchChapterContent(itemId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val relay = relayBaseUrl.trimEnd('/')
            val conn = URL("$relay/api/raw_full?item_id=$itemId").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", DESKTOP_UA)
            val body = conn.inputStream.use { it.readBytes().decodeToString() }
            if (conn.responseCode !in 200..299) error("中转服务 HTTP ${conn.responseCode}")

            val json = org.json.JSONObject(body)
            val code = json.optInt("code", -1)
            if (code != 200 && code != 0) error("中转服务返回 code=$code")
            val content = json.optJSONObject("data")?.optString("content").orEmpty()
            if (content.isBlank()) error("章节内容为空（可能为 VIP 章节）")

            Jsoup.parse(content).select("p").map { it.text().trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(Jsoup.parse(content).text()) }
        }
    }

    companion object {
        const val DEFAULT_RELAY = "http://101.35.133.34:5000"

        fun extractBookId(input: String): String? {
            val trimmed = input.trim()
            trimmed.toLongOrNull()?.let { return trimmed }
            Regex("/page/(\\d+)").find(trimmed)?.let { return it.groupValues[1] }
            Regex("(\\d{6,})").find(trimmed)?.let { return it.groupValues[1] }
            return null
        }
    }
}
