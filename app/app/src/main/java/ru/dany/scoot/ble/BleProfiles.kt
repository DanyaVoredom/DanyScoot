package ru.dany.scoot.ble

import java.util.UUID

object BleProfiles {

    val EDIFIER_SERVICE: UUID = DeviceClassifier.EDIFIER_SERVICE
    val EDIFIER_WRITE: UUID = UUID.fromString("00001101-d102-11e1-9b23-00025b00a5a5")
    val EDIFIER_NOTIFY: UUID = UUID.fromString("00001102-d102-11e1-9b23-00025b00a5a5")
    val EDIFIER_NOTIFY_ALT: UUID = UUID.fromString("00001103-d102-11e1-9b23-00025b00a5a5")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    val URENT = BleProfile(
        service = BleTransport.UART_SERVICE,
        write = BleTransport.UART_RX_CHAR,
        notify = BleTransport.UART_TX_CHAR,
        cccd = BleTransport.CCCD,
        mtu = BleTransport.REQUESTED_MTU,
    )

    val EDIFIER = BleProfile(
        service = EDIFIER_SERVICE,
        write = EDIFIER_WRITE,
        notify = EDIFIER_NOTIFY,
        cccd = BleTransport.CCCD,
        mtu = BleTransport.REQUESTED_MTU,
        writeWithoutResponse = true,
        extraNotify = listOf(EDIFIER_NOTIFY_ALT, BATTERY_LEVEL),
    )

    fun forKind(kind: DeviceKind): BleProfile = when (kind) {
        DeviceKind.HEADSET -> EDIFIER
        else -> URENT
    }
}
