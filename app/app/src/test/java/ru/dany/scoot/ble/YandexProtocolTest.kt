package ru.dany.scoot.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexProtocolTest {

    private val deviceId = 0xD0D1
    private val session = 0xC0
    private val nonce = byteArrayOf(0xC0.toByte(), 0xC1.toByte(), 0xC2.toByte(), 0xC3.toByte())

    @Test
    fun authMatchesReport() {
        val expected = byteArrayOf(
            0xD0.toByte(), 0xD1.toByte(), 0x08, 0x00, 0x00, 0x01,
            0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
        )
        assertArrayEquals(expected, YandexProtocol.auth(deviceId, "ABCDEFGHI"))
    }

    @Test
    fun authWithEmptyKeyIsZeroPadded() {
        val frame = YandexProtocol.auth(deviceId, "")
        assertEquals(15, frame.size)
        assertTrue(frame.copyOfRange(6, 15).all { it.toInt() == 0 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun authRejectsLongKey() {
        YandexProtocol.auth(deviceId, "ABCDEFGHIJ")
    }

    @Test
    fun unlockStepOneMatchesReport() {
        val expected = byteArrayOf(
            0xD0.toByte(), 0xD1.toByte(), 0x0A, 0x00, 0xC0.toByte(), 0x05, 0x01,
            0xC0.toByte(), 0xC1.toByte(), 0xC2.toByte(), 0xC3.toByte(),
            0x11, 0x22, 0x33, 0x44, 0x00, 0x00,
        )
        assertArrayEquals(expected, YandexProtocol.unlock1(deviceId, session, nonce, 0x11223344L))
    }

    @Test
    fun lockStepsMatchReport() {
        assertArrayEquals(
            byteArrayOf(0xD0.toByte(), 0xD1.toByte(), 0x01, 0x00, 0xC0.toByte(), 0x15, 0x01, 0x00),
            YandexProtocol.lock1(deviceId, session),
        )
        assertArrayEquals(
            byteArrayOf(0xD0.toByte(), 0xD1.toByte(), 0x01, 0x00, 0xC0.toByte(), 0x15, 0x02, 0x00),
            YandexProtocol.lock2(deviceId, session),
        )
    }

    @Test
    fun unlockStepTwoMatchesReport() {
        assertArrayEquals(
            byteArrayOf(0xD0.toByte(), 0xD1.toByte(), 0x01, 0x00, 0xC0.toByte(), 0x05, 0x02, 0x00),
            YandexProtocol.unlock2(deviceId, session),
        )
    }

    @Test
    fun soundCableBatteryMatchReport() {
        assertArrayEquals(
            byteArrayOf(0xD0.toByte(), 0xD1.toByte(), 0x01, 0x00, 0xC0.toByte(), 0xA2.toByte(), 0x02, 0x00),
            YandexProtocol.sound(deviceId, session),
        )
        assertArrayEquals(
            byteArrayOf(0xD0.toByte(), 0xD1.toByte(), 0x01, 0x00, 0xC0.toByte(), 0x81.toByte(), 0x01, 0x00),
            YandexProtocol.cableUnlock(deviceId, session),
        )
        assertArrayEquals(
            byteArrayOf(0xD0.toByte(), 0xD1.toByte(), 0x01, 0x00, 0xC0.toByte(), 0xC0.toByte(), 0x01, 0x00),
            YandexProtocol.batteryCover(deviceId, session),
        )
    }

    @Test
    fun parseAuthSession() {
        val frame = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0x2A)
        assertEquals(0x2A, YandexProtocol.parseAuth(frame))
        assertNull(YandexProtocol.parseAuth(byteArrayOf(0, 0, 0)))
    }

    @Test
    fun parseLockAndUnlock() {
        assertTrue(YandexProtocol.parseLock(byteArrayOf(0, 0, 0, 0, 0, 0, 1, 0)))
        assertFalse(YandexProtocol.parseLock(byteArrayOf(0, 0, 0, 0, 0, 0, 2, 0)))
        assertTrue(YandexProtocol.parseUnlock(byteArrayOf(0, 0, 0, 0, 0, 0, 1, 0)))
        assertFalse(YandexProtocol.parseUnlock(byteArrayOf(0, 0, 0, 0, 0, 0, 2, 0)))
    }

    @Test
    fun parseCableAndSound() {
        assertTrue(YandexProtocol.parseCable(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)))
        assertFalse(YandexProtocol.parseCable(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1)))
        assertTrue(YandexProtocol.parseSound(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)))
        assertFalse(YandexProtocol.parseSound(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0xFF.toByte())))
    }
}
