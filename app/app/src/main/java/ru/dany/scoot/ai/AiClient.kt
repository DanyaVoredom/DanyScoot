package ru.dany.scoot.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AiClient {

    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 120000
    private const val MAX_TOKENS = 4096
    private const val ANTHROPIC_VERSION = "2023-06-01"

    suspend fun complete(
        config: AiConfig,
        system: String,
        history: List<AiHistoryItem>,
        tools: List<AiToolSpec>,
    ): AiTurn = withContext(Dispatchers.IO) {
        val format = config.format.resolve(config.baseUrl)
        val url = endpoint(config.baseUrl, format)
        when (format) {
            AiFormat.ANTHROPIC -> anthropic(config, url, system, history, tools)
            AiFormat.OPENAI_RESPONSES -> responses(config, url, system, history, tools)
            else -> chat(config, url, system, history, tools)
        }
    }

    fun endpoint(baseUrl: String, format: AiFormat): String {
        var base = baseUrl.trim()
        if (base.isEmpty()) throw IOException("Пустой base URL")
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "https://$base"
        base = base.trimEnd('/')
        val version = Regex("/v\\d+$")
        return when (format) {
            AiFormat.ANTHROPIC -> {
                if (base.endsWith("/messages")) return base
                if (!version.containsMatchIn(base)) base += "/v1"
                "$base/messages"
            }
            AiFormat.OPENAI_RESPONSES -> {
                if (base.endsWith("/responses")) return base
                if (base.endsWith("/chat/completions")) base = base.removeSuffix("/chat/completions")
                if (!version.containsMatchIn(base)) base += "/v1"
                "$base/responses"
            }
            else -> {
                if (base.endsWith("/chat/completions")) return base
                if (base.endsWith("/responses")) base = base.removeSuffix("/responses")
                if (!version.containsMatchIn(base)) base += "/v1"
                "$base/chat/completions"
            }
        }
    }

    private fun chat(
        config: AiConfig,
        url: String,
        system: String,
        history: List<AiHistoryItem>,
        tools: List<AiToolSpec>,
    ): AiTurn {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        for (item in history) {
            when (item) {
                is AiHistoryItem.User -> messages.put(
                    JSONObject().put("role", "user").put("content", item.text),
                )
                is AiHistoryItem.Assistant -> {
                    val message = JSONObject()
                        .put("role", "assistant")
                        .put("content", item.text.ifEmpty { "" })
                    if (item.calls.isNotEmpty()) {
                        val calls = JSONArray()
                        for (call in item.calls) {
                            calls.put(
                                JSONObject()
                                    .put("id", call.id)
                                    .put("type", "function")
                                    .put("function", JSONObject()
                                        .put("name", call.name)
                                        .put("arguments", call.arguments.ifEmpty { "{}" })),
                            )
                        }
                        message.put("tool_calls", calls)
                    }
                    messages.put(message)
                }
                is AiHistoryItem.ToolResult -> messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", item.callId)
                        .put("content", item.output),
                )
            }
        }
        val body = JSONObject()
            .put("model", config.model)
            .put("messages", messages)
        if (tools.isNotEmpty()) {
            body.put("tools", toolsArrayChat(tools))
            body.put("tool_choice", "auto")
        }
        val json = postJson(
            url,
            body,
            mapOf("Authorization" to "Bearer ${config.apiKey}"),
        )
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw IOException("Пустой ответ API")
        val message = choice.optJSONObject("message")
            ?: throw IOException("В ответе нет message")
        val text = extractText(message.opt("content"))
        val calls = ArrayList<AiToolCall>()
        val array = message.optJSONArray("tool_calls")
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val function = item.optJSONObject("function") ?: continue
                val name = function.optString("name")
                if (name.isEmpty()) continue
                calls.add(
                    AiToolCall(
                        id = item.optString("id").ifEmpty { generatedId() },
                        name = name,
                        arguments = argumentsText(function.opt("arguments")),
                    ),
                )
            }
        }
        val legacy = message.optJSONObject("function_call")
        if (calls.isEmpty() && legacy != null) {
            val name = legacy.optString("name")
            if (name.isNotEmpty()) {
                calls.add(
                    AiToolCall(
                        id = generatedId(),
                        name = name,
                        arguments = argumentsText(legacy.opt("arguments")),
                    ),
                )
            }
        }
        return AiTurn(text, calls)
    }

    private fun responses(
        config: AiConfig,
        url: String,
        system: String,
        history: List<AiHistoryItem>,
        tools: List<AiToolSpec>,
    ): AiTurn {
        val input = JSONArray()
        for (item in history) {
            when (item) {
                is AiHistoryItem.User -> input.put(
                    JSONObject().put("role", "user").put("content", item.text),
                )
                is AiHistoryItem.Assistant -> {
                    if (item.text.isNotEmpty()) {
                        input.put(JSONObject().put("role", "assistant").put("content", item.text))
                    }
                    for (call in item.calls) {
                        input.put(
                            JSONObject()
                                .put("type", "function_call")
                                .put("call_id", call.id)
                                .put("name", call.name)
                                .put("arguments", call.arguments.ifEmpty { "{}" }),
                        )
                    }
                }
                is AiHistoryItem.ToolResult -> input.put(
                    JSONObject()
                        .put("type", "function_call_output")
                        .put("call_id", item.callId)
                        .put("output", item.output),
                )
            }
        }
        val body = JSONObject()
            .put("model", config.model)
            .put("instructions", system)
            .put("input", input)
        if (tools.isNotEmpty()) {
            body.put("tools", toolsArrayResponses(tools))
            body.put("tool_choice", "auto")
        }
        val json = postJson(
            url,
            body,
            mapOf("Authorization" to "Bearer ${config.apiKey}"),
        )
        val output = json.optJSONArray("output")
        val text = StringBuilder()
        val calls = ArrayList<AiToolCall>()
        if (output != null) {
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                when (item.optString("type")) {
                    "message" -> {
                        val content = item.optJSONArray("content")
                        if (content != null) {
                            for (j in 0 until content.length()) {
                                val part = content.optJSONObject(j) ?: continue
                                val value = part.optString("text")
                                if (value.isNotEmpty()) {
                                    if (text.isNotEmpty()) text.append('\n')
                                    text.append(value)
                                }
                            }
                        } else {
                            val value = item.optString("text")
                            if (value.isNotEmpty()) {
                                if (text.isNotEmpty()) text.append('\n')
                                text.append(value)
                            }
                        }
                    }
                    "function_call" -> {
                        val name = item.optString("name")
                        if (name.isNotEmpty()) {
                            calls.add(
                                AiToolCall(
                                    id = item.optString("call_id").ifEmpty {
                                        item.optString("id").ifEmpty { generatedId() }
                                    },
                                    name = name,
                                    arguments = argumentsText(item.opt("arguments")),
                                ),
                            )
                        }
                    }
                }
            }
        }
        val top = json.optString("output_text")
        if (text.isEmpty() && top.isNotEmpty()) text.append(top)
        return AiTurn(text.toString(), calls)
    }

    private fun anthropic(
        config: AiConfig,
        url: String,
        system: String,
        history: List<AiHistoryItem>,
        tools: List<AiToolSpec>,
    ): AiTurn {
        val messages = JSONArray()
        for (item in history) {
            when (item) {
                is AiHistoryItem.User -> appendAnthropic(
                    messages,
                    "user",
                    JSONObject().put("type", "text").put("text", item.text),
                )
                is AiHistoryItem.Assistant -> {
                    val blocks = JSONArray()
                    if (item.text.isNotEmpty()) {
                        blocks.put(JSONObject().put("type", "text").put("text", item.text))
                    }
                    for (call in item.calls) {
                        blocks.put(
                            JSONObject()
                                .put("type", "tool_use")
                                .put("id", call.id)
                                .put("name", call.name)
                                .put("input", jsonObjectOf(call.arguments)),
                        )
                    }
                    if (blocks.length() > 0) {
                        messages.put(JSONObject().put("role", "assistant").put("content", blocks))
                    }
                }
                is AiHistoryItem.ToolResult -> appendAnthropic(
                    messages,
                    "user",
                    JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", item.callId)
                        .put("content", item.output),
                )
            }
        }
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", MAX_TOKENS)
            .put("system", system)
            .put("messages", messages)
        if (tools.isNotEmpty()) {
            body.put("tools", toolsArrayAnthropic(tools))
            body.put("tool_choice", JSONObject().put("type", "auto"))
        }
        val json = postJson(
            url,
            body,
            mapOf(
                "x-api-key" to config.apiKey,
                "anthropic-version" to ANTHROPIC_VERSION,
            ),
        )
        val content = json.optJSONArray("content")
        val text = StringBuilder()
        val calls = ArrayList<AiToolCall>()
        if (content != null) {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                when (block.optString("type")) {
                    "text" -> {
                        val value = block.optString("text")
                        if (value.isNotEmpty()) {
                            if (text.isNotEmpty()) text.append('\n')
                            text.append(value)
                        }
                    }
                    "tool_use" -> {
                        val name = block.optString("name")
                        if (name.isNotEmpty()) {
                            calls.add(
                                AiToolCall(
                                    id = block.optString("id").ifEmpty { generatedId() },
                                    name = name,
                                    arguments = argumentsText(block.opt("input")),
                                ),
                            )
                        }
                    }
                }
            }
        }
        return AiTurn(text.toString(), calls)
    }

    private fun appendAnthropic(messages: JSONArray, role: String, block: JSONObject) {
        val last = if (messages.length() > 0) messages.optJSONObject(messages.length() - 1) else null
        if (last != null && last.optString("role") == role) {
            last.optJSONArray("content")?.put(block)
        } else {
            messages.put(JSONObject().put("role", role).put("content", JSONArray().put(block)))
        }
    }

    private fun toolsArrayChat(tools: List<AiToolSpec>): JSONArray {
        val array = JSONArray()
        for (tool in tools) {
            array.put(
                JSONObject()
                    .put("type", "function")
                    .put("function", JSONObject()
                        .put("name", tool.name)
                        .put("description", tool.description)
                        .put("parameters", tool.parameters)),
            )
        }
        return array
    }

    private fun toolsArrayResponses(tools: List<AiToolSpec>): JSONArray {
        val array = JSONArray()
        for (tool in tools) {
            array.put(
                JSONObject()
                    .put("type", "function")
                    .put("name", tool.name)
                    .put("description", tool.description)
                    .put("parameters", tool.parameters),
            )
        }
        return array
    }

    private fun toolsArrayAnthropic(tools: List<AiToolSpec>): JSONArray {
        val array = JSONArray()
        for (tool in tools) {
            array.put(
                JSONObject()
                    .put("name", tool.name)
                    .put("description", tool.description)
                    .put("input_schema", tool.parameters),
            )
        }
        return array
    }

    private fun postJson(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "DanyScoot")
        for ((key, value) in headers) {
            connection.setRequestProperty(key, value)
        }
        return try {
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException(httpError(code, text))
            if (text.isBlank()) throw IOException("Пустой ответ API")
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun httpError(code: Int, body: String): String {
        val message = runCatching {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            error?.optString("message")?.ifEmpty { null } ?: json.optString("message")
        }.getOrNull()
        val suffix = message?.take(400)?.takeIf { it.isNotBlank() }
        return if (suffix == null) "HTTP $code" else "HTTP $code: $suffix"
    }

    private fun extractText(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> {
            val parts = ArrayList<String>()
            for (i in 0 until value.length()) {
                val part = value.opt(i)
                val text = when (part) {
                    is String -> part
                    is JSONObject -> part.optString("text").ifEmpty {
                        part.optJSONObject("text")?.optString("value").orEmpty()
                    }
                    else -> ""
                }
                if (text.isNotEmpty()) parts.add(text)
            }
            parts.joinToString("\n")
        }
        is JSONObject -> value.optString("text").ifEmpty {
            value.optJSONObject("text")?.optString("value").orEmpty()
        }
        else -> ""
    }

    private fun argumentsText(value: Any?): String = when (value) {
        is String -> value.ifBlank { "{}" }
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        null -> "{}"
        else -> value.toString()
    }

    private fun jsonObjectOf(text: String): JSONObject = runCatching { JSONObject(text) }
        .getOrElse { JSONObject() }

    private fun generatedId(): String = "call_${System.nanoTime()}"
}
