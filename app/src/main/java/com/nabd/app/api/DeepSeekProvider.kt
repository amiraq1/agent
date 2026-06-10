package com.nabd.app.api

import com.nabd.app.api.util.StreamingThinkTagParser

class DeepSeekProvider : BaseOpenAiProvider() {
    override val name: String = "DeepSeek"
    override val defaultBaseUrl: String = "https://api.deepseek.com"

    override suspend fun parseDeltaContent(
        delta: OpenAiDelta,
        config: ProviderConfig,
        thinkParser: StreamingThinkTagParser,
        emit: suspend (StreamEvent) -> Unit
    ) {
        delta.reasoningContent?.let { reasoning ->
            if (reasoning.isNotEmpty() && config.thinkingEnabled) {
                emit(StreamEvent.ThoughtChunk(reasoning))
            }
        }
        delta.content?.let { content ->
            if (content.isNotEmpty()) emit(StreamEvent.TextChunk(content))
        }
    }
}
