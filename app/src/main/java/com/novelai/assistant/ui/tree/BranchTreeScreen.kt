package com.novelai.assistant.ui.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.ui.components.EmptyState
import com.novelai.assistant.ui.components.OriginBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BranchTreeViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    bookRepository: BookRepository
) : ViewModel() {
    private val bookId: String = savedStateHandle["bookId"] ?: ""

    val book: StateFlow<BookEntity?> = bookRepository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chapters: StateFlow<List<ChapterEntity>> = bookRepository.observeChapters(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchTreeScreen(
    bookId: String,
    onOpenChapter: (chapterIndex: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: BranchTreeViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()

    val byId = chapters.associateBy { it.id }
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
    val treeItems = chapters.map { it to depth(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("剧情分支树")
                        Text(
                            "《${book?.title ?: ""}》",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (chapters.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.CallMerge,
                    title = "暂无章节",
                    hint = "在阅读页使用「AI 续写」或「What-if 分支」后\n章节树会在这里生长"
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp
                )
            ) {
                itemsIndexed(treeItems, key = { _, pair -> pair.first.id }) { _, pair ->
                    val (chapter, d) = pair
                    val isBranch = chapter.originType == ChapterOriginType.AI_BRANCH
                    val isContinuation = chapter.originType == ChapterOriginType.AI_CONTINUATION
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChapter(chapter.chapterIndex) }
                            .padding(start = (d * 20).dp, top = 4.dp, bottom = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                when {
                                    isBranch -> com.novelai.assistant.ui.theme.NovelColors.BadgeBranch.copy(alpha = 0.08f)
                                    isContinuation -> com.novelai.assistant.ui.theme.NovelColors.BadgeAi.copy(alpha = 0.08f)
                                    else -> Color.Transparent
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 树形连接线
                        Canvas(Modifier.size(width = 14.dp, height = 22.dp)) {
                            if (d > 0) {
                                drawLine(
                                    color = if (isBranch) com.novelai.assistant.ui.theme.NovelColors.BadgeBranch
                                    else com.novelai.assistant.ui.theme.NovelColors.BadgeAi,
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(size.width - 4f, size.height / 2),
                                    strokeWidth = 3f
                                )
                            }
                        }
                        OriginBadge(chapter.originType.name)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                chapter.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chapter.promptUsed != null) {
                                Text(
                                    "指令：${chapter.promptUsed}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
