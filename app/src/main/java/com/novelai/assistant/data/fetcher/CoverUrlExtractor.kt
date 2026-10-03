package com.novelai.assistant.data.fetcher

import org.jsoup.nodes.Document

/** 从书籍页面 HTML 中提取封面图地址 */
object CoverUrlExtractor {

    fun extract(doc: Document): String? {
        val og = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        if (!og.isNullOrBlank()) return og

        val candidates = doc.select("img[src]").map { el ->
            el.absUrl("src").ifBlank { el.attr("src") }.trim()
        }.filter { it.isNotBlank() }

        val preferred = candidates.firstOrNull {
            it.contains("cover", ignoreCase = true) || it.contains("thumb", ignoreCase = true)
        }
        return (preferred ?: candidates.firstOrNull { it.startsWith("http") })
    }
}
