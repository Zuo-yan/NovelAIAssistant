package com.novelai.assistant.data.importer

import com.novelai.assistant.data.db.BookSourceType
import org.mozilla.universalchardet.UniversalDetector
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TXT 导入：BOM / juniversalchardet 编码探测（中文常见 GBK/GB18030），
 * 正则智能分章（第X章/卷、Chapter N、序章/楔子/番外等），失败时按体积分块兜底。
 */
@Singleton
class TxtImporter @Inject constructor() : BookImporter {

    override fun sourceType(): BookSourceType = BookSourceType.LOCAL_TXT

    private val chapterRegex = Regex(
        "^[ \\t\\u3000]*(?:" +
            "第\\s*[0-9零〇一二三四五六七八九十百千万两]+\\s*[章回节卷部集篇话幕]" +
            "|Chapter\\s+\\d+" +
            "|序章|序言|楔子|引子|前言|后记|尾声|终章|番外[\\s\\S]{0,24}" +
            ")[^\\n]{0,60}[ \\t\\u3000]*$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    override suspend fun parse(inputStream: InputStream, fileName: String): ParsedBook {
        val bytes = inputStream.use { it.readBytes() }
        val text = decodeText(bytes)
        val chapters = splitChapters(text)
            .ifEmpty { fallbackChunks(text) }
            .ifEmpty { listOf(ParsedChapter(cleanTitle(fileName), text.trim())) }
        return ParsedBook(
            title = cleanTitle(fileName),
            author = "",
            chapters = chapters,
            sourceType = sourceType(),
            sourcePath = fileName
        )
    }

    internal fun decodeText(bytes: ByteArray): String {
        bytes.takeIf { it.size >= 3 }?.let { b ->
            when {
                b[0] == 0xEF.b() && b[1] == 0xBB.b() && b[2] == 0xBF.b() ->
                    return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
                b[0] == 0xFF.b() && b[1] == 0xFE.b() ->
                    return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
                b[0] == 0xFE.b() && b[1] == 0xFF.b() ->
                    return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
        }
        val probeSize = minOf(bytes.size, 2_000_000)
        val detector = UniversalDetector(null)
        detector.handleData(bytes, 0, probeSize)
        detector.dataEnd()
        val detected = detector.detectedCharset
        detector.reset()

        // juniversalchardet 对短中文 GBK 文本常误判为单字节拉丁编码，这类结果不可信
        val isUntrustworthyLatin = detected != null && (
            detected.startsWith("windows-125", true) ||
                detected.startsWith("ISO-8859", true) ||
                detected.equals("MacRoman", true) ||
                detected.equals("TIS-620", true)
            )

        return when {
            isUntrustworthyLatin -> strictUtf8OrGbk(bytes)
            detected == null || detected.equals("UTF-8", true) -> strictUtf8OrGbk(bytes)
            else -> runCatching { String(bytes, charsetOf(detected)) }.getOrElse { strictUtf8OrGbk(bytes) }
        }
    }

    /** 先尝试严格 UTF-8（存在非法序列即失败），失败则按 GB18030（GBK 超集）解码 */
    private fun strictUtf8OrGbk(bytes: ByteArray): String {
        val strictUtf8 = java.nio.charset.Charset.forName("UTF-8").newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return runCatching { strictUtf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, charsetOf("GB18030")) }
    }

    private fun charsetOf(name: String): java.nio.charset.Charset =
        if (name.equals("GB18030", true) || name.equals("GBK", true) || name.equals("GB2312", true))
            java.nio.charset.Charset.forName("GB18030") else java.nio.charset.Charset.forName(name)

    internal fun splitChapters(text: String): List<ParsedChapter> {
        val matches = chapterRegex.findAll(text).toList()
        if (matches.size < 3) return emptyList()

        val chapters = mutableListOf<ParsedChapter>()
        // 第一个匹配之前如有正文，归属到第一章
        val first = matches.first()
        val prologue = text.substring(0, first.range.first).trim()
        val firstBody = text.substring(first.range.last + 1, matches.getOrNull(1)?.range?.first ?: text.length).trim()
        chapters.add(ParsedChapter(first.value.trim(), (prologue + "\n\n" + firstBody).trim()))

        for (i in 1 until matches.size) {
            val m = matches[i]
            val start = m.range.last + 1
            val end = matches.getOrNull(i + 1)?.range?.first ?: text.length
            val body = text.substring(start, end).trim()
            chapters.add(ParsedChapter(m.value.trim(), body))
        }
        return chapters.filter { it.title.isNotBlank() }
    }

    /** 无章节匹配时按约 1.2 万字符在段落边界分块 */
    fun fallbackChunks(text: String): List<ParsedChapter> {
        val paragraphs = text.split(Regex("\n+"))
        val chunks = mutableListOf<StringBuilder>()
        var current = StringBuilder()
        var size = 0
        for (p in paragraphs) {
            current.append(p).append("\n\n")
            size += p.length
            if (size >= 12_000) {
                chunks.add(current); current = StringBuilder(); size = 0
            }
        }
        if (current.isNotBlank()) chunks.add(current)
        return chunks.filter { it.isNotBlank() }.mapIndexed { i, sb ->
            ParsedChapter("第 ${i + 1} 部分", sb.toString().trim())
        }
    }

    private fun cleanTitle(fileName: String): String =
        fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').ifBlank { "未命名" }

    private fun Int.b(): Byte = toByte()
}
