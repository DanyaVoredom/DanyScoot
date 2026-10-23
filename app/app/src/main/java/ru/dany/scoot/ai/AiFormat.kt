package ru.dany.scoot.ai

import androidx.annotation.StringRes
import ru.dany.scoot.R

enum class AiFormat(
    val id: String,
    @param:StringRes val labelRes: Int,
) {
    AUTO("auto", R.string.ai_format_auto),
    OPENAI_CHAT("openai_chat", R.string.ai_format_chat),
    OPENAI_RESPONSES("openai_responses", R.string.ai_format_responses),
    ANTHROPIC("anthropic", R.string.ai_format_anthropic);

    fun resolve(baseUrl: String): AiFormat {
        if (this != AUTO) return this
        val lower = baseUrl.lowercase()
        return when {
            lower.contains("anthropic") || lower.contains("/messages") -> ANTHROPIC
            lower.contains("/responses") -> OPENAI_RESPONSES
            else -> OPENAI_CHAT
        }
    }

    companion object {
        fun fromId(id: String?): AiFormat =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO
    }
}
