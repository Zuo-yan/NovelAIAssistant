package com.novelai.assistant.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.ui.components.LargeTitleHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val maxChapters by viewModel.maxChapters.collectAsStateWithLifecycle()
    val relay by viewModel.relay.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    var showRelayDialog by remember { mutableStateOf(false) }
    var relayDraft by remember { mutableStateOf("") }
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieDraft by remember { mutableStateOf("") }
    val sfCookie by viewModel.sfCookie.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        LargeTitleHeader(title = "发现", subtitle = "在线抓取 · 导入到书架")

        com.novelai.assistant.ui.components.SectionCard(title = "书源") {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.novelai.assistant.ui.components.SegmentedControl(
                    options = FetchSource.entries.toList(),
                    selected = source,
                    labelOf = { it.label },
                    onSelect = { viewModel.setSource(it) }
                )
                Text(
                    source.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (source == FetchSource.SFACG) {
                    // 付费章节 Cookie 入口
                    TextButton(onClick = {
                        cookieDraft = sfCookie
                        showCookieDialog = true
                    }) {
                        Text(
                            if (sfCookie.isBlank()) "填写账号 Cookie（可选，用于下载已订阅的付费章节）"
                            else "已配置账号 Cookie（点击修改/清除）",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { viewModel.input.value = it },
                    placeholder = { Text(if (source == FetchSource.SFACG) "如 245539 或完整书籍链接" else "如 7143038691944959011 或完整链接") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.fetch()
                    },
                    enabled = state !is DiscoverUiState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, Modifier.size(18.dp))
                    Text(if (state is DiscoverUiState.Loading) "  抓取目录中…" else "  抓取目录")
                }
                if (source == FetchSource.FANQIE) {
                    Text(
                        "番茄正文依赖第三方中转 API（地址经常失效），当前：$relay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        TextButton(onClick = {
                            relayDraft = relay
                            showRelayDialog = true
                        }) { Text("修改地址", style = MaterialTheme.typography.labelLarge) }
                        TextButton(onClick = { viewModel.resetRelay() }) {
                            Text("恢复默认", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Text(
                        "默认地址来自 fanqienovel-downloader 项目；失效时请到其 GitHub 主页获取最新中转",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            is DiscoverUiState.Loading -> {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DiscoverUiState.Error -> {
                com.novelai.assistant.ui.components.SectionCard(title = "抓取失败") {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is DiscoverUiState.Loaded -> {
                com.novelai.assistant.ui.components.SectionCard(title = "抓取结果") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(s.book.title, style = MaterialTheme.typography.titleLarge)
                        if (s.book.author.isNotBlank()) {
                            Text(
                                "作者：${s.book.author}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "共 ${s.book.chapters.size} 章（VIP 章节已自动跳过）",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // 导入范围
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "全部", 20 to "前 20 章", 50 to "前 50 章").forEach { (n, label) ->
                                FilterChip(
                                    selected = maxChapters == n,
                                    onClick = { viewModel.setMaxChapters(n) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        val progress = importProgress
                        if (progress != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "正在抓取第 ${progress + 1} / ${s.book.chapters.size} 章…",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = {
                                        (progress + 1f) / s.book.chapters.size.coerceAtLeast(1)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelImport() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("取消导入") }
                        } else {
                            Button(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.import()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("导入到书架") }
                        }
                    }
                }
            }
            is DiscoverUiState.Imported -> {
                com.novelai.assistant.ui.components.SectionCard(title = "导入完成") {
                    Text(
                        "《${s.title}》已导入书架，共 ${s.chapters} 章\n到书架即可开始阅读或 AI 续写",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            DiscoverUiState.Idle -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "输入书号抓取整本小说\n抓取完成后自动进入书架",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 抓取说明
        com.novelai.assistant.ui.components.SectionCard(title = "说明", footer = "仅支持免费内容；VIP 章节无法抓取") {
            com.novelai.assistant.ui.components.SettingRow(
                title = "版权与使用范围",
                subtitle = "本工具仅将网页内容转为本地文本供个人阅读，请支持正版",
                icon = Icons.Rounded.Settings
            )
        }
    }

    if (showRelayDialog) {
        AlertDialog(
            onDismissRequest = { showRelayDialog = false },
            title = { Text("番茄中转 API") },
            text = {
                OutlinedTextField(
                    value = relayDraft,
                    onValueChange = { relayDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRelay(relayDraft)
                    showRelayDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRelayDialog = false }) { Text("取消") } }
        )
    }

    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text("SF 账号 Cookie") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "获取方式：电脑浏览器登录 book.sfacg.com 并订阅想看的书 → F12 打开开发者工具 → Network 刷新页面 → 点击任意请求 → 复制请求头 Cookie 的完整值粘贴到下面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = cookieDraft,
                        onValueChange = { cookieDraft = it },
                        placeholder = { Text("Cookie: xxx=yyy; ...") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "仅能下载该账号已订阅章节；Cookie 经 Keystore 加密保存在本机。留空保存即清除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSfCookie(cookieDraft)
                    showCookieDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showCookieDialog = false }) { Text("取消") } }
        )
    }
}
