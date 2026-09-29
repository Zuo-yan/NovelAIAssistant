package com.novelai.assistant.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterDao
import com.novelai.assistant.data.db.BookDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 导入调度器：识别扩展名 → 分发解析器 → 落库 */
@Singleton
class BookImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val txtImporter: TxtImporter,
    private val markdownImporter: MarkdownImporter,
    private val epubImporter: EpubImporter,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao
) {
    sealed class ImportResult {
        data class Success(val bookId: String, val title: String, val chapterCount: Int) : ImportResult()
        data class Failure(val message: String) : ImportResult()
    }

    suspend fun importFromUri(uri: Uri): ImportResult {
        return try {
            val fileName = queryDisplayName(uri) ?: return ImportResult.Failure("无法读取文件名")
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val importer = when (ext) {
                "txt" -> txtImporter
                "md", "markdown" -> markdownImporter
                "epub" -> epubImporter
                else -> return ImportResult.Failure("暂不支持 .$ext 格式（支持 txt / md / epub）")
            }
            val parsed = context.contentResolver.openInputStream(uri)?.use {
                importer.parse(it, fileName)
            } ?: return ImportResult.Failure("无法打开文件")

            if (parsed.chapters.isEmpty()) return ImportResult.Failure("解析失败：未找到有效章节内容")

            val bookId = UUID.randomUUID().toString()
            var coverPath: String? = null
            parsed.coverBytes?.let { bytes ->
                runCatching {
                    val dir = File(context.filesDir, "covers").apply { mkdirs() }
                    val coverFile = File(dir, "$bookId.img")
                    coverFile.writeBytes(bytes)
                    coverPath = coverFile.absolutePath
                }
            }

            val book = BookEntity(
                id = bookId,
                title = parsed.title.ifBlank { fileName },
                author = parsed.author,
                coverUri = coverPath,
                sourcePath = parsed.sourcePath,
                sourceType = parsed.sourceType,
                totalChapters = parsed.chapters.size,
                createdAt = System.currentTimeMillis()
            )
            val entities = parsed.chapters.mapIndexed { i, c ->
                ChapterEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    chapterIndex = i,
                    title = c.title,
                    content = c.content,
                    originType = com.novelai.assistant.data.db.ChapterOriginType.ORIGINAL
                )
            }
            bookDao.insert(book)
            chapterDao.insertAll(entities)
            ImportResult.Success(bookId, book.title, entities.size)
        } catch (t: Throwable) {
            ImportResult.Failure("导入失败：${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) {
                    return cursor.getString(nameIdx)
                }
            }
        }
        return uri.lastPathSegment
    }

    companion object {
        fun sourceTypeLabel(type: BookSourceType): String = when (type) {
            BookSourceType.LOCAL_TXT -> "TXT"
            BookSourceType.LOCAL_MD -> "MD"
            BookSourceType.LOCAL_EPUB -> "EPUB"
            BookSourceType.FANQIE -> "番茄"
            BookSourceType.BOLUOBAO -> "菠萝包"
        }
    }
}
