package com.novelai.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.prefs.AppThemeMode
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.ApiProviderConfig
import com.novelai.assistant.data.repository.ModelRoute
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.repository.TokenUsage
import com.novelai.assistant.data.repository.UsageRepository
import com.novelai.assistant.data.network.AiClientFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PingUiState {
    data object Testing : PingUiState
    data class Ok(val latencyMs: Long) : PingUiState
    data class Fail(val message: String) : PingUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val aiConfigRepository: AiConfigRepository,
    private val usageRepository: UsageRepository,
    private val readingPreferencesRepository: ReadingPreferencesRepository,
    private val aiClientFactory: AiClientFactory
) : ViewModel() {

    val providers = aiConfigRepository.providers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continuationRoute: StateFlow<ModelRoute> = aiConfigRepository.continuationRoute
    val companionRoute: StateFlow<ModelRoute> = aiConfigRepository.companionRoute

    val usage: StateFlow<TokenUsage> = usageRepository.usage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TokenUsage())

    val appThemeMode: StateFlow<AppThemeMode> = readingPreferencesRepository.settings
        .map { it.appThemeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeMode.FOLLOW_SYSTEM)

    private val _pingResults = MutableStateFlow<Map<String, PingUiState>>(emptyMap())
    val pingResults: StateFlow<Map<String, PingUiState>> = _pingResults.asStateFlow()

    /** 任务分流弹窗：从提供商拉取的模型列表 */
    private val _routeModels = MutableStateFlow<List<String>>(emptyList())
    val routeModels: StateFlow<List<String>> = _routeModels.asStateFlow()

    private val _loadingRouteModels = MutableStateFlow(false)
    val loadingRouteModels: StateFlow<Boolean> = _loadingRouteModels.asStateFlow()

    fun fetchModelsFor(providerId: String) {
        val config = aiConfigRepository.getProvider(providerId) ?: return
        _loadingRouteModels.value = true
        viewModelScope.launch {
            val result = aiClientFactory.clientFor(config.kind).listModels(config)
            _routeModels.value = result.getOrDefault(emptyList())
            _loadingRouteModels.value = false
        }
    }

    fun clearRouteModels() {
        _routeModels.value = emptyList()
    }

    fun pingProvider(providerId: String) {
        val config = aiConfigRepository.getProvider(providerId) ?: return
        _pingResults.value = _pingResults.value + (providerId to PingUiState.Testing)
        viewModelScope.launch {
            val result = aiClientFactory.clientFor(config.kind).ping(config)
            _pingResults.value = _pingResults.value + (providerId to when {
                result.isSuccess -> PingUiState.Ok(result.getOrDefault(0L))
                else -> PingUiState.Fail(result.exceptionOrNull()?.message ?: "失败")
            })
        }
    }

    fun toggleEnabled(config: ApiProviderConfig, enabled: Boolean) {
        aiConfigRepository.upsertProvider(config.copy(isEnabled = enabled))
    }

    fun deleteProvider(id: String) = aiConfigRepository.deleteProvider(id)

    fun saveRoute(scene: TaskScene, providerId: String, model: String) {
        aiConfigRepository.saveRoute(scene, ModelRoute(providerId, model.trim()))
    }

    fun setAppTheme(mode: AppThemeMode) {
        viewModelScope.launch { readingPreferencesRepository.setAppThemeMode(mode) }
    }

    fun resetUsage() {
        viewModelScope.launch { usageRepository.reset() }
    }
}
