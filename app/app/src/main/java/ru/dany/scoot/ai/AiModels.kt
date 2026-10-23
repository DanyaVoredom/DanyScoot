package ru.dany.scoot.ai

import org.json.JSONObject

enum class AiRole { USER, ASSISTANT, TOOL, ERROR }

data class AiMessage(
    val id: Long,
    val role: AiRole,
    val text: String,
    val timeMs: Long = System.currentTimeMillis(),
)

data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class AiTurn(
    val text: String,
    val calls: List<AiToolCall>,
)

data class AiToolSpec(
    val name: String,
    val description: String,
    val parameters: JSONObject,
)

sealed interface AiHistoryItem {
    data class User(val text: String) : AiHistoryItem
    data class Assistant(val text: String, val calls: List<AiToolCall>) : AiHistoryItem
    data class ToolResult(val callId: String, val name: String, val output: String) : AiHistoryItem
}

data class AiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val format: AiFormat,
)
