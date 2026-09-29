package com.novelai.assistant.data.repository

import android.content.Context
import android.net.Uri
import com.novelai.assistant.data.db.AiChatRecordEntity
import com.novelai.assistant.data.db.BookDao
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterDao
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.CategoryDao
import com.novelai.assistant.data.db.CategoryEntity
import com.novelai.assistant.data.db.ChatDao
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.importer.BookImportManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val importManager: BookImportManager
) {
    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAll()
    fun observeBook(id: String): Flow<BookEntity?> = bookDao.observeById(id)
    fun observeChapters(bookId: String): Flow<List<ChapterEntity>> = chapterDao.observeByBook(bookId)
    fun observeChapter(id: String): Flow<ChapterEntity?> = chapterDao.observeById(id)

    suspend fun getBook(id: String): BookEntity? = bookDao.getById(id)
    suspend fun getChapters(bookId: String): List<ChapterEntity> = chapterDao.getByBook(bookId)
    suspend fun getChapter(id: String): ChapterEntity? = chapterDao.getById(id)

    suspend fun import(uri: Uri): BookImportManager.ImportResult = importManager.importFromUri(uri)

    /** 抓取器导入：直接落库在线抓取的章节 */
    suspend fun importFetched(
        title: String,
        author: String,
        sourceType: com.novelai.assistant.data.db.BookSourceType,
        chapters: List<Pair<String, String>>
    ): String {
        val bookId = java.util.UUID.randomUUID().toString()
        bookDao.insert(
            BookEntity(
                id = bookId,
                title = title,
                author = author,
                coverUri = null,
                sourcePath = title,
                sourceType = sourceType,
                totalChapters = chapters.size,
                createdAt = System.currentTimeMillis()
            )
        )
        chapterDao.insertAll(
            chapters.mapIndexed { i, (t, c) ->
                ChapterEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    chapterIndex = i,
                    title = t,
                    content = c,
                    originType = ChapterOriginType.ORIGINAL
                )
            }
        )
        return bookId
    }

    suspend fun deleteBook(bookId: String) {
        chapterDao.deleteByBook(bookId)
        bookDao.delete(bookId)
    }

    suspend fun saveProgress(bookId: String, chapterIndex: Int, totalChapters: Int) {
        val percent = if (totalChapters > 0) (chapterIndex + 1f) / totalChapters * 100f else 0f
        bookDao.updateProgress(bookId, chapterIndex, percent, System.currentTimeMillis())
    }

    suspend fun updateCategory(bookId: String, category: String) = bookDao.updateCategory(bookId, category)
    suspend fun resetCategoryBooks(categoryName: String) = bookDao.resetCategoryBooks(categoryName)

    suspend fun updateChapter(chapter: ChapterEntity) = chapterDao.update(chapter)

    suspend fun deleteChapter(chapterId: String) = chapterDao.delete(chapterId)

    /** 保存 AI 生成内容为新章节（续写 / 分支） */
    suspend fun saveGeneratedChapter(
        bookId: String,
        parentChapterId: String?,
        title: String,
        content: String,
        promptUsed: String?,
        originType: ChapterOriginType
    ): ChapterEntity {
        val nextIndex = (chapterDao.maxChapterIndex(bookId) ?: -1) + 1
        val chapter = ChapterEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            chapterIndex = nextIndex,
            title = title,
            content = content.trim(),
            originType = originType,
            parentChapterId = parentChapterId,
            promptUsed = promptUsed
        )
        chapterDao.insert(chapter)
        bookDao.getById(bookId)?.let {
            bookDao.update(it.copy(totalChapters = chapterDao.countByBook(bookId)))
        }
        return chapter
    }

    suspend fun appendToChapter(chapterId: String, extra: String) {
        val chapter = chapterDao.getById(chapterId) ?: return
        chapterDao.updateContent(chapterId, chapter.content + extra)
    }

    /**
     * 获取指定章节的完整前文剧情线（自动处理主线与平行分支宇宙）：
     * - 如果目标章节属于分支，沿 parentChapterId 逆向追溯直到最初分叉的原文主线，再向前拼接主线前文
     * - 完美剔除当前分支世界线之外的无关章节
     */
    suspend fun getChapterLineage(bookId: String, targetChapterId: String): List<ChapterEntity> {
        val all = chapterDao.getByBook(bookId)
        val target = all.firstOrNull { it.id == targetChapterId } ?: return emptyList()
        val byId = all.associateBy { it.id }
        val branchChain = mutableListOf<ChapterEntity>()
        var cur: ChapterEntity? = target
        val visited = mutableSetOf<String>()

        while (cur != null && cur.id !in visited) {
            visited.add(cur.id)
            branchChain.add(cur)
            if (cur.originType == ChapterOriginType.ORIGINAL) {
                val originalHead = all
                    .filter { it.originType == ChapterOriginType.ORIGINAL && it.chapterIndex < cur.chapterIndex }
                    .sortedBy { it.chapterIndex }
                return originalHead + branchChain.reversed()
            }
            cur = cur.parentChapterId?.let { byId[it] }
        }
        return branchChain.reversed()
    }
}

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    fun observeChat(bookId: String): Flow<List<AiChatRecordEntity>> = chatDao.observeByBook(bookId)

    suspend fun recentMessages(bookId: String, limit: Int = 12): List<AiChatRecordEntity> =
        chatDao.getRecent(bookId, limit)

    suspend fun save(record: AiChatRecordEntity) = chatDao.insert(record)

    suspend fun clear(bookId: String) = chatDao.clearByBook(bookId)
}

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()
    suspend fun addCategory(name: String) =
        categoryDao.insert(CategoryEntity(id = UUID.randomUUID().toString(), name = name.trim()))
    suspend fun deleteCategory(id: String) = categoryDao.delete(id)
    suspend fun deleteCategoryByName(name: String) = categoryDao.deleteByName(name)
}
