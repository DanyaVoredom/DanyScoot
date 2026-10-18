package ru.dany.urent.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCodecTest {

    @Test
    fun crc8MatchesMaximCheckValue() {
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0xA1, Crc8.compute(data))
    }

    @Test
    fun crc8OfEmptyIsZero() {
        assertEquals(0, Crc8.compute(ByteArray(0)))
    }

    @Test
    fun crc16MatchesModbusCheckValue() {
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0x4B37, Crc16.compute(data))
    }

    @Test
    fun frameRoundTripKeepsFields() {
        val payload = byteArrayOf(0x01, 0x31, 0x31, 0x32, 0x39, 0x00)
        val frame = FrameCodec.build(0x05, 0x42, payload)
        assertEquals(FRAME_HEADER_1, frame[0].toInt() and 0xFF)
        assertEquals(FRAME_HEADER_2, frame[1].toInt() and 0xFF)
        assertEquals(payload.size, frame[2].toInt() and 0xFF)
        assertEquals(payload.size + 7, frame.size)
        val wire = frame[3].toInt() and 0xFF
        assertTrue(wire in 0..255)
        val parsed = FrameCodec.parse(frame)
        assertEquals(0x05, parsed.cmd)
        assertEquals(0x42, parsed.key)
        assertTrue(parsed.rand in 1..254)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun frameKeyAndCommandAreXoredWithUnshiftedRandom() {
        val frame = FrameCodec.build(0x81, 0x42, byteArrayOf(0x05))
        val shifted = frame[3].toInt() and 0xFF
        val r = (shifted - RAND_OFFSET) and 0xFF
        assertEquals((0x42 xor r) and 0xFF, frame[4].toInt() and 0xFF)
        assertEquals((0x81 xor r) and 0xFF, frame[5].toInt() and 0xFF)
        assertEquals(((0x05 xor r) and 0xFF), frame[6].toInt() and 0xFF)
    }

    @Test
    fun buildRejectsOversizedPayload() {
        try {
            FrameCodec.build(0x01, 0, ByteArray(256))
            throw AssertionError("expected FrameException")
        } catch (e: FrameCodec.FrameException) {
            assertEquals("Payload too long: 256", e.message)
        }
    }

    @Test
    fun parseRejectsBadCrc() {
        val frame = FrameCodec.build(0x15, 0x42, byteArrayOf(0x01))
        frame[frame.size - 1] = (frame[frame.size - 1] + 1).toByte()
        try {
            FrameCodec.parse(frame)
            throw AssertionError("expected FrameException")
        } catch (e: FrameCodec.FrameException) {
            assertTrue(e.message.orEmpty().startsWith("CRC mismatch"))
        }
    }

    @Test
    fun findFramesSkipsNoiseAndSplitsStream() {
        val a = FrameCodec.build(0x01, 0x00, "4BKNwi77".toByteArray(Charsets.US_ASCII))
        val b = FrameCodec.build(0x60, 0x42, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val stream = byteArrayOf(0x00, 0x11, 0x22) + a + b
        val (frames, rest) = FrameCodec.findFrames(stream)
        assertEquals(2, frames.size)
        assertEquals(0x01, frames[0].cmd)
        assertEquals(0x60, frames[1].cmd)
        assertEquals(0, rest.size)
    }

    @Test
    fun findFramesKeepsPartialTail() {
        val frame = FrameCodec.build(0x60, 0x42, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val partial = frame.copyOfRange(0, 5)
        val (frames, rest) = FrameCodec.findFrames(partial)
        assertEquals(0, frames.size)
        assertArrayEquals(partial, rest)
    }

    @Test
    fun findFramesKeepsTrailingHeaderByte() {
        val tail = byteArrayOf(0x11, 0x22, FRAME_HEADER_1.toByte())
        val (frames, rest) = FrameCodec.findFrames(tail)
        assertEquals(0, frames.size)
        assertArrayEquals(byteArrayOf(FRAME_HEADER_1.toByte()), rest)
    }

    @Test
    fun findFramesResynchronizesAfterCorruptFrame() {
        val valid = FrameCodec.build(0x60, 0x42, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val corrupt = byteArrayOf(FRAME_HEADER_1.toByte(), FRAME_HEADER_2.toByte(), 1, 0, 0, 0, 0, 0)
        val (frames, rest) = FrameCodec.findFrames(corrupt + valid)
        assertEquals(1, frames.size)
        assertEquals(0x60, frames[0].cmd)
        assertEquals(0, rest.size)
    }

    @Test
    fun parseRejectsBadHeader() {
        val frame = FrameCodec.build(0x15, 0x42, byteArrayOf(0x01))
        frame[0] = 0x00
        try {
            FrameCodec.parse(frame)
            throw AssertionError("expected FrameException")
        } catch (e: FrameCodec.FrameException) {
            assertTrue(e.message.orEmpty().startsWith("Invalid frame header"))
        }
    }

    @Test
    fun parseRejectsTruncatedFrame() {
        val frame = FrameCodec.build(0x15, 0x42, byteArrayOf(0x01))
        try {
            FrameCodec.parse(frame.copyOfRange(0, frame.size - 1))
            throw AssertionError("expected FrameException")
        } catch (e: FrameCodec.FrameException) {
            assertTrue(e.message.orEmpty().startsWith("Invalid frame length"))
        }
    }

    @Test
    fun hexFormattingUsesUpperCaseAndSeparator() {
        assertEquals("", hex(ByteArray(0)))
        assertEquals("0A FF", hex(byteArrayOf(0x0A, 0xFF.toByte())))
        assertEquals("0A-FF", hex(byteArrayOf(0x0A, 0xFF.toByte()), "-"))
        assertEquals("00", hexByte(0))
        assertEquals("A5", hexByte(0x1A5))
    }

    @Test
    fun parseHexAcceptsCommonSeparators() {
        assertArrayEquals(byteArrayOf(0x01, 0xAB.toByte()), parseHex("01 ab"))
        assertArrayEquals(byteArrayOf(0x10, 0x00), parseHex("0x10:00"))
        assertArrayEquals(ByteArray(0), parseHex("   "))
        assertNull(parseHex("01a"))
        assertNull(parseHex("01 zz"))
    }
}
