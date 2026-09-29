package com.novelai.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.ApiProviderConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderEditScreen(
    providerId: String,
    onBack: () -> Unit,
    viewModel: ProviderEditViewModel = hiltViewModel()
) {
    LaunchedEffect(providerId) { viewModel.load(providerId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val config = state.config
    val haptics = LocalHapticFeedback.current

    var showApiKey by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDocSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "添加提供商" else "编辑提供商") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showDocSheet = true }) {
                        Icon(Icons.Rounded.MenuBook, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("使用文档")
                    }
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 预设
            Text("厂商预设", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AiConfigRepository.PRESETS.forEach { preset ->
                    FilterChip(
                        selected = config.providerName == preset.name && config.baseUrl == preset.baseUrl,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.applyPreset(preset)
                        },
                        label = { Text(preset.name) }
                    )
                }
            }

            OutlinedTextField(
                value = config.providerName,
                onValueChange = { name -> viewModel.update { it.copy(providerName = name) } },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 协议说明（统一 OpenAI 兼容）
            Text(
                "协议：OpenAI 兼容（中转站/聚合平台均支持，Claude 等模型填中转地址 + 模型名即可）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = config.baseUrl,
                onValueChange = { url -> viewModel.update { it.copy(baseUrl = url.trim()) } },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com/v1 或中转站地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { key -> viewModel.update { it.copy(apiKey = key.trim()) } },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = config.selectedModel,
                onValueChange = { model -> viewModel.update { it.copy(selectedModel = model) } },
                label = { Text("模型名称") },
                placeholder = { Text("如 deepseek-chat / claude-sonnet-4-5") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.fetchModels() }) {
                        if (state.loadingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.CloudDownload, contentDescription = "拉取模型列表")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = config.maxTokens.toString(),
                    onValueChange = { v -> viewModel.update { it.copy(maxTokens = v.filter(Char::isDigit).toIntOrNull() ?: 4096) } },
                    label = { Text("Max Tokens") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "Temperature ${"%.2f".format(config.temperature)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = config.temperature,
                        onValueChange = { t -> viewModel.update { it.copy(temperature = t) } },
                        valueRange = 0f..2f
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("启用该提供商", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = { enabled -> viewModel.update { it.copy(isEnabled = enabled) } }
                )
            }

            // 连接测试结果
            state.pingState?.let { ping ->
                when (ping) {
                    is PingUiState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在测试连接…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is PingUiState.Ok -> Text(
                        "连接成功 · ${ping.latencyMs}ms",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    is PingUiState.Fail -> Text(
                        "连接失败：${ping.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Cable, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("测试连接")
                }
                Button(
                    onClick = { viewModel.save(onBack) },
                    enabled = config.providerName.isNotBlank() && config.baseUrl.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }

            if (state.models.isNotEmpty()) {
                Text(
                    "已拉取 ${state.models.size} 个模型，点击模型名输入框右侧按钮查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDocSheet) {
        ProviderDocSheet(onDismiss = { showDocSheet = false })
    }

    // 模型列表选择（拉取成功后自动弹出）
    if (showModelPicker && state.models.isNotEmpty()) {
        ModelPickerSheet(
            models = state.models,
            onSelect = { model ->
                viewModel.update { it.copy(selectedModel = model) }
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }

    confirmDelete.let {
        if (it) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("删除该提供商？") },
                text = { Text("相关任务分流配置将一并清除。") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.delete(onBack)
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
            )
        }
    }

    // 拉取成功后弹出模型选择
    LaunchedEffect(state.models) {
        if (state.models.isNotEmpty()) showModelPicker = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    models: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var filter by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("选择模型（${models.size}）", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("搜索模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn {
                val filtered = models.filter { it.contains(filter, ignoreCase = true) }
                items(filtered) { model ->
                    TextButton(onClick = { onSelect(model) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            model,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDocSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("模型配置项使用文档", style = MaterialTheme.typography.titleLarge)
            }

            DocSection(
                title = "1. 厂商预设 (Provider Presets)",
                desc = "快捷填入常见服务商的默认设置（OpenAI、Claude 中转、DeepSeek、Moonshot Kimi、硅基流动、Ollama 等）。选择预设会自动填充标准 Base URL 和默认模型名，只需填入 API Key 即可。"
            )

            DocSection(
                title = "2. 服务商名称 (Provider Name)",
                desc = "您为该配置自定义的别名（例如「DeepSeek 主力」、「便宜中转站」）。在设置首页与任务分流中会按此名称显示，方便多模型多 Key 一键切换。"
            )

            DocSection(
                title = "3. API 基础地址 (Base URL)",
                desc = "大模型服务接口的网络根地址，必须以 http:// 或 https:// 开头，兼容 OpenAI 规范格式（通常以 /v1 结尾）。\n• 官方接口：如 https://api.deepseek.com/v1\n• 中转站/聚合站：支持任意 OneAPI / NewAPI / 代理站（如 https://api.example.com/v1）\n• 本地服务：支持局域网内运行的 Ollama（如 http://192.168.1.100:11434/v1）"
            )

            DocSection(
                title = "4. API Key (接口密钥)",
                desc = "服务商分配的鉴权凭证（通常以 sk- 开头）。\n【隐私安全】本应用采用纯客户端 BYOK 模式，绝不上报或中转。您的 API Key 经 Android 系统 Keystore 加密存储于本机私有安全空间，安全无忧。"
            )

            DocSection(
                title = "5. 模型名称 (Model Name)",
                desc = "实际调用的 AI 模型名称（如 deepseek-chat、gpt-4o、claude-3-5-sonnet）。\n填好 Base URL 和 API Key 后，可点击输入框右侧的「拉取」按钮，一键从服务商端点自动拉取其支持的全部模型列表直接选取，无需手动核对。"
            )

            DocSection(
                title = "6. 最大输出 Token (Max Tokens)",
                desc = "单次请求模型返回的最大内容长度（1000 Token 大约折合 700~800 汉字）。\n• 续写场景：建议设置 2048 ~ 4096，以便生成完整的长章节；\n• 伴读问答：建议设置 1024 ~ 2048，响应迅速且节省费用。"
            )

            DocSection(
                title = "7. 采样温度 (Temperature)",
                desc = "控制模型输出的随机性与创造力（范围 0.0 ~ 2.0）：\n• 0.2 ~ 0.5（严谨）：输出稳定一致、逻辑严密，适合剧情回忆、角色分析与剧情梳理；\n• 0.7 ~ 0.9（平衡）：兼顾逻辑性与文学创造力，适合小说正文续写与分支推演；\n• 1.0 以上（高随机）：思维发散，适合寻找脑洞大开的反转灵感。"
            )

            DocSection(
                title = "8. 自定义请求头 (Custom Headers)",
                desc = "可选高级项。以标准 JSON 格式填写（如 {\"X-Custom-Header\": \"value\"}）。常用于访问某些需要额外携带项目 ID、特定身份标头或路由规则的代理服务。"
            )

            DocSection(
                title = "9. 任务分流机制",
                desc = "在「设置 -> 任务分流」中，可将不同任务指定给不同服务商与模型：\n• 续写场景：指定文风模仿强、上下文长的旗舰模型；\n• 伴读场景：指定响应快、成本低的轻量模型，兼顾体验与预算。"
            )
        }
    }
}

@Composable
private fun DocSection(title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
