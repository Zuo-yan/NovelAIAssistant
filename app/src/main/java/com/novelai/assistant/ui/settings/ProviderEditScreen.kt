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
                .padding(bottom = 32.dp),
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
