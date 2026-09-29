package com.novelai.assistant.data.fetcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

data class FetchedChapter(val url: String, val title: String, val volume: String)

data class FetchedBook(
    val bookId: String,
    val title: String,
    val author: String,
    val chapters: List<FetchedChapter>
)

/** SF 菠萝包轻小说抓取器：book.sfacg.com 明文 HTML；带登录 Cookie 可抓自己已订阅的付费章节 */
@Singleton
class SfacgFetcher @Inject constructor() {

    /** 登录 Cookie（可选）：填入后可抓取该账号已订阅的 VIP 章节 */
    var cookie: String? = null

    private fun connection(url: String) = Jsoup.connect(url)
        .userAgent(DESKTOP_UA)
        .timeout(20_000)
        .apply {
            cookie?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
        }

    suspend fun fetchBook(bookIdOrUrl: String): Result<FetchedBook> = withContext(Dispatchers.IO) {
        runCatching {
            val id = extractBookId(bookIdOrUrl)
                ?: error("无法识别书籍 ID，请输入数字 ID 或 book.sfacg.com 书籍链接")
            val url = "https://book.sfacg.com/Novel/$id/MainIndex/"
            val doc = connection(url).get()

            val title = doc.selectFirst("h1")?.text()?.trim()
                ?: doc.title().substringBefore("_").substringBefore("-").trim().ifBlank { "未命名" }
            val author = doc.selectFirst(".book-info a, .author-name, [property=og:novel:author]")?.text()?.trim() ?: ""

            val chapters = mutableListOf<FetchedChapter>()
            var currentVolume = ""
            val hasCookie = !cookie.isNullOrBlank()
            // 全部卷容器（每卷一个 .story-catalog），卷标题与章节链接交替出现
            val catalogEls = if (doc.select(".story-catalog").isNotEmpty())
                doc.select(".story-catalog .catalog-title, .story-catalog .catalog-list li a")
            else doc.select(".catalog-title, .catalog-list li a")
            for (el in catalogEls) {
                if (el.hasClass("catalog-title") || el.tagName() == "h2") {
                    currentVolume = el.text().trim()
                } else if (el.tagName() == "a") {
                    val href = el.absUrl("href").ifBlank { "https://book.sfacg.com${el.attr("href")}" }
                    // 未登录时 VIP 章节无法抓取正文，跳过；带 Cookie 时保留（已订阅章节可抓）
                    if (href.contains("/vip") && !hasCookie) continue
                    val t = el.text().trim()
                    if (t.isNotBlank()) chapters.add(FetchedChapter(href, t, currentVolume))
                }
            }
            if (chapters.isEmpty()) error("目录解析失败（页面结构可能已变化）")
            FetchedBook(id, title, author, chapters)
        }
    }

    suspend fun fetchChapterContent(url: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = connection(url).get()
            val isVip = url.contains("/vip")
            doc.select("script,style,.article-options,.bar-bottom").remove()
            val paragraphs = doc.select(".article-content p").map { it.text().trim() }
                .filter { it.isNotBlank() }
            if (paragraphs.isEmpty()) {
                if (isVip) error("本章节需要登录 Cookie 且账号已订阅")
                doc.body()?.text()?.split(Regex("(?<=[。！？!?])"))?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()
            } else paragraphs
        }
    }

    companion object {
        fun extractBookId(input: String): String? {
            val trimmed = input.trim()
            trimmed.toIntOrNull()?.let { return trimmed }
            Regex("Novel/(\\d+)").find(trimmed)?.let { return it.groupValues[1] }
            Regex("(\\d{4,})").find(trimmed)?.let { return it.groupValues[1] }
            return null
        }
    }
}
