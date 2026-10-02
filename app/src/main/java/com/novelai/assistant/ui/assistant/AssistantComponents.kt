package com.novelai.assistant.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelai.assistant.data.db.AiChatRecordEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    record: AiChatRecordEntity,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isUser = record.role == "user"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            },
            modifier = Modifier
                .widthIn(max = 330.dp)
                .combinedClickable(
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val copyContent = if (isUser && record.content.contains("【提问】")) {
                            val parts = record.content.replace("\r\n", "\n").split("\n\n【提问】\n", limit = 2)
                            parts.getOrNull(1) ?: record.content
                        } else {
                            record.content
                        }
                        clipboardManager.setText(AnnotatedString(copyContent))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    onClick = {}
                )
        ) {
            SelectionContainer {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    val isQuotedUserMsg = isUser && record.content.startsWith("【划线选段】")
                    if (isQuotedUserMsg) {
                        val cleaned = record.content.replace("\r\n", "\n")
                        val parts = cleaned.removePrefix("【划线选段】\n").split("\n\n【提问】\n", limit = 2)
                        val quote = parts.getOrNull(0) ?: ""
                        val question = parts.getOrNull(1) ?: ""

                        if (quote.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = "“$quote”",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                        if (question.isNotBlank()) {
                            Text(
                                text = question,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = if (isStreaming) "${record.content} ▌" else record.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBubble(
    text: String = "AI 正在思考并检索小说章节…",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
