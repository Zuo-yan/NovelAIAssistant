package com.novelai.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.network.AiClientFactory
import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.ApiProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderEditState(
    val isNew: Boolean = true,
    val config: ApiProviderConfig = newConfig(),
    val pingState: PingUiState? = null,
    val models: List<String> = emptyList(),
    val loadingModels: Boolean = false,
    val saved: Boolean = false
) {
    companion object {
        fun newConfig() = ApiProviderConfig(providerName = "", baseUrl = "", apiKey = "", selectedModel = "")
    }
}

@HiltViewModel
class ProviderEditViewModel @Inject constructor(
    private val aiConfigRepository: AiConfigRepository,
    private val aiClientFactory: AiClientFactory
) : ViewModel() {

    private val _state = MutableStateFlow(ProviderEditState())
    val state: StateFlow<ProviderEditState> = _state.asStateFlow()

    fun load(providerId: String) {
        if (providerId == "new") {
            _state.value = ProviderEditState(isNew = true)
        } else {
            val existing = aiConfigRepository.getProvider(providerId)
            _state.value = if (existing != null) {
                ProviderEditState(isNew = false, config = existing)
            } else ProviderEditState(isNew = true)
        }
    }

    fun update(transform: (ApiProviderConfig) -> ApiProviderConfig) {
        _state.value = _state.value.copy(config = transform(_state.value.config))
    }

    fun applyPreset(preset: AiConfigRepository.Companion.ProviderPreset) {
        update {
            it.copy(
                providerName = preset.name,
                baseUrl = preset.baseUrl,
                kind = preset.kind,
                selectedModel = it.selectedModel.ifBlank { preset.suggestedModel }
            )
        }
    }

    fun testConnection() {
        val config = _state.value.config
        _state.value = _state.value.copy(pingState = PingUiState.Testing)
        viewModelScope.launch {
            val result = aiClientFactory.clientFor(config.kind).ping(config)
            _state.value = _state.value.copy(
                pingState = when {
                    result.isSuccess -> PingUiState.Ok(result.getOrDefault(0L))
                    else -> PingUiState.Fail(result.exceptionOrNull()?.message ?: "连接失败")
                }
            )
        }
    }

    fun fetchModels() {
        val config = _state.value.config
        _state.value = _state.value.copy(loadingModels = true)
        viewModelScope.launch {
            val result = aiClientFactory.clientFor(config.kind).listModels(config)
            _state.value = _state.value.copy(
                loadingModels = false,
                models = result.getOrDefault(emptyList()),
                pingState = if (result.isFailure) PingUiState.Fail(
                    result.exceptionOrNull()?.message ?: "拉取失败"
                ) else _state.value.pingState
            )
        }
    }

    fun save(onDone: () -> Unit) {
        val config = _state.value.config
        if (config.providerName.isBlank() || config.baseUrl.isBlank()) return
        viewModelScope.launch {
            aiConfigRepository.upsertProvider(config.copy(selectedModel = config.selectedModel.trim()))
            _state.value = _state.value.copy(saved = true)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _state.value.config.id
        viewModelScope.launch {
            aiConfigRepository.deleteProvider(id)
            onDone()
        }
    }
}
