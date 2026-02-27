package com.newoether.agora.api

import android.util.Log
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.Participant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

class OpenRouterProvider : LlmProvider {
    override val name: String = "Open Router"
    override val defaultBaseUrl: String = "https://openrouter.ai/api/v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun generateResponse(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Flow<StreamEvent> = flow {
        val baseUrl = config.baseUrl?.trimEnd('/') ?: defaultBaseUrl
        val apiMessages = messages.takeLast(config.maxContextWindow).map { msg ->
            OpenAiMessage(
                role = if (msg.participant == Participant.USER) "user" else "assistant",
                content = listOf(OpenAiContentPart(type = "text", text = msg.text))
            )
        }

        val requestBody = OpenAiChatRequest(
            model = config.modelId,
            messages = apiMessages,
            stream = true,
            streamOptions = OpenAiStreamOptions(includeUsage = true)
        )

        var connection: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/chat/completions")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            if (config.apiKey.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            // Open Router specific headers
            connection.setRequestProperty("HTTP-Referer", "https://github.com/newo-ether/Agora")
            connection.setRequestProperty("X-Title", "Agora")
            connection.doOutput = true
            
            connection.outputStream.bufferedWriter().use { 
                it.write(json.encodeToString(OpenAiChatRequest.serializer(), requestBody)) 
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = connection.inputStream.bufferedReader()
                var line = reader.readLine()
                while (line != null && currentCoroutineContext().isActive) {
                    if (line.startsWith("data: ")) {
                        val jsonStr = line.substring(6).trim()
                        if (jsonStr == "[DONE]") break
                            val response = json.decodeFromString<OpenAiStreamResponse>(jsonStr)
                            response.choices?.firstOrNull()?.delta?.let { delta ->
                                delta.content?.let { if (it.isNotEmpty()) emit(StreamEvent.TextChunk(it)) }
                                delta.reasoningContent?.let { if (it.isNotEmpty() && config.thinkingEnabled) emit(StreamEvent.ThoughtChunk(it, null)) }
                            }
                            response.usage?.let { emit(StreamEvent.UsageUpdate(it.totalTokens)) }
                    }
                    line = reader.readLine()
                }
            } else {
                emit(StreamEvent.Error("Error $responseCode: ${connection.errorStream?.bufferedReader()?.readText()}"))
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) emit(StreamEvent.Error("Error: ${e.localizedMessage}"))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchModels(apiKey: String, baseUrl: String?): List<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val url = URL("${baseUrl?.trimEnd('/') ?: defaultBaseUrl}/models")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<OpenAiModelListResponse>(responseText).data.map { it.id }.sorted()
        } catch (e: Exception) { emptyList() }
    }
}
