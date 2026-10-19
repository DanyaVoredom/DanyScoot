package ru.dany.scoot.ble

import java.util.UUID

enum class WhooshOfflineKind { OK, SMS, STATUS, ERROR, MALFORMED, UNKNOWN }
enum class WhooshOnlineKind { SUCCESS, ERROR, IGNORED }

data class WhooshOfflineResponse(
    val kind: WhooshOfflineKind,
    val status: Int?,
    val raw: String,
)

data class WhooshOnlineResponse(
    val kind: WhooshOnlineKind,
    val text: String,
)

object WhooshProtocol {

    val OFFLINE_SERVICE: UUID = UUID.fromString("0000e018-0000-1000-8000-00805f9b34fb")
    val OFFLINE_WRITE: UUID = UUID.fromString("0000dd1a-0000-1000-8000-00805f9b34fb")
    val OFFLINE_NOTIFY: UUID = UUID.fromString("0000dd2a-0000-1000-8000-00805f9b34fb")

    val ONLINE_SERVICE: UUID = UUID.fromString("00001c18-0000-1000-8000-00805f9b34fb")
    val ONLINE_WRITE: UUID = UUID.fromString("00009f2a-0000-1000-8000-00805f9b34fb")
    val ONLINE_NOTIFY: UUID = UUID.fromString("00009f2a-0000-1000-8000-00805f9b34fb")
    val ONLINE_KEY: UUID = UUID.fromString("00003d2a-0000-1000-8000-00805f9b34fb")
    val ONLINE_DIGITAL: UUID = UUID.fromString("0000572a-0000-1000-8000-00805f9b34fb")

    const val OFFLINE_MTU = 185
    const val OFFLINE_RETRY_MS = 500L
    const val OFFLINE_TIMEOUT_MS = 30000L
    const val ONLINE_RETRY_MS = 1500L
    const val ONLINE_TIMEOUT_MS = 15000L
    const val SMS_PHONE = "+79994445089"

    private const val BASE64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun offlinePayload(commandIdHex: String, attempt: Int, tokenHex: String): ByteArray {
        require(attempt > 0) { "Attempt must be positive" }
        val command = commandIdHex.trim().uppercase()
        require(command.length == 2 && command.all { it.isHexDigit() }) {
            "Command id must be exactly 1 byte in hex"
        }
        val unique = Integer.toString(attempt, 16).uppercase().padStart(8, '0')
        require(unique.length == 8) { "Unique id must be exactly 4 bytes in hex" }
        val token = tokenHex.trim().uppercase()
        require(token.length % 2 == 0 && token.all { it.isHexDigit() }) {
            "Token hex must have even length"
        }
        return parseHex(command + unique + token)
            ?: throw IllegalArgumentException("Invalid offline payload hex")
    }

    fun tokenHex(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("hex:", ignoreCase = true)) {
            return parseHex(text.substring(4))?.toHexUpper()
        }
        val bytes = base64Decode(text) ?: return parseHex(text)?.toHexUpper()
        return bytes.toHexUpper()
    }

    fun decodeHex(input: String): ByteArray? {
        val bytes = parseHex(input) ?: return null
        return bytes.takeIf { it.isNotEmpty() }
    }

    fun parseOfflineResponse(data: ByteArray): WhooshOfflineResponse {
        val raw = hex(data)
        if (data.size >= 3 && isTriple(data, 0xEE)) {
            return WhooshOfflineResponse(WhooshOfflineKind.ERROR, null, raw)
        }
        if (data.size >= 3 && isTriple(data, 0xBB)) {
            if (data.size < 9) {
                return WhooshOfflineResponse(WhooshOfflineKind.MALFORMED, null, raw)
            }
            val status = data[8].toInt() and 0xFF
            val kind = when (status) {
                0 -> WhooshOfflineKind.OK
                10 -> WhooshOfflineKind.SMS
                in 1..11 -> WhooshOfflineKind.STATUS
                else -> WhooshOfflineKind.UNKNOWN
            }
            return WhooshOfflineResponse(kind, status, raw)
        }
        return WhooshOfflineResponse(WhooshOfflineKind.UNKNOWN, null, raw)
    }

    fun parseOnlineResponse(data: ByteArray): WhooshOnlineResponse {
        val text = String(data, Charsets.UTF_8)
        val kind = when {
            text.startsWith("CMD_EXE_SUCCESS") -> WhooshOnlineKind.SUCCESS
            text.startsWith("CMD_EXE_ERROR") -> WhooshOnlineKind.ERROR
            else -> WhooshOnlineKind.IGNORED
        }
        return WhooshOnlineResponse(kind, text)
    }

    fun statusName(code: Int): String = when (code) {
        0 -> "STATUS_OK"
        1 -> "STATUS_UNKNOWN_CMD"
        2 -> "STATUS_INVALID_SIG"
        3 -> "STATUS_EXPIRED"
        4 -> "STATUS_REPLAY"
        5 -> "STATUS_RTC_NOT_SYNCED"
        6 -> "STATUS_TRIP_ACTIVE"
        7 -> "STATUS_NO_ACTIVE_TRIP"
        8 -> "STATUS_INVALID_USER"
        9 -> "STATUS_LOG_FULL"
        10 -> "STATUS_SCOOTER_ONLINE"
        11 -> "STATUS_BATT_LOW_CAP"
        else -> "STATUS_UNKNOWN_$code"
    }

    private fun base64Decode(input: String): ByteArray? {
        val text = input.filterNot { it.isWhitespace() }
        if (text.isEmpty()) return ByteArray(0)
        val out = ArrayList<Byte>(text.length * 3 / 4 + 3)
        var buffer = 0
        var bits = 0
        for (ch in text) {
            if (ch == '=') break
            val value = BASE64_ALPHABET.indexOf(ch)
            if (value < 0) return null
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
                buffer = if (bits == 0) 0 else buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }

    private fun isTriple(data: ByteArray, value: Int): Boolean {
        val v = value.toByte()
        return data[0] == v && data[1] == v && data[2] == v
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || lowercaseChar() in 'a'..'f'

    private fun ByteArray.toHexUpper(): String =
        joinToString("") { hexByte(it.toInt()) }
}
