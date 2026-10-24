package ru.dany.scoot.ble

import java.util.Locale

data class IotInfo(
    val value: Int,
    val flags: Int,
    val locked: Boolean,
    val version: String,
    val text: String? = null,
)

data class VehicleInfo(
    val batteryPercent: Int,
    val mode: Int,
    val speed: Double,
    val singleMileage: Int,
    val remainingRange: Int,
)

data class RealtimeInfo(
    val batteryPercent: Int,
    val speed: Double,
    val current: Double,
    val flags: Int,
)

data class RfidPair(
    val first: Long,
    val second: Long,
)

object ScooterCodec {

    const val CMD_KEY = 0x01
    const val CMD_UNLOCK = 0x05
    const val CMD_ERROR = 0x10
    const val CMD_LOCK = 0x15
    const val CMD_DEVICE_INFO = 0x31
    const val CMD_SCOOTER_INFO = 0x60
    const val CMD_SCOOTER_SET = 0x61
    const val CMD_REALTIME = 0x63
    const val CMD_RFID = 0x85
    const val CMD_RFID_DELETE = 0x86
    const val CMD_EJECT_BATTERY = 0x81
    const val CMD_FIND_CAR = 0xA2
    const val CMD_BATTERY_COVER = 0xC0
    const val CMD_IOT_RESTART = 0x92
    const val CMD_CLEAR_BT = 0x54
    const val CMD_COLOR_LIGHT = 0x82
    const val CMD_POWER_CONTROL = 0x91
    const val CMD_FW_INFO = 0xFA
    const val CMD_FW_STATUS = 0xFB
    const val CMD_FW_PACK = 0xFC

    val KEY_PRESETS: List<String> = listOf("4BKNwi77", "w4S3Hqn5")

    private val LOCK_SUCCESS_STATUSES = setOf(0, 1, 2)
    private val EJECT_SUCCESS_STATUSES = setOf(0, 1)

    fun keyCommand(key: String): ByteArray =
        FrameCodec.build(CMD_KEY, 0, key.toByteArray(Charsets.UTF_8))

    fun keyCandidates(deviceName: String, currentKey: String, wordlist: List<String> = emptyList()): List<String> {
        val out = LinkedHashSet<String>()
        wordlist.forEach { addKeyCandidate(out, it) }
        KEY_PRESETS.forEach { addKeyCandidate(out, it) }
        addKeyCandidate(out, currentKey)
        val compact = deviceName.filter { it.isLetterOrDigit() }
        if (compact.isNotEmpty()) {
            val tail = compact.takeLast(8)
            addKeyCandidate(out, tail)
            addKeyCandidate(out, tail.uppercase(Locale.ROOT))
            addKeyCandidate(out, tail.lowercase(Locale.ROOT))
        }
        val digits = deviceName.filter { it.isDigit() }
        if (digits.isNotEmpty()) {
            addKeyCandidate(out, digits.takeLast(8).padStart(8, '0'))
            addKeyCandidate(out, digits.takeLast(6).padStart(6, '0'))
        }
        return out.toList()
    }

    private fun addKeyCandidate(out: MutableSet<String>, raw: String) {
        val key = raw.trim()
        if (key.isEmpty() || key.length > 16) return
        if (key.any { it.code !in 0x20..0x7E }) return
        out.add(key)
    }

    fun unlockCommand(actualKey: Int): ByteArray =
        FrameCodec.build(CMD_UNLOCK, actualKey, ByteArray(10) { 0x01 })

    fun unlockCommandTimestamp(actualKey: Int, timeMs: Long): ByteArray {
        val seconds = timeMs / 1000L
        val payload = byteArrayOf(
            0x01,
            0x31,
            0x31,
            0x32,
            0x39,
            ((seconds shr 24) and 0xFF).toByte(),
            ((seconds shr 16) and 0xFF).toByte(),
            ((seconds shr 8) and 0xFF).toByte(),
            (seconds and 0xFF).toByte(),
            0x00,
        )
        return FrameCodec.build(CMD_UNLOCK, actualKey, payload)
    }

    fun isLockStatusSuccess(payload: ByteArray): Boolean =
        payload.isEmpty() || (payload[0].toInt() and 0xFF) in LOCK_SUCCESS_STATUSES

    fun isEjectSuccess(payload: ByteArray): Boolean {
        if (payload.size < 2) return true
        val status = payload[1].toInt() and 0xFF
        return status in EJECT_SUCCESS_STATUSES
    }

    fun lockCommand(actualKey: Int): ByteArray =
        FrameCodec.build(CMD_LOCK, actualKey, byteArrayOf(0x01))

    fun setScooterCommand(actualKey: Int, headlight: Int, mode: Int, throttle: Int): ByteArray =
        FrameCodec.build(
            CMD_SCOOTER_SET,
            actualKey,
            byteArrayOf(headlight.toByte(), mode.toByte(), throttle.toByte(), 0x00),
        )

    fun ejectBatteryCommand(actualKey: Int): ByteArray =
        FrameCodec.build(CMD_EJECT_BATTERY, actualKey, byteArrayOf(0x05))

    fun findCarCommand(actualKey: Int): ByteArray =
        FrameCodec.build(CMD_FIND_CAR, actualKey, byteArrayOf(0x01))

    fun spamUnlockCommand(actualKey: Int, timeMs: Long): ByteArray =
        unlockCommandTimestamp(actualKey, timeMs)

    fun isAsciiDeviceInfo(payload: ByteArray): Boolean {
        var end = payload.size
        while (end > 0 && payload[end - 1].toInt() == 0) end--
        if (end == 0 || payload.none { (it.toInt() and 0xFF) == 0x3A }) return false
        for (i in 0 until end) {
            val value = payload[i].toInt() and 0xFF
            if (value !in 0x20..0x7E && value != 0x09 && value != 0x0A && value != 0x0D) return false
        }
        return true
    }

    fun parseIotInfo(payload: ByteArray): IotInfo? {
        if (payload.size < 7) return null
        val value = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val flags = payload[2].toInt() and 0xFF
        val locked = (flags and 0x01) == 0x00
        val version = buildString {
            append(payload[4].toInt() and 0xFF)
            append('.')
            append(payload[5].toInt() and 0xFF)
            append('.')
            append(payload[6].toInt() and 0xFF)
        }
        return IotInfo(value = value, flags = flags, locked = locked, version = version)
    }

    fun parseIotText(payload: ByteArray): IotInfo {
        var end = payload.size
        while (end > 0 && payload[end - 1].toInt() == 0) end--
        val text = String(payload, 0, end, Charsets.UTF_8).trim()
        return IotInfo(value = 0, flags = 0, locked = false, version = "", text = text)
    }

    fun parseVehicleInfo(payload: ByteArray): VehicleInfo? {
        if (payload.size < 8) return null
        val battery = payload[0].toInt() and 0xFF
        val mode = payload[1].toInt() and 0xFF
        val speedRaw = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
        val mileageRaw = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
        val rangeRaw = ((payload[6].toInt() and 0xFF) shl 8) or (payload[7].toInt() and 0xFF)
        return VehicleInfo(
            batteryPercent = battery,
            mode = mode,
            speed = speedRaw / 10.0,
            singleMileage = mileageRaw * 10,
            remainingRange = rangeRaw * 10,
        )
    }

    fun parseRfid(payload: ByteArray): Long? {
        if (payload.size < 4) return null
        var value = 0L
        for (i in 0 until 4) {
            value = (value shl 8) or (payload[i].toLong() and 0xFF)
        }
        return value
    }

    fun parseRealtime(payload: ByteArray): RealtimeInfo? {
        if (payload.size < 6) return null
        val battery = payload[0].toInt() and 0xFF
        val speedRaw = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
        val currentRaw = ((payload[3].toInt() and 0xFF) shl 8) or (payload[4].toInt() and 0xFF)
        val flags = payload[5].toInt() and 0xFF
        return RealtimeInfo(
            batteryPercent = battery,
            speed = speedRaw / 10.0,
            current = currentRaw / 100.0,
            flags = flags,
        )
    }

    fun parseRfidPair(payload: ByteArray): RfidPair? {
        if (payload.size < 8) return null
        val first = parseRfid(payload) ?: return null
        val second = parseRfid(payload.copyOfRange(4, 8)) ?: return null
        return RfidPair(first, second)
    }

    fun setScooterFromUi(actualKey: Int, headlight: Boolean, modeIndex: Int, throttle: Boolean): ByteArray {
        val hl = if (headlight) 2 else 1
        val th = if (throttle) 2 else 1
        val mode = (modeIndex + 1).coerceIn(1, 3)
        return setScooterCommand(actualKey, hl, mode, th)
    }
}
