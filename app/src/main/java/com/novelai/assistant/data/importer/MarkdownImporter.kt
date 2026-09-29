package com.novelai.assistant.data.importer

import com.novelai.assistant.data.db.BookSourceType
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Markdown 导入：按 1-2 级标题切章 */
@Singleton
class MarkdownImporter @Inject constructor() : BookImporter {

    override fun sourceType(): BookSourceType = BookSourceType.LOCAL_MD

    private val headingRegex = Regex("^#{1,2}[ \\t]+(.+?)[ \\t]*#*\\s*$", RegexOption.MULTILINE)

    override suspend fun parse(inputStream: InputStream, fileName: String): ParsedBook {
        val text = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        val title = cleanTitle(fileName)
        val matches = headingRegex.findAll(text).toList()

        val chapters = if (matches.size < 2) {
            listOf(ParsedChapter(title, text.trim()))
        } else {
            val result = mutableListOf<ParsedChapter>()
            val first = matches.first()
            val before = text.substring(0, first.range.first).trim()
            if (before.isNotEmpty()) result.add(ParsedChapter(title, before))
            for (i in matches.indices) {
                val m = matches[i]
                val start = m.range.last + 1
                val end = matches.getOrNull(i + 1)?.range?.first ?: text.length
                val body = text.substring(start, end).trim()
                result.add(ParsedChapter(m.groupValues[1].trim(), body))
            }
            result
        }
        return ParsedBook(
            title = title,
            author = "",
            chapters = chapters.filter { it.content.isNotBlank() || it.title.isNotBlank() },
            sourceType = sourceType(),
            sourcePath = fileName
        )
    }

    private fun cleanTitle(fileName: String): String =
        fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').ifBlank { "未命名" }
}
