package ru.dany.scoot.ble

import java.util.Locale
import java.util.UUID
import kotlin.math.pow
import ru.dany.scoot.R

enum class DeviceKind {
    SCOOTER,
    HEADSET,
    OTHER;

    companion object {
        fun fromId(id: String?): DeviceKind =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: OTHER
    }
}

enum class DeviceSort(val id: String) {
    DISTANCE("distance"),
    NAME("name"),
    ADDRESS("address"),
    KIND("kind");

    companion object {
        fun fromId(id: String?): DeviceSort = entries.firstOrNull { it.id == id } ?: DISTANCE
    }
}

object DeviceClassifier {

    val EDIFIER_SERVICE: UUID = UUID.fromString("00001100-d102-11e1-9b23-00025b00a5a5")
    val FAST_PAIR_SERVICE: UUID = UUID.fromString("0000fe2c-0000-1000-8000-00805f9b34fb")
    val EDIFIER_EXT_SERVICE: UUID = UUID.fromString("48091601-1a48-11e9-ab14-d663bd873d93")

    private const val RSSI_AT_ONE_METER = -59.0
    private const val PATH_LOSS_EXPONENT = 2.0
    private const val MIN_DISTANCE = 0.2
    private const val MAX_DISTANCE = 120.0

    fun classify(name: String, uuids: Collection<UUID>?): DeviceKind {
        if (uuids != null && uuids.contains(BleTransport.UART_SERVICE)) return DeviceKind.SCOOTER
        if (uuids != null && uuids.contains(EDIFIER_SERVICE)) return DeviceKind.HEADSET
        if (name.startsWith("EDIFIER", ignoreCase = true)) return DeviceKind.HEADSET
        return DeviceKind.OTHER
    }

    fun classify(name: String, advert: String): DeviceKind {
        val lower = advert.lowercase(Locale.ROOT)
        if (lower.contains(BleTransport.UART_SERVICE.toString())) return DeviceKind.SCOOTER
        if (lower.contains(EDIFIER_SERVICE.toString()) || name.startsWith("EDIFIER", ignoreCase = true)) {
            return DeviceKind.HEADSET
        }
        return DeviceKind.OTHER
    }

    fun distanceMeters(rssi: Int): Double {
        if (rssi == 0) return MAX_DISTANCE
        val value = 10.0.pow((RSSI_AT_ONE_METER - rssi) / (10.0 * PATH_LOSS_EXPONENT))
        return value.coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    fun formatDistance(rssi: Int): String {
        val meters = distanceMeters(rssi)
        return if (meters < 10.0) {
            String.format(Locale.ROOT, "%.1f", meters)
        } else {
            String.format(Locale.ROOT, "%.0f", meters)
        }
    }

    fun labelRes(kind: DeviceKind): Int = when (kind) {
        DeviceKind.SCOOTER -> R.string.device_kind_scooter
        DeviceKind.HEADSET -> R.string.device_kind_headset
        DeviceKind.OTHER -> R.string.device_kind_ble
    }
}
