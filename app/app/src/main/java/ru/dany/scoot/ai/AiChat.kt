package ru.dany.scoot.ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.dany.scoot.R
import ru.dany.scoot.core.Prefs

object AiChat {

    private const val MAX_STEPS = 12
    private const val MAX_HISTORY = 80
    private const val MAX_MESSAGES = 300
    private const val TOOL_PREVIEW = 1600

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val history = ArrayList<AiHistoryItem>()
    private var nextId = 1L
    private var job: Job? = null
    private var runId = 0L

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    fun send(context: Context, text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _busy.value) return
        val app = context.applicationContext
        val prefs = Prefs.get(app)
        addMessage(AiRole.USER, prompt)
        history.add(AiHistoryItem.User(prompt))
        trimHistory()
        val config = AiConfig(
            baseUrl = prefs.aiBaseUrl,
            apiKey = prefs.aiApiKey,
            model = prefs.aiModel,
            format = AiFormat.fromId(prefs.aiFormat),
        )
        if (config.apiKey.isEmpty() || config.model.isBlank()) {
            addMessage(AiRole.ERROR, app.getString(R.string.ai_not_configured))
            return
        }
        _busy.value = true
        val currentRun = ++runId
        job = scope.launch {
            try {
                var lastHadCalls = false
                for (step in 1..MAX_STEPS) {
                    if (!isActive) break
                    _status.value = app.getString(R.string.ai_status_request)
                    normalizeHistory()
                    val turn = AiClient.complete(config, systemPrompt(), history.toList(), AiTools.specs)
                    if (turn.text.isNotBlank()) addMessage(AiRole.ASSISTANT, turn.text)
                    if (turn.text.isNotBlank() || turn.calls.isNotEmpty()) {
                        history.add(AiHistoryItem.Assistant(turn.text, turn.calls))
                    }
                    trimHistory()
                    lastHadCalls = turn.calls.isNotEmpty()
                    if (!lastHadCalls) break
                    for (call in turn.calls) {
                        if (!isActive) break
                        _status.value = app.getString(R.string.ai_status_tool, call.name)
                        val result = AiTools.execute(app, call)
                        history.add(AiHistoryItem.ToolResult(call.id, call.name, result))
                        addMessage(AiRole.TOOL, "${call.name}: ${preview(result)}")
                        trimHistory()
                    }
                }
                if (lastHadCalls && isActive) {
                    addMessage(AiRole.ERROR, app.getString(R.string.ai_limit_steps))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addMessage(
                    AiRole.ERROR,
                    app.getString(R.string.ai_request_failed, e.message ?: e.javaClass.simpleName),
                )
            } finally {
                if (runId == currentRun) {
                    job = null
                    _busy.value = false
                    _status.value = ""
                }
            }
        }
    }

    fun stop() {
        runId++
        job?.cancel()
        job = null
        _busy.value = false
        _status.value = ""
    }

    fun clear() {
        stop()
        history.clear()
        _messages.value = emptyList()
        nextId = 1L
    }

    private fun systemPrompt(): String = buildString {
        append("Ты AI-ассистент приложения DanyScoot для BLE-устройств: самокатов Urent и гарнитур Edifier. ")
        append("Управляй устройствами только через инструменты и не выдумывай данные. ")
        append("Правила: ")
        append("1. Перед командами самоката проверяй состояние через get_state и get_gatt. ")
        append("2. Для поиска устройства используй scan_devices, затем connect_device. ")
        append("3. Опасные действия (изъятие батареи, питание, перебор ключей, спам, установка обновления) выполняй только по явной просьбе пользователя. ")
        append("4. Если инструмент вернул ошибку, объясни причину и предложи следующий шаг, не повторяй тот же вызов без изменений. ")
        append("5. Не показывай API-ключи и служебные токены. ")
        append("6. Отвечай по-русски, кратко и по делу, без лишних предупреждений. ")
        append("Доступные устройства и протокол описаны в инструментах. ")
        append("\nТекущий контекст: ").append(AiTools.contextSnapshot())
    }

    private fun addMessage(role: AiRole, text: String) {
        val list = ArrayList(_messages.value)
        list.add(AiMessage(id = nextId++, role = role, text = text))
        if (list.size > MAX_MESSAGES) list.subList(0, list.size - MAX_MESSAGES).clear()
        _messages.value = list
    }

    private fun preview(text: String): String {
        val clean = text.trim()
        return if (clean.length <= TOOL_PREVIEW) clean else clean.take(TOOL_PREVIEW) + "…"
    }

    private fun normalizeHistory() {
        if (history.isEmpty()) return
        val fixed = ArrayList<AiHistoryItem>(history.size)
        var index = 0
        while (index < history.size) {
            val item = history[index]
            fixed.add(item)
            if (item is AiHistoryItem.Assistant && item.calls.isNotEmpty()) {
                val found = HashSet<String>()
                var next = index + 1
                while (next < history.size && history[next] is AiHistoryItem.ToolResult) {
                    val result = history[next] as AiHistoryItem.ToolResult
                    found.add(result.callId)
                    fixed.add(result)
                    next++
                }
                for (call in item.calls) {
                    if (call.id !in found) {
                        fixed.add(AiHistoryItem.ToolResult(call.id, call.name, "Отменено"))
                    }
                }
                index = next
                continue
            }
            index++
        }
        history.clear()
        history.addAll(fixed)
    }

    private fun trimHistory() {
        if (history.size <= MAX_HISTORY) return
        val target = history.size - MAX_HISTORY
        var cut = -1
        for (i in target until history.size) {
            if (history[i] is AiHistoryItem.User) {
                cut = i
                break
            }
        }
        if (cut > 0) history.subList(0, cut).clear()
    }
}
