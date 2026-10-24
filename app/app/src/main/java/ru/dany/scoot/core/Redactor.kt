package ru.dany.scoot.core

import java.util.Locale

object Redactor {

    private const val MASK = "***"

    private val MAC = Regex("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")
    private val BEARER = Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-]{6,}")
    private val API_KEY_HEADER = Regex("(?i)((?:x-api-key|api[-_]?key|apikey|authorization)[\"']?\\s*[:=]\\s*[\"']?)[A-Za-z0-9._\\-]{6,}")
    private val SECRET_WORD = Regex("(?i)((?:serial|serialnumber|serial_number|token|secret|password|passwd|ble[_-]?key|key)[\"']?\\s*[:=]\\s*[\"']?)([^\\s,\"'}]{3,})")
    private val OPENAI_KEY = Regex("sk-[A-Za-z0-9_\\-]{8,}")
    private val GITHUB_TOKEN = Regex("gh[pousr]_[A-Za-z0-9]{20,}")

    fun address(value: String): String {
        val parts = value.trim().split(':')
        if (parts.size != 6) return MASK
        return "**:**:**:**:${parts[4].uppercase(Locale.ROOT)}:${parts[5].uppercase(Locale.ROOT)}"
    }

    fun text(input: String, secrets: Collection<String> = emptyList()): String {
        var out = input
        for (secret in secrets) {
            val value = secret.trim()
            if (value.length < 4) continue
            out = out.replace(value, MASK, ignoreCase = true)
        }
        out = MAC.replace(out) { address(it.value) }
        out = BEARER.replace(out) { match -> match.groupValues[1] + MASK }
        out = API_KEY_HEADER.replace(out) { match -> match.groupValues[1] + MASK }
        out = OPENAI_KEY.replace(out, MASK)
        out = GITHUB_TOKEN.replace(out, MASK)
        out = SECRET_WORD.replace(out) { match -> match.groupValues[1] + MASK }
        return out
    }
}
