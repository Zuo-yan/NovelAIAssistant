package com.novelai.assistant.data.importer

import com.novelai.assistant.data.db.BookSourceType
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPUB 导入：container.xml → OPF → spine 顺序解析，jsoup 提取正文与封面。
 * 结构异常时宽容降级（缺标题用「第 N 节」）。
 */
@Singleton
class EpubImporter @Inject constructor() : BookImporter {

    override fun sourceType(): BookSourceType = BookSourceType.LOCAL_EPUB

    override suspend fun parse(inputStream: InputStream, fileName: String): ParsedBook {
        val tmp = File.createTempFile("novelai_epub", ".zip")
        try {
            inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            ZipFile(tmp).use { zip ->
                return parseZip(zip, fileName)
            }
        } finally {
            tmp.delete()
        }
    }

    private fun parseZip(zip: ZipFile, fileName: String): ParsedBook {
        var title = cleanTitle(fileName)
        var author = ""
        val chapters = mutableListOf<ParsedChapter>()
        var coverBytes: ByteArray? = null

        val containerEntry = zip.getEntry("META-INF/container.xml")
            ?: return fallback(zip, title)
        val containerXml = zip.getInputStream(containerEntry).use { it.readBytes().decodeToString() }
        val opfPath = Jsoup.parse(containerXml, "", Parser.xmlParser())
            .getElementsByTag("rootfile").firstOrNull()?.attr("full-path")
            ?: return fallback(zip, title)

        val opfEntry = zip.getEntry(opfPath) ?: return fallback(zip, title)
        val opfBaseDir = opfPath.substringBeforeLast('/', "")
        val opfXml = zip.getInputStream(opfEntry).use { it.readBytes().decodeToString() }
        val opf = Jsoup.parse(opfXml, "", Parser.xmlParser())

        opf.getElementsByTag("title").firstOrNull()?.text()?.takeIf { it.isNotBlank() }?.let { title = it.trim() }
        opf.getElementsByTag("creator").firstOrNull()?.text()?.takeIf { it.isNotBlank() }?.let { author = it.trim() }

        // manifest: id → (href, mediaType, properties)
        data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)
        val manifest = opf.getElementsByTag("item").mapNotNull { el ->
            val id = el.attr("id")
            val href = el.attr("href")
            if (id.isBlank() || href.isBlank()) null
            else ManifestItem(id, href, el.attr("media-type"), el.attr("properties"))
        }
        val byId = manifest.associateBy { it.id }

        // 封面：properties 含 cover-image，或 id 含 cover 的图片
        val coverItem = manifest.firstOrNull { it.properties.contains("cover-image") }
            ?: manifest.firstOrNull { it.id.contains("cover", true) && it.mediaType.startsWith("image/") }
        coverBytes = coverItem?.let { readZipEntry(zip, resolveHref(opfBaseDir, it.href)) }

        val spineIds = opf.getElementsByTag("itemref").map { it.attr("idref") }
        val docItems = spineIds.mapNotNull { byId[it] }
            .filter { it.mediaType.contains("html", true) || it.href.endsWith(".xhtml", true) || it.href.endsWith(".html", true) || it.href.endsWith(".htm", true) }

        var index = 0
        for (item in docItems) {
            val bytes = readZipEntry(zip, resolveHref(opfBaseDir, item.href)) ?: continue
            val doc = Jsoup.parse(bytes.decodeToString())
            doc.select("script,style,nav,header,footer").remove()
            val docTitle = doc.selectFirst("h1,h2,h3")?.text()?.trim()
                ?: doc.title().trim()
            val paragraphs: List<String> = run {
                val ps = doc.select("p")
                if (ps.isNotEmpty()) ps.map { collapse(it.text()) }
                else doc.body()?.text()?.split(Regex("(?<=[。！？!?])\\s*"))?.map { collapse(it) } ?: emptyList()
            }.filter { it.isNotBlank() }
            if (paragraphs.isEmpty()) continue
            index++
            chapters.add(
                ParsedChapter(
                    title = docTitle.ifBlank { "第 $index 节" },
                    content = paragraphs.joinToString("\n\n")
                )
            )
        }

        if (chapters.isEmpty()) return fallback(zip, title)
        return ParsedBook(title, author, coverBytes, chapters, sourceType(), fileName)
    }

    private fun fallback(zip: ZipFile, title: String): ParsedBook {
        // 兜底：抓取 zip 内所有 html/xhtml 按文件名顺序拼接为单章
        val allText = zip.entries().asSequence()
            .filter { !it.isDirectory && (it.name.endsWith(".xhtml") || it.name.endsWith(".html")) }
            .sortedBy { it.name }
            .mapNotNull { entry ->
                runCatching {
                    val doc = Jsoup.parse(zip.getInputStream(entry).use { it.readBytes().decodeToString() })
                    doc.select("script,style").remove()
                    doc.body()?.text()
                }.getOrNull()
            }
            .filter { !it.isNullOrBlank() }
            .joinToString("\n\n") { collapse(it) }
        val chapters = if (allText.isBlank()) emptyList() else listOf(ParsedChapter(title, allText))
        return ParsedBook(title, "", null, chapters, sourceType(), title)
    }

    private fun readZipEntry(zip: ZipFile, path: String): ByteArray? {
        val entry = zip.getEntry(path) ?: return tryAlternate(zip, path)
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun tryAlternate(zip: ZipFile, path: String): ByteArray? {
        val decoded = runCatching { URLDecoder.decode(path, "UTF-8") }.getOrNull() ?: path
        if (decoded != path) return zip.getEntry(decoded)?.let { zip.getInputStream(it).use { s -> s.readBytes() } }
        return null
    }

    private fun resolveHref(baseDir: String, href: String): String {
        val decoded = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
        return if (baseDir.isBlank()) decoded else "$baseDir/$decoded"
    }

    private fun collapse(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    private fun cleanTitle(fileName: String): String =
        fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').ifBlank { "未命名" }
}
