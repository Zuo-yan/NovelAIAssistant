package com.novelai.assistant.data.repository

import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ApiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val providerName: String,
    val baseUrl: String,
    val apiKey: String,
    val selectedModel: String,
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val customHeaders: Map<String, String> = emptyMap(),
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val isEnabled: Boolean = true
)

/** 任务分流：某场景使用哪个提供商的哪个模型 */
@Serializable
data class ModelRoute(
    val providerId: String? = null,
    val model: String = ""
)

enum class TaskScene(val label: String, val hint: String) {
    CONTINUATION("续写场景", "长上下文拟合与文风模仿强的模型"),
    COMPANION("伴读场景", "响应快、成本低的小模型")
}

@Singleton
class AiConfigRepository @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _providers = MutableStateFlow(loadProviders())
    val providers: StateFlow<List<ApiProviderConfig>> = _providers.asStateFlow()

    private val _continuationRoute = MutableStateFlow(loadRoute(ROUTE_CONTINUATION))
    val continuationRoute: StateFlow<ModelRoute> = _continuationRoute.asStateFlow()

    private val _companionRoute = MutableStateFlow(loadRoute(ROUTE_COMPANION))
    val companionRoute: StateFlow<ModelRoute> = _companionRoute.asStateFlow()

    private fun loadProviders(): List<ApiProviderConfig> {
        val raw = secureStorage.getDecrypted(KEY_PROVIDERS) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ApiProviderConfig.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun persistProviders(list: List<ApiProviderConfig>) {
        secureStorage.putEncrypted(KEY_PROVIDERS, json.encodeToString(ListSerializer(ApiProviderConfig.serializer()), list))
        _providers.value = list
    }

    fun upsertProvider(config: ApiProviderConfig) {
        val list = _providers.value.toMutableList()
        val index = list.indexOfFirst { it.id == config.id }
        if (index >= 0) list[index] = config else list.add(config)
        persistProviders(list)
    }

    fun deleteProvider(id: String) {
        persistProviders(_providers.value.filterNot { it.id == id })
        // 清理指向该提供商的路由
        if (_continuationRoute.value.providerId == id) saveRoute(TaskScene.CONTINUATION, ModelRoute())
        if (_companionRoute.value.providerId == id) saveRoute(TaskScene.COMPANION, ModelRoute())
    }

    fun getProvider(id: String?): ApiProviderConfig? = _providers.value.firstOrNull { it.id == id }

    fun enabledProviders(): List<ApiProviderConfig> = _providers.value.filter { it.isEnabled }

    fun route(scene: TaskScene): ModelRoute = when (scene) {
        TaskScene.CONTINUATION -> _continuationRoute.value
        TaskScene.COMPANION -> _companionRoute.value
    }

    fun saveRoute(scene: TaskScene, route: ModelRoute) {
        secureStorage.putPlain(
            when (scene) {
                TaskScene.CONTINUATION -> ROUTE_CONTINUATION
                TaskScene.COMPANION -> ROUTE_COMPANION
            },
            json.encodeToString(ModelRoute.serializer(), route)
        )
        when (scene) {
            TaskScene.CONTINUATION -> _continuationRoute.value = route
            TaskScene.COMPANION -> _companionRoute.value = route
        }
    }

    /** 解析某场景当前生效的 (提供商, 模型)，路由未配置时回退到第一个启用的提供商 */
    fun resolveActive(scene: TaskScene): Pair<ApiProviderConfig, String>? {
        val route = route(scene)
        val provider = getProvider(route.providerId) ?: enabledProviders().firstOrNull()
            ?: return null
        val model = route.model.ifBlank { provider.selectedModel }
        return provider to model
    }

    private fun loadRoute(key: String): ModelRoute {
        val raw = secureStorage.getPlain(key) ?: return ModelRoute()
        return runCatching { json.decodeFromString(ModelRoute.serializer(), raw) }.getOrDefault(ModelRoute())
    }

    companion object {
        private const val KEY_PROVIDERS = "api_providers_v1"
        private const val ROUTE_CONTINUATION = "route_continuation"
        private const val ROUTE_COMPANION = "route_companion"

        /** 厂商预设模版（统一 OpenAI 兼容协议；Claude 等模型经中转站以模型名访问） */
        data class ProviderPreset(
            val name: String,
            val baseUrl: String,
            val kind: ProviderKind,
            val suggestedModel: String
        )

        val PRESETS = listOf(
            ProviderPreset("OpenAI", "https://api.openai.com/v1", ProviderKind.OPENAI_COMPATIBLE, "gpt-4o-mini"),
            ProviderPreset("Claude (中转)", "", ProviderKind.OPENAI_COMPATIBLE, "claude-sonnet-4-5"),
            ProviderPreset("DeepSeek", "https://api.deepseek.com/v1", ProviderKind.OPENAI_COMPATIBLE, "deepseek-chat"),
            ProviderPreset("Moonshot Kimi", "https://api.moonshot.cn/v1", ProviderKind.OPENAI_COMPATIBLE, "moonshot-v1-32k"),
            ProviderPreset("SiliconFlow 硅基流动", "https://api.siliconflow.cn/v1", ProviderKind.OPENAI_COMPATIBLE, "deepseek-ai/DeepSeek-V2.5"),
            ProviderPreset("Ollama 本地", "http://localhost:11434/v1", ProviderKind.OPENAI_COMPATIBLE, "qwen2.5:7b"),
            ProviderPreset("自定义中转站", "", ProviderKind.OPENAI_COMPATIBLE, "")
        )
    }
}
