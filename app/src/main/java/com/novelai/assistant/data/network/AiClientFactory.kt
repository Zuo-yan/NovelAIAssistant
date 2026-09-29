package com.novelai.assistant.data.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一走 OpenAI 兼容协议（各类中转站/聚合平台均提供该协议，
 * Claude/Gemini 等模型也通过中转站以 OpenAI 协议 + 模型名访问）。
 */
@Singleton
class AiClientFactory @Inject constructor(
    private val openAiCompatClient: OpenAiCompatClient
) {
    fun clientFor(kind: ProviderKind): AiChatClient = openAiCompatClient
}
