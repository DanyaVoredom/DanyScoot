package ru.dany.scoot.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhooshProtocolTest {

    private val tokenHex = "000102030405060708090A0B0C0D0E0F"

    @Test
    fun offlineStartAttemptOneMatchesReport() {
        val payload = WhooshProtocol.offlinePayload("01", 1, tokenHex)
        assertEquals("0100000001000102030405060708090A0B0C0D0E0F", hex(payload, ""))
    }

    @Test
    fun offlineStopAttemptTwoMatchesReport() {
        val payload = WhooshProtocol.offlinePayload("02", 2, tokenHex)
        assertEquals("0200000002000102030405060708090A0B0C0D0E0F", hex(payload, ""))
    }

    @Test
    fun offlineAttempt256MatchesReport() {
        val payload = WhooshProtocol.offlinePayload("01", 256, tokenHex)
        assertEquals("0100000100000102030405060708090A0B0C0D0E0F", hex(payload, ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun offlineAttemptZeroIsRejected() {
        WhooshProtocol.offlinePayload("01", 0, tokenHex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun offlineCommandIdMustBeOneByte() {
        WhooshProtocol.offlinePayload("1", 1, tokenHex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun offlineTokenMustHaveEvenLength() {
        WhooshProtocol.offlinePayload("01", 1, "ABC")
    }

    @Test
    fun tokenHexDecodesBase64() {
        assertEquals(tokenHex, WhooshProtocol.tokenHex("AAECAwQFBgcICQoLDA0ODw=="))
    }

    @Test
    fun tokenHexDecodesExplicitHex() {
        assertEquals("00FF10", WhooshProtocol.tokenHex("hex:00ff10"))
    }

    @Test
    fun tokenHexRejectsEmpty() {
        assertNull(WhooshProtocol.tokenHex(""))
    }

    @Test
    fun offlineResponseOk() {
        val response = WhooshProtocol.parseOfflineResponse(byteArrayOf(0xBB.toByte(), 0xBB.toByte(), 0xBB.toByte(), 0, 0, 0, 0, 0, 0))
        assertEquals(WhooshOfflineKind.OK, response.kind)
        assertEquals(0, response.status)
    }

    @Test
    fun offlineResponseSmsFallback() {
        val response = WhooshProtocol.parseOfflineResponse(byteArrayOf(0xBB.toByte(), 0xBB.toByte(), 0xBB.toByte(), 0, 0, 0, 0, 0, 10))
        assertEquals(WhooshOfflineKind.SMS, response.kind)
        assertEquals(10, response.status)
    }

    @Test
    fun offlineResponseStatus() {
        val response = WhooshProtocol.parseOfflineResponse(byteArrayOf(0xBB.toByte(), 0xBB.toByte(), 0xBB.toByte(), 0, 0, 0, 0, 0, 11))
        assertEquals(WhooshOfflineKind.STATUS, response.kind)
        assertEquals(11, response.status)
    }

    @Test
    fun offlineResponseUnknownStatus() {
        val response = WhooshProtocol.parseOfflineResponse(byteArrayOf(0xBB.toByte(), 0xBB.toByte(), 0xBB.toByte(), 0, 0, 0, 0, 0, 12))
        assertEquals(WhooshOfflineKind.UNKNOWN, response.kind)
        assertEquals(12, response.status)
    }

    @Test
    fun offlineResponseError() {
        val response = WhooshProtocol.parseOfflineResponse(byteArrayOf(0xEE.toByte(), 0xEE.toByte(), 0xEE.toByte()))
        assertEquals(WhooshOfflineKind.ERROR, response.kind)
    }

    @Test
    fun offlineResponseMalformed() {
        val response = WhooshProtocol.parseOfflineResponse(byteArrayOf(0xBB.toByte(), 0xBB.toByte(), 0xBB.toByte()))
        assertEquals(WhooshOfflineKind.MALFORMED, response.kind)
    }

    @Test
    fun onlineSuccess() {
        val response = WhooshProtocol.parseOnlineResponse("CMD_EXE_SUCCESS".toByteArray())
        assertEquals(WhooshOnlineKind.SUCCESS, response.kind)
    }

    @Test
    fun onlineError() {
        val response = WhooshProtocol.parseOnlineResponse("CMD_EXE_ERROR reason".toByteArray())
        assertEquals(WhooshOnlineKind.ERROR, response.kind)
        assertTrue(response.text.startsWith("CMD_EXE_ERROR"))
    }

    @Test
    fun onlineIgnored() {
        val response = WhooshProtocol.parseOnlineResponse("OK".toByteArray())
        assertEquals(WhooshOnlineKind.IGNORED, response.kind)
    }
}
