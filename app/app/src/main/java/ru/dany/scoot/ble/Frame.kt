package ru.dany.scoot.ble

import kotlin.random.Random

object Crc8 {
    private val table = IntArray(256)

    init {
        for (i in 0 until 256) {
            var c = i
            for (j in 0 until 8) {
                c = if (c and 1 != 0) (c shr 1) xor 0x8C else c shr 1
            }
            table[i] = c and 0xFF
        }
    }

    fun compute(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0
        for (i in from until to) {
            crc = table[(crc xor (data[i].toInt() and 0xFF)) and 0xFF]
        }
        return crc and 0xFF
    }
}

object Crc16 {
    fun compute(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in from until to) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (j in 0 until 8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }
}

const val FRAME_HEADER_1 = 0xA3
const val FRAME_HEADER_2 = 0xA4
const val RAND_OFFSET = 0x32

data class ScooterFrame(
    val cmd: Int,
    val key: Int,
    val rand: Int,
    val payload: ByteArray,
    val raw: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is ScooterFrame && raw.contentEquals(other.raw)
    override fun hashCode(): Int = raw.contentHashCode()
}

object FrameCodec {

    class FrameException(message: String) : Exception(message)

    fun build(cmd: Int, key: Int, payload: ByteArray): ByteArray {
        if (payload.size > 0xFF) throw FrameException("Payload too long: ${payload.size}")
        val r = Random.nextInt(0xFE) + 1
        val rand = (r + RAND_OFFSET) and 0xFF
        val frame = ByteArray(6 + payload.size)
        frame[0] = FRAME_HEADER_1.toByte()
        frame[1] = FRAME_HEADER_2.toByte()
        frame[2] = (payload.size and 0xFF).toByte()
        frame[3] = rand.toByte()
        frame[4] = (key xor r).toByte()
        frame[5] = (cmd xor r).toByte()
        for (i in payload.indices) {
            frame[6 + i] = ((payload[i].toInt() and 0xFF) xor r).toByte()
        }
        val out = frame.copyOf(frame.size + 1)
        out[frame.size] = Crc8.compute(frame).toByte()
        return out
    }

    fun parse(data: ByteArray): ScooterFrame {
        if (data.size < 7) throw FrameException("Frame too short: ${data.size}")
        val h1 = data[0].toInt() and 0xFF
        val h2 = data[1].toInt() and 0xFF
        if (h1 != FRAME_HEADER_1 || h2 != FRAME_HEADER_2) {
            throw FrameException("Invalid frame header: ${hex(data)}")
        }
        val len = data[2].toInt() and 0xFF
        if (data.size != len + 7) {
            throw FrameException("Invalid frame length expected=${len + 7} got=${data.size}")
        }
        val expected = Crc8.compute(data, 0, data.size - 1)
        val actual = data[data.size - 1].toInt() and 0xFF
        if (expected != actual) {
            throw FrameException("CRC mismatch expected=$expected actual=$actual")
        }
        val r = ((data[3].toInt() and 0xFF) - RAND_OFFSET) and 0xFF
        val key = (data[4].toInt() and 0xFF) xor r
        val cmd = (data[5].toInt() and 0xFF) xor r
        val payload = ByteArray(len)
        for (i in 0 until len) {
            payload[i] = (((data[6 + i].toInt() and 0xFF) xor r) and 0xFF).toByte()
        }
        return ScooterFrame(cmd = cmd, key = key, rand = r, payload = payload, raw = data)
    }

    fun findFrames(buffer: ByteArray): Pair<List<ScooterFrame>, ByteArray> {
        val frames = ArrayList<ScooterFrame>()
        var offset = 0
        while (offset < buffer.size) {
            val start = buffer.indexOfHeader(offset)
            if (start < 0) return frames to ByteArray(0)
            if (start + 6 >= buffer.size) return frames to buffer.copyOfRange(start, buffer.size)
            val len = buffer[start + 2].toInt() and 0xFF
            val total = len + 7
            if (start + total > buffer.size) return frames to buffer.copyOfRange(start, buffer.size)
            val candidate = buffer.copyOfRange(start, start + total)
            try {
                frames.add(parse(candidate))
                offset = start + total
            } catch (e: FrameException) {
                offset = start + 1
            }
        }
        return frames to ByteArray(0)
    }

    private fun ByteArray.indexOfHeader(from: Int): Int {
        var index = from
        while (index < size) {
            if ((this[index].toInt() and 0xFF) != FRAME_HEADER_1) {
                index += 1
                continue
            }
            if (index + 1 >= size || (this[index + 1].toInt() and 0xFF) == FRAME_HEADER_2) {
                return index
            }
            index += 1
        }
        return -1
    }
}

private const val HEX_DIGITS = "0123456789ABCDEF"

fun hex(data: ByteArray, separator: String = " "): String {
    if (data.isEmpty()) return ""
    val sb = StringBuilder(data.size * 3)
    for (i in data.indices) {
        if (i > 0) sb.append(separator)
        val value = data[i].toInt() and 0xFF
        sb.append(HEX_DIGITS[value ushr 4]).append(HEX_DIGITS[value and 0x0F])
    }
    return sb.toString()
}

fun hexByte(value: Int): String {
    val v = value and 0xFF
    return String(charArrayOf(HEX_DIGITS[v ushr 4], HEX_DIGITS[v and 0x0F]))
}

fun parseHex(text: String): ByteArray? {
    var clean = text.trim()
    if (clean.startsWith("0x", ignoreCase = true)) clean = clean.substring(2)
    clean = clean.filterNot { it.isWhitespace() || it == ',' || it == ':' || it == '-' }
    if (clean.isEmpty()) return ByteArray(0)
    if (clean.length % 2 != 0) return null
    if (!clean.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
    return ByteArray(clean.length / 2) { i ->
        clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
