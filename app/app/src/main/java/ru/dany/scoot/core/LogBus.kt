package ru.dany.scoot.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { INFO, TX, RX, OK, WARN, ERR }

data class LogLine(
    val timeMs: Long,
    val level: LogLevel,
    val tag: String,
    val text: String,
)

object LogBus {

    private const val MAX_LINES = 1000

    private val buffer = ArrayList<LogLine>(MAX_LINES)
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())

    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    @Synchronized
    fun log(level: LogLevel, tag: String, text: String) {
        val line = LogLine(System.currentTimeMillis(), level, tag, text)
        buffer.add(line)
        if (buffer.size > MAX_LINES) {
            buffer.subList(0, buffer.size - MAX_LINES).clear()
        }
        _lines.value = ArrayList(buffer)
    }

    fun info(tag: String, text: String) = log(LogLevel.INFO, tag, text)
    fun tx(tag: String, text: String) = log(LogLevel.TX, tag, text)
    fun rx(tag: String, text: String) = log(LogLevel.RX, tag, text)
    fun ok(tag: String, text: String) = log(LogLevel.OK, tag, text)
    fun warn(tag: String, text: String) = log(LogLevel.WARN, tag, text)
    fun err(tag: String, text: String) = log(LogLevel.ERR, tag, text)

    @Synchronized
    fun clear() {
        buffer.clear()
        _lines.value = emptyList()
    }
}
