package ru.dany.scoot.ble

object BleProfiles {

    val URENT = BleProfile(
        service = BleTransport.UART_SERVICE,
        write = BleTransport.UART_RX_CHAR,
        notify = BleTransport.UART_TX_CHAR,
        cccd = BleTransport.CCCD,
        mtu = BleTransport.REQUESTED_MTU,
    )
}
