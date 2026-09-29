package com.novelai.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.novelai.assistant.data.prefs.AppThemeMode
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.ui.components.LargeTitleHeader
import com.novelai.assistant.ui.components.SectionCard
import com.novelai.assistant.ui.components.SettingRow
import com.novelai.assistant.ui.components.StatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onEditProvider: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val continuationRoute by viewModel.continuationRoute.collectAsStateWithLifecycle()
    val companionRoute by viewModel.companionRoute.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val appTheme by viewModel.appThemeMode.collectAsStateWithLifecycle()
    val pingResults by viewModel.pingResults.collectAsStateWithLifecycle()
    val routeModels by viewModel.routeModels.collectAsStateWithLifecycle()
    val loadingRouteModels by viewModel.loadingRouteModels.collectAsStateWithLifecycle()

    var routeScene by remember { mutableStateOf<TaskScene?>(null) }
    val haptics = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        LargeTitleHeader(title = "设置", subtitle = "BYOK 模式 · 密钥仅存本地加密空间")

        SectionCard(
            title = "模型与 API",
            footer = "兼容 OpenAI 协议的中转站/聚合平台均可接入；密钥经 Android Keystore 加密存储"
        ) {
            providers.forEach { config ->
                val ping = pingResults[config.id]
                SettingRow(
                    title = config.providerName,
                    subtitle = "${config.selectedModel.ifBlank { "未选模型" }} · ${config.baseUrl}",
                    icon = Icons.Rounded.Key,
                    onClick = { onEditProvider(config.id) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (ping) {
                                is PingUiState.Testing -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                is PingUiState.Ok -> {
                                    StatusDot(MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("${ping.latencyMs}ms", style = MaterialTheme.typography.labelMedium)
                                }
                                is PingUiState.Fail -> {
                                    StatusDot(MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(4.dp))
                                    Text("失败", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                }
                                null -> {}
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = config.isEnabled,
                                onCheckedChange = { viewModel.toggleEnabled(config, it) }
                            )
                        }
                    }
                )
            }
            SettingRow(
                title = "添加提供商",
                subtitle = "OpenAI / Claude / DeepSeek / Kimi / 硅基流动 / Ollama / 自定义",
                icon = Icons.Rounded.Add,
                onClick = { onEditProvider("new") }
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionCard(title = "任务分流", footer = "为不同任务指定不同模型，兼顾成本与效果") {
            val contProvider = providers.firstOrNull { it.id == continuationRoute.providerId }
            val compProvider = providers.firstOrNull { it.id == companionRoute.providerId }
            SettingRow(
                title = "续写场景",
                subtitle = TaskScene.CONTINUATION.hint,
                icon = Icons.Rounded.SwapHoriz,
                onClick = { routeScene = TaskScene.CONTINUATION },
                trailing = {
                    Text(
                        continuationRoute.model.ifBlank { contProvider?.selectedModel ?: "未配置" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
            SettingRow(
                title = "速览与伴读场景",
                subtitle = TaskScene.COMPANION.hint,
                icon = Icons.Rounded.Chat,
                onClick = { routeScene = TaskScene.COMPANION },
                trailing = {
                    Text(
                        companionRoute.model.ifBlank { compProvider?.selectedModel ?: "未配置" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionCard(title = "用量统计", footer = "Token 用量为本地累计估算") {
            SettingRow(
                title = "累计输入 Token",
                icon = Icons.Rounded.Cached,
                trailing = { Text("${usage.promptTokens}", style = MaterialTheme.typography.labelLarge) }
            )
            SettingRow(
                title = "累计输出 Token",
                icon = Icons.Rounded.Cached,
                trailing = { Text("${usage.completionTokens}", style = MaterialTheme.typography.labelLarge) }
            )
            SettingRow(
                title = "清空统计",
                icon = Icons.Rounded.Delete,
                onClick = { viewModel.resetUsage() }
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionCard(title = "外观") {
            SettingRow(
                title = "主题模式",
                icon = Icons.Rounded.ColorLens,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = appTheme == AppThemeMode.FOLLOW_SYSTEM,
                            onClick = { viewModel.setAppTheme(AppThemeMode.FOLLOW_SYSTEM) },
                            label = { Text("跟随系统") }
                        )
                        FilterChip(
                            selected = appTheme == AppThemeMode.LIGHT,
                            onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.setAppTheme(AppThemeMode.LIGHT) },
                            label = { Text("浅色") }
                        )
                        FilterChip(
                            selected = appTheme == AppThemeMode.DARK,
                            onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.setAppTheme(AppThemeMode.DARK) },
                            label = { Text("深色") }
                        )
                    }
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionCard(title = "关于", footer = "AI 智阅小说 v0.3.0 · 本地阅读 + AI 伴读续写 · 隐私数据仅存本机") {
            SettingRow(
                title = "手势提示",
                subtitle = "阅读页点按中间唤出菜单；长按段落「划线即问」",
                icon = Icons.Rounded.ColorLens
            )
        }
    }

    // 任务分流配置弹窗
    routeScene?.let { scene ->
        RouteEditDialog(
            scene = scene,
            providers = providers,
            current = if (scene == TaskScene.CONTINUATION) continuationRoute else companionRoute,
            routeModels = routeModels,
            loadingModels = loadingRouteModels,
            onFetchModels = { viewModel.fetchModelsFor(it) },
            onClearModels = { viewModel.clearRouteModels() },
            onDismiss = { routeScene = null },
            onSave = { providerId, model ->
                viewModel.saveRoute(scene, providerId, model)
                routeScene = null
            }
        )
    }
}

@Composable
private fun RouteEditDialog(
    scene: TaskScene,
    providers: List<com.novelai.assistant.data.repository.ApiProviderConfig>,
    current: com.novelai.assistant.data.repository.ModelRoute,
    routeModels: List<String>,
    loadingModels: Boolean,
    onFetchModels: (String) -> Unit,
    onClearModels: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var providerId by remember { mutableStateOf(current.providerId ?: providers.firstOrNull()?.id ?: "") }
    val selectedProvider = providers.firstOrNull { it.id == providerId }
    var model by remember { mutableStateOf(current.model.ifBlank { selectedProvider?.selectedModel ?: "" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置${scene.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (providers.isEmpty()) {
                    Text("请先在「模型与 API」中添加并启用提供商")
                }
                // 提供商选择
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    providers.take(3).forEach { p ->
                        FilterChip(
                            selected = p.id == providerId,
                            onClick = {
                                providerId = p.id
                                onClearModels()
                                model = p.selectedModel
                            },
                            label = { Text(p.providerName.take(5)) }
                        )
                    }
                    if (providers.size > 3) {
                        FilterChip(
                            selected = providerId !in providers.take(3).map { it.id },
                            onClick = {
                                val other = providers.first { it.id !in providers.take(3).map { x -> x.id } }
                                providerId = other.id
                                onClearModels()
                                model = other.selectedModel
                            },
                            label = { Text(providers.first { it.id !in providers.take(3).map { x -> x.id } }.providerName.take(5)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名称（可手填或从列表选择）") },
                    singleLine = true,
                    trailingIcon = {
                        if (loadingModels) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { onFetchModels(providerId) }) { Text("拉取") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // 模型列表
                if (routeModels.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        routeModels.forEach { m ->
                            TextButton(
                                onClick = { model = m },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    m,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(providerId, model) },
                enabled = providerId.isNotBlank() && model.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
