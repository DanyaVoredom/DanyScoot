package ru.dany.scoot.ble

import java.util.UUID

object BleProfiles {

    val URENT = BleProfile(
        service = BleTransport.UART_SERVICE,
        write = BleTransport.UART_RX_CHAR,
        notify = BleTransport.UART_TX_CHAR,
        cccd = BleTransport.CCCD,
        mtu = BleTransport.REQUESTED_MTU,
        scanService = BleTransport.UART_SERVICE,
    )

    val WHOOSH_OFFLINE = BleProfile(
        service = WhooshProtocol.OFFLINE_SERVICE,
        write = WhooshProtocol.OFFLINE_WRITE,
        notify = WhooshProtocol.OFFLINE_NOTIFY,
        cccd = BleTransport.CCCD,
        mtu = WhooshProtocol.OFFLINE_MTU,
        scanService = WhooshProtocol.OFFLINE_SERVICE,
    )

    val WHOOSH_ONLINE = BleProfile(
        service = WhooshProtocol.ONLINE_SERVICE,
        write = WhooshProtocol.ONLINE_WRITE,
        notify = WhooshProtocol.ONLINE_NOTIFY,
        cccd = BleTransport.CCCD,
        mtu = BleTransport.REQUESTED_MTU,
        scanService = null,
    )

    fun yandex(service: String, write: String, notify: String, cccd: String): BleProfile? {
        val s = parseUuid(service) ?: return null
        val w = parseUuid(write) ?: return null
        val n = parseUuid(notify) ?: return null
        val c = parseUuid(cccd) ?: BleTransport.CCCD
        return BleProfile(s, w, n, c, BleTransport.REQUESTED_MTU, null)
    }

    private fun parseUuid(value: String): UUID? {
        val text = value.trim()
        if (text.isEmpty()) return null
        return try {
            UUID.fromString(text)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
