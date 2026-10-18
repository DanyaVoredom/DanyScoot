package ru.dany.scoot.ble

object YandexProtocol {

    const val OPCODE_LOCK = 0x15
    const val OPCODE_UNLOCK = 0x05
    const val OPCODE_SOUND = 0xA2
    const val OPCODE_CABLE = 0x81
    const val OPCODE_BATTERY_COVER = 0xC0

    fun auth(deviceId: Int, key: String): ByteArray {
        require(deviceId in 0..0xFFFF) { "Device id must be 16-bit" }
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        require(keyBytes.size <= 9) { "AUTH key must be at most 9 bytes" }
        val frame = ByteArray(15)
        frame[0] = (deviceId ushr 8).toByte()
        frame[1] = deviceId.toByte()
        frame[2] = 0x08
        frame[3] = 0x00
        frame[4] = 0x00
        frame[5] = 0x01
        keyBytes.copyInto(frame, 6)
        return frame
    }

    fun lock1(deviceId: Int, session: Int): ByteArray =
        simple(deviceId, session, OPCODE_LOCK, 0x01)

    fun lock2(deviceId: Int, session: Int): ByteArray =
        simple(deviceId, session, OPCODE_LOCK, 0x02)

    fun unlock1(deviceId: Int, session: Int, nonce: ByteArray, timestampSeconds: Long): ByteArray {
        require(nonce.size == 4) { "Nonce must be 4 bytes" }
        val ts = timestampSeconds.toInt()
        val frame = ByteArray(17)
        frame[0] = (deviceId ushr 8).toByte()
        frame[1] = deviceId.toByte()
        frame[2] = 0x0A
        frame[3] = 0x00
        frame[4] = session.toByte()
        frame[5] = OPCODE_UNLOCK.toByte()
        frame[6] = 0x01
        nonce.copyInto(frame, 7)
        frame[11] = (ts ushr 24).toByte()
        frame[12] = (ts ushr 16).toByte()
        frame[13] = (ts ushr 8).toByte()
        frame[14] = ts.toByte()
        frame[15] = 0x00
        frame[16] = 0x00
        return frame
    }

    fun unlock2(deviceId: Int, session: Int): ByteArray =
        simple(deviceId, session, OPCODE_UNLOCK, 0x02)

    fun sound(deviceId: Int, session: Int): ByteArray =
        simple(deviceId, session, OPCODE_SOUND, 0x02)

    fun cableUnlock(deviceId: Int, session: Int): ByteArray =
        simple(deviceId, session, OPCODE_CABLE, 0x01)

    fun batteryCover(deviceId: Int, session: Int): ByteArray =
        simple(deviceId, session, OPCODE_BATTERY_COVER, 0x01)

    fun parseAuth(data: ByteArray): Int? =
        if (data.size > 7) data[7].toInt() and 0xFF else null

    fun parseLock(data: ByteArray): Boolean =
        data.size > 6 && (data[6].toInt() and 0xFF) == 1

    fun parseUnlock(data: ByteArray): Boolean =
        data.size > 6 && (data[6].toInt() and 0xFF) == 1

    fun parseCable(data: ByteArray): Boolean =
        data.size > 7 && (data[7].toInt() and 0xFF) == 0

    fun parseSound(data: ByteArray): Boolean =
        data.size > 7 && (data[7].toInt() and 0xFF) == 0

    private fun simple(deviceId: Int, session: Int, opcode: Int, step: Int): ByteArray {
        require(deviceId in 0..0xFFFF) { "Device id must be 16-bit" }
        require(session in 0..0xFF) { "Session must be a byte" }
        return byteArrayOf(
            (deviceId ushr 8).toByte(),
            deviceId.toByte(),
            0x01,
            0x00,
            session.toByte(),
            opcode.toByte(),
            step.toByte(),
            0x00,
        )
    }
}
