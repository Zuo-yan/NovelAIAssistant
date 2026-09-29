package com.novelai.assistant.ui.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.prefs.PageMode
import com.novelai.assistant.data.prefs.ReaderBgTheme
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.prefs.ReadingSettings
import com.novelai.assistant.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TocFilter(val label: String) {
    ALL("全部"), ORIGINAL("仅原作"), AI("AI 衍生"), TREE("分支树")
}

data class TocItem(
    val chapter: ChapterEntity,
    val depth: Int
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readingPreferencesRepository: ReadingPreferencesRepository,
    val ttsPlayer: com.novelai.assistant.data.tts.TtsPlayer
) : ViewModel() {

    val bookId: String = savedStateHandle["bookId"] ?: ""
    private val initialChapterIndex: Int = savedStateHandle["chapter"] ?: -1

    val book: StateFlow<BookEntity?> = bookRepository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chapters: StateFlow<List<ChapterEntity>> = bookRepository.observeChapters(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<ReadingSettings> = readingPreferencesRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingSettings())

    // 初始定位到上次阅读章节
    private val _chapterIndex = MutableStateFlow(-1)
    val chapterIndex: StateFlow<Int> = _chapterIndex.asStateFlow()

    val currentChapter: StateFlow<ChapterEntity?> =
        combine(chapters, _chapterIndex) { list, idx ->
            if (idx in list.indices) list[idx] else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // UI 开关
    private val _chromeVisible = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private val _tocOpen = MutableStateFlow(false)
    val tocOpen: StateFlow<Boolean> = _tocOpen.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _tocFilter = MutableStateFlow(TocFilter.ALL)
    val tocFilter: StateFlow<TocFilter> = _tocFilter.asStateFlow()

    // 划线即问 / 选段朗读
    private val _quotedText = MutableStateFlow<String?>(null)
    val quotedText: StateFlow<String?> = _quotedText.asStateFlow()

    private val _quotedParaIndex = MutableStateFlow<Int?>(null)
    val quotedParaIndex: StateFlow<Int?> = _quotedParaIndex.asStateFlow()

    // 目录树（分支缩进）
    val tocItems: StateFlow<List<TocItem>> =
        combine(chapters, _tocFilter) { list, filter ->
            when (filter) {
                TocFilter.ALL -> list.map { TocItem(it, 0) }
                TocFilter.ORIGINAL -> list.filter { it.originType == ChapterOriginType.ORIGINAL }.map { TocItem(it, 0) }
                TocFilter.AI -> list.filter { it.originType != ChapterOriginType.ORIGINAL }.map { TocItem(it, 0) }
                TocFilter.TREE -> buildTree(list)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var progressJob: Job? = null

    init {
        if (initialChapterIndex >= 0) {
            _chapterIndex.value = initialChapterIndex
        }
        // 初始章节：书籍加载后定位到上次阅读位置
        book.onEach { b ->
            if (b != null && _chapterIndex.value == -1 && b.currentReadingChapterIndex >= 0) {
                _chapterIndex.value = b.currentReadingChapterIndex
            }
        }.launchIn(viewModelScope)
    }

    private val _targetPageOnChapterLoad = MutableStateFlow<Int?>(null)
    val targetPageOnChapterLoad: StateFlow<Int?> = _targetPageOnChapterLoad.asStateFlow()

    fun openPreviousChapter() {
        val idx = _chapterIndex.value
        if (idx > 0) {
            _targetPageOnChapterLoad.value = -1
            setChapterIndex(idx - 1)
        }
    }

    fun openNextChapter() {
        val idx = _chapterIndex.value
        if (idx < chapters.value.lastIndex) {
            _targetPageOnChapterLoad.value = 0
            setChapterIndex(idx + 1)
        }
    }

    fun consumeTargetPage(): Int? {
        val target = _targetPageOnChapterLoad.value
        _targetPageOnChapterLoad.value = null
        return target
    }

    fun setChapterIndex(index: Int) {
        if (index == _chapterIndex.value) return
        // 听书中手动切章：停止当前朗读（自动连播场景由 onTtsChapterEnd 自行驱动）
        if (ttsPlayer.speaking.value || ttsPlayer.paused.value) ttsPlayer.stop()
        _chapterIndex.value = index
        scheduleProgressSave()
    }

    // ---- 听书（TTS） ----
    val ttsReady = ttsPlayer.ready
    val ttsSpeaking = ttsPlayer.speaking
    val ttsPaused = ttsPlayer.paused
    val ttsPosition = ttsPlayer.position

    private val _ttsSpeed = MutableStateFlow(1.0f)
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    init {
        // 加载持久化的语速
        viewModelScope.launch {
            val saved = readingPreferencesRepository.currentTtsSpeed()
            _ttsSpeed.value = saved
            ttsPlayer.setRate(saved)
        }
    }

    fun setTtsSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3f)
        _ttsSpeed.value = clamped
        ttsPlayer.setRate(clamped)
        launchPref { readingPreferencesRepository.setTtsSpeed(clamped) }
    }

    /** 开始/停止朗读当前章；支持指定起始段落，读毕自动连播下一章 */
    fun toggleTts(startIndex: Int = 0) {
        if (ttsPlayer.speaking.value || ttsPlayer.paused.value) {
            ttsPlayer.stop()
        } else {
            startTts(startIndex)
        }
    }

    fun startTts(startIndex: Int = 0) {
        val chapter = currentChapter.value ?: return
        val paragraphs = Paginator.splitParagraphs(chapter.content)
        val validIndex = startIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        ttsPlayer.play(paragraphs, startIndex = validIndex) { onTtsChapterEnd() }
    }

    fun pauseTts() = ttsPlayer.pause()
    fun resumeTts() = ttsPlayer.resume()
    fun stopTts() {
        if (ttsPlayer.speaking.value || ttsPlayer.paused.value) ttsPlayer.stop()
    }

    private fun onTtsChapterEnd() {
        val next = _chapterIndex.value + 1
        if (next <= chapters.value.lastIndex) {
            _chapterIndex.value = next
            scheduleProgressSave()
            viewModelScope.launch {
                kotlinx.coroutines.delay(400) // 等 chapters/currentChapter 刷新
                startTts(0)
            }
        }
    }

    override fun onCleared() {
        ttsPlayer.stop()
        // 兜底保存
        val idx = _chapterIndex.value
        if (idx >= 0) {
            kotlinx.coroutines.runBlocking {
                runCatching { bookRepository.saveProgress(bookId, idx, chapters.value.size) }
            }
        }
        super.onCleared()
    }

    fun toggleChrome() { _chromeVisible.value = !_chromeVisible.value }

    fun openToc() { _tocOpen.value = true; _chromeVisible.value = false }
    fun closeToc() { _tocOpen.value = false }
    fun openSettings() { _settingsOpen.value = true }
    fun closeSettings() { _settingsOpen.value = false }

    fun setTocFilter(filter: TocFilter) { _tocFilter.value = filter }

    fun quoteParagraph(text: String, paraIndex: Int? = null) {
        _quotedText.value = text
        _quotedParaIndex.value = paraIndex
    }

    fun clearQuote() {
        _quotedText.value = null
        _quotedParaIndex.value = null
    }

    fun playFromQuotedParagraph() {
        val idx = _quotedParaIndex.value ?: 0
        clearQuote()
        startTts(idx)
    }

    fun scheduleProgressSave() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(600)
            val idx = _chapterIndex.value
            val total = chapters.value.size
            if (idx >= 0 && total > 0) bookRepository.saveProgress(bookId, idx, total)
        }
    }

    // ---- 阅读设置 ----
    fun setFontSize(size: Int) = launchPref { readingPreferencesRepository.setFontSize(size) }
    fun setLineSpacing(m: Float) = launchPref { readingPreferencesRepository.setLineSpacing(m) }
    fun setHorizontalPadding(dp: Int) = launchPref { readingPreferencesRepository.setHorizontalPadding(dp) }
    fun setParagraphSpacing(dp: Int) = launchPref { readingPreferencesRepository.setParagraphSpacing(dp) }
    fun setPageMode(mode: PageMode) = launchPref { readingPreferencesRepository.setPageMode(mode) }
    fun setBgTheme(theme: ReaderBgTheme) = launchPref { readingPreferencesRepository.setBgTheme(theme) }
    fun setSerif(serif: Boolean) = launchPref { readingPreferencesRepository.setSerif(serif) }

    private var lastLightTheme = ReaderBgTheme.WHITE

    /** 夜间模式快捷切换：亮色 ↔ 暗夜（记住离开前的亮色主题） */
    fun toggleNightMode() = launchPref {
        val cur = readingPreferencesRepository.current().bgTheme
        if (cur == ReaderBgTheme.DARK || cur == ReaderBgTheme.BLACK) {
            readingPreferencesRepository.setBgTheme(lastLightTheme)
        } else {
            lastLightTheme = cur
            readingPreferencesRepository.setBgTheme(ReaderBgTheme.DARK)
        }
    }

    private fun launchPref(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // ---- 导出 ----
    fun exportChapter(uri: Uri?, markdown: Boolean) {
        if (uri == null) return
        val chapter = currentChapter.value ?: return
        viewModelScope.launch {
            runCatching {
                val text = if (markdown) {
                    "# ${chapter.title}\n\n${chapter.content}"
                } else {
                    "${chapter.title}\n\n${chapter.content}"
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    companion object {
        private fun buildTree(list: List<ChapterEntity>): List<TocItem> {
            val byId = list.associateBy { it.id }
            fun depth(chapter: ChapterEntity): Int {
                var d = 0
                var cur = chapter
                while (cur.parentChapterId != null) {
                    d++
                    cur = byId[cur.parentChapterId] ?: break
                    if (d > 20) break
                }
                return d
            }
            // 树形排序：原作章节依次展开，AI 章节紧跟其父章节之后
            val result = mutableListOf<TocItem>()
            val processed = mutableSetOf<String>()
            for (chapter in list) {
                if (chapter.id in processed) continue
                result.add(TocItem(chapter, depth(chapter)))
                processed.add(chapter.id)
                // 追加所有以它为父的衍生章节（递归）
                fun appendChildren(parent: ChapterEntity) {
                    val children = list.filter { it.parentChapterId == parent.id }
                    for (child in children) {
                        if (child.id in processed) continue
                        result.add(TocItem(child, depth(child)))
                        processed.add(child.id)
                        appendChildren(child)
                    }
                }
                appendChildren(chapter)
            }
            // 兜底：遗漏的章节补在末尾
            list.filter { it.id !in processed }.forEach { result.add(TocItem(it, 0)) }
            return result
        }
    }
}
