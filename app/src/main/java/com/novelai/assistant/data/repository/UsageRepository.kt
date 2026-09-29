package com.novelai.assistant.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class TokenUsage(val promptTokens: Long = 0, val completionTokens: Long = 0) {
    val total: Long get() = promptTokens + completionTokens
}

/** 客户端本地 Token 用量统计 */
@Singleton
class UsageRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val prompt = longPreferencesKey("usage_prompt_tokens")
        val completion = longPreferencesKey("usage_completion_tokens")
    }

    val usage: Flow<TokenUsage> = dataStore.data.map { p ->
        TokenUsage(p[Keys.prompt] ?: 0L, p[Keys.completion] ?: 0L)
    }

    suspend fun add(promptTokens: Long, completionTokens: Long) {
        dataStore.edit { p ->
            p[Keys.prompt] = (p[Keys.prompt] ?: 0L) + promptTokens
            p[Keys.completion] = (p[Keys.completion] ?: 0L) + completionTokens
        }
    }

    suspend fun reset() {
        dataStore.edit { p ->
            p[Keys.prompt] = 0L
            p[Keys.completion] = 0L
        }
    }
}
