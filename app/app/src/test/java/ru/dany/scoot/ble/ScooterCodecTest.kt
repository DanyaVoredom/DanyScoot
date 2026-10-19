package ru.dany.scoot.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScooterCodecTest {

    @Test
    fun keyCommandUsesKeyZeroAndAsciiPayload() {
        val frame = FrameCodec.parse(ScooterCodec.keyCommand("4BKNwi77"))
        assertEquals(0x01, frame.cmd)
        assertEquals(0, frame.key)
        assertArrayEquals("4BKNwi77".toByteArray(Charsets.US_ASCII), frame.payload)
    }

    @Test
    fun unlockCommandMatchesNineHackLayout() {
        val frame = FrameCodec.parse(ScooterCodec.unlockCommand(0x42))
        assertEquals(0x05, frame.cmd)
        assertEquals(0x42, frame.key)
        assertEquals(10, frame.payload.size)
        assertTrue(frame.payload.all { it.toInt() == 1 })
    }

    @Test
    fun lockCommandHasSingleByte() {
        val frame = FrameCodec.parse(ScooterCodec.lockCommand(0x42))
        assertEquals(0x15, frame.cmd)
        assertArrayEquals(byteArrayOf(0x01), frame.payload)
    }

    @Test
    fun ejectCommandHasBatterySelector() {
        val frame = FrameCodec.parse(ScooterCodec.ejectBatteryCommand(0x42))
        assertEquals(0x81, frame.cmd)
        assertArrayEquals(byteArrayOf(0x05), frame.payload)
    }

    @Test
    fun ejectStatusAcceptsBothReferenceLayouts() {
        assertTrue(ScooterCodec.isEjectSuccess(ByteArray(0)))
        assertTrue(ScooterCodec.isEjectSuccess(byteArrayOf(0x01)))
        assertTrue(ScooterCodec.isEjectSuccess(byteArrayOf(0x05, 0x00)))
        assertTrue(ScooterCodec.isEjectSuccess(byteArrayOf(0x05, 0x01)))
        assertFalse(ScooterCodec.isEjectSuccess(byteArrayOf(0x05, 0x02)))
    }

    @Test
    fun findCarUsesSoundOpcode() {
        val frame = FrameCodec.parse(ScooterCodec.findCarCommand(0x42))
        assertEquals(0xA2, frame.cmd)
        assertArrayEquals(byteArrayOf(0x01), frame.payload)
    }

    @Test
    fun setScooterMapsUiValues() {
        val on = FrameCodec.parse(ScooterCodec.setScooterFromUi(0x42, true, 2, true))
        assertEquals(0x61, on.cmd)
        assertArrayEquals(byteArrayOf(0x02, 0x03, 0x02, 0x00), on.payload)
        val off = FrameCodec.parse(ScooterCodec.setScooterFromUi(0x42, false, 0, false))
        assertArrayEquals(byteArrayOf(0x01, 0x01, 0x01, 0x00), off.payload)
    }

    @Test
    fun setScooterClampsModeToProtocolRange() {
        val high = FrameCodec.parse(ScooterCodec.setScooterFromUi(0x42, true, 99, true))
        assertEquals(0x03, high.payload[1].toInt())
        val low = FrameCodec.parse(ScooterCodec.setScooterFromUi(0x42, true, -5, true))
        assertEquals(0x01, low.payload[1].toInt())
    }

    @Test
    fun spamUnlockCarriesTimestampBigEndian() {
        val timeMs = 1_700_000_000_000L
        val seconds = timeMs / 1000L
        val frame = FrameCodec.parse(ScooterCodec.spamUnlockCommand(0x33, timeMs))
        assertEquals(0x05, frame.cmd)
        assertEquals(0x33, frame.key)
        assertEquals(10, frame.payload.size)
        assertArrayEquals(byteArrayOf(0x01, 0x31, 0x31, 0x32, 0x39), frame.payload.copyOfRange(0, 5))
        val stamp = ((frame.payload[5].toLong() and 0xFF) shl 24) or
            ((frame.payload[6].toLong() and 0xFF) shl 16) or
            ((frame.payload[7].toLong() and 0xFF) shl 8) or
            (frame.payload[8].toLong() and 0xFF)
        assertEquals(seconds, stamp)
        assertEquals(0, frame.payload[9].toInt())
    }

    @Test
    fun timestampUnlockMatchesSpamBuilder() {
        val timeMs = 1_700_000_000_000L
        val direct = FrameCodec.parse(ScooterCodec.unlockCommandTimestamp(0x33, timeMs))
        val spam = FrameCodec.parse(ScooterCodec.spamUnlockCommand(0x33, timeMs))
        assertEquals(direct.cmd, spam.cmd)
        assertEquals(direct.key, spam.key)
        assertArrayEquals(direct.payload, spam.payload)
    }

    @Test
    fun lockStatusAcceptsReferenceCodes() {
        assertTrue(ScooterCodec.isLockStatusSuccess(ByteArray(0)))
        assertTrue(ScooterCodec.isLockStatusSuccess(byteArrayOf(0x00)))
        assertTrue(ScooterCodec.isLockStatusSuccess(byteArrayOf(0x01)))
        assertTrue(ScooterCodec.isLockStatusSuccess(byteArrayOf(0x02)))
        assertFalse(ScooterCodec.isLockStatusSuccess(byteArrayOf(0x03)))
        assertFalse(ScooterCodec.isLockStatusSuccess(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun vehicleInfoDecodesFields() {
        val payload = byteArrayOf(
            87, 2,
            0x00, 0xBB.toByte(),
            0x11, 0x36,
            0x0D, 0xAC.toByte(),
        )
        val info = ScooterCodec.parseVehicleInfo(payload)
        assertNotNull(info)
        info!!
        assertEquals(87, info.batteryPercent)
        assertEquals(2, info.mode)
        assertEquals(18.7, info.speed, 0.0001)
        assertEquals(44060, info.singleMileage)
        assertEquals(35000, info.remainingRange)
    }

    @Test
    fun vehicleInfoRejectsShortPayload() {
        assertEquals(null, ScooterCodec.parseVehicleInfo(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun iotInfoLockedFlagIsInverted() {
        val locked = ScooterCodec.parseIotInfo(byteArrayOf(0x14, 0x6E, 0x00, 0x00, 1, 4, 2))
        assertNotNull(locked)
        assertTrue(locked!!.locked)
        assertEquals("1.4.2", locked.version)
        assertEquals(5230, locked.value)
        val unlocked = ScooterCodec.parseIotInfo(byteArrayOf(0x14, 0x6E, 0x01, 0x00, 1, 4, 2))
        assertFalse(unlocked!!.locked)
    }

    @Test
    fun asciiDeviceInfoIsDetectedByColon() {
        val text = "IMEI:860123456789012".toByteArray(Charsets.UTF_8)
        assertTrue(ScooterCodec.isAsciiDeviceInfo(text))
        assertFalse(ScooterCodec.isAsciiDeviceInfo(byteArrayOf(1, 2, 3)))
        assertEquals("IMEI:860123456789012", ScooterCodec.parseIotText(text).text)
    }

    @Test
    fun asciiDeviceInfoRequiresPrintablePayload() {
        assertFalse(ScooterCodec.isAsciiDeviceInfo(byteArrayOf(0x3A, 0xFF.toByte())))
        assertFalse(ScooterCodec.isAsciiDeviceInfo(byteArrayOf(0x41, 0x42)))
        assertTrue(ScooterCodec.isAsciiDeviceInfo("IMEI:1\u0000".toByteArray(Charsets.UTF_8)))
        assertEquals("IMEI:1", ScooterCodec.parseIotText("IMEI:1\u0000\u0000".toByteArray(Charsets.UTF_8)).text)
    }

    @Test
    fun iotInfoRejectsShortPayload() {
        assertEquals(null, ScooterCodec.parseIotInfo(byteArrayOf(1, 2, 3)))
        assertEquals(null, ScooterCodec.parseIotInfo(ByteArray(0)))
    }

    @Test
    fun realtimeDecodesCurrent() {
        val info = ScooterCodec.parseRealtime(byteArrayOf(88, 0x00, 0xF5.toByte(), 0x01, 0x2C, 0x03))
        assertNotNull(info)
        assertEquals(88, info!!.batteryPercent)
        assertEquals(24.5, info.speed, 0.0001)
        assertEquals(3.0, info.current, 0.0001)
        assertEquals(3, info.flags)
    }

    @Test
    fun rfidPairReadsBothCards() {
        val payload = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x0A, 0x0B, 0x0C, 0x0D)
        assertEquals(0x12345678L, ScooterCodec.parseRfid(payload))
        val pair = ScooterCodec.parseRfidPair(payload)
        assertNotNull(pair)
        assertEquals(0x12345678L, pair!!.first)
        assertEquals(0x0A0B0C0DL, pair.second)
    }

    @Test
    fun rfidRejectsShortPayloads() {
        assertEquals(null, ScooterCodec.parseRfid(byteArrayOf(1, 2, 3)))
        assertEquals(null, ScooterCodec.parseRfidPair(ByteArray(7)))
        assertEquals(0x00000001L, ScooterCodec.parseRfid(byteArrayOf(0, 0, 0, 1)))
    }
}
