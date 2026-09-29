package com.novelai.assistant.data.rag

import com.novelai.assistant.data.db.ChapterEntity

data class ScoredChapter(val chapter: ChapterEntity, val score: Double)

/** 分词：CJK 连续串按字符 bigram + 单字展开；拉丁/数字串按整词。检索与建索引共用。 */
internal fun tokenizeForIndex(text: String): List<String> {
    val terms = mutableListOf<String>()
    val buf = StringBuilder()
    var bufHasCjk = false

    fun flush() {
        val s = buf.toString()
        buf.setLength(0)
        if (s.isEmpty()) return
        if (!bufHasCjk) {
            terms.add(s)
            return
        }
        if (s.length == 1) {
            terms.add(s)
        } else {
            for (i in 0 until s.length - 1) terms.add(s.substring(i, i + 2))
            for (c in s) terms.add(c.toString())
        }
    }

    for (ch in text) {
        when {
            isCjkChar(ch) -> { buf.append(ch); bufHasCjk = true }
            ch.isLetterOrDigit() -> {
                if (bufHasCjk) flush()
                buf.append(ch.lowercaseChar())
                bufHasCjk = false
            }
            else -> flush()
        }
    }
    flush()
    return terms
}

private fun isCjkChar(c: Char): Boolean {
    val block = Character.UnicodeBlock.of(c) ?: return false
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
}

/**
 * 章节级 BM25 检索索引。纯 Kotlin 实现，无需外部分词/向量模型，适合设备端长文召回。
 */
class ChapterSearchIndex private constructor(
    private val chapters: List<ChapterEntity>,
    private val postings: Map<String, Map<Int, Int>>, // term -> (docIdx -> tf)
    private val docLengths: IntArray,
    private val avgDocLength: Double
) {

    fun search(query: String, topK: Int = 3, maxChapterIndex: Int? = null): List<ScoredChapter> {
        val queryTerms = tokenizeForIndex(query).distinct()
        if (queryTerms.isEmpty()) return emptyList()

        val totalDocs = chapters.size
        val scores = HashMap<Int, Double>()

        for (term in queryTerms) {
            val docMap = postings[term] ?: continue
            val df = docMap.size.toDouble()
            val idf = kotlin.math.ln((totalDocs - df + 0.5) / (df + 0.5) + 1.0)
            for ((docIdx, tf) in docMap) {
                val chapter = chapters[docIdx]
                if (maxChapterIndex != null && chapter.chapterIndex > maxChapterIndex) continue
                val tfNorm = (tf * (BM25_K + 1)) /
                    (tf + BM25_K * (1 - BM25_B + BM25_B * docLengths[docIdx] / avgDocLength))
                scores[docIdx] = (scores[docIdx] ?: 0.0) + idf * tfNorm
            }
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .map { ScoredChapter(chapters[it.key], it.value) }
    }

    companion object {
        private const val BM25_K = 1.4
        private const val BM25_B = 0.72
        private const val MAX_DOC_CHARS = 40_000 // 超长章节截断索引，控制内存

        fun build(chapters: List<ChapterEntity>): ChapterSearchIndex {
            val postings = HashMap<String, HashMap<Int, Int>>()
            val docLengths = IntArray(chapters.size)
            var totalLength = 0L

            chapters.forEachIndexed { docIdx, chapter ->
                val text = chapter.title + "\n" + chapter.content.take(MAX_DOC_CHARS)
                val terms = tokenizeForIndex(text)
                docLengths[docIdx] = terms.size
                totalLength += terms.size
                for (term in terms) {
                    val docMap = postings.getOrPut(term) { HashMap() }
                    docMap[docIdx] = (docMap[docIdx] ?: 0) + 1
                }
            }
            val avg = if (chapters.isEmpty()) 1.0 else totalLength.toDouble() / chapters.size
            return ChapterSearchIndex(chapters, postings, docLengths, avg.coerceAtLeast(1.0))
        }
    }
}
