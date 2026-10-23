package ru.dany.scoot.ble

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

data class GattCharacteristicInfo(
    val uuid: UUID,
    val properties: Int,
) {
    val writable: Boolean
        get() = properties and (
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            ) != 0

    val notifiable: Boolean
        get() = properties and (
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE
            ) != 0

    fun propertyNames(): List<String> {
        val names = ArrayList<String>()
        if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) names.add("broadcast")
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) names.add("read")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) names.add("write-nr")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) names.add("write")
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) names.add("notify")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) names.add("indicate")
        if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) names.add("signed")
        if (properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) names.add("extended")
        return names
    }

    fun describe(): String = propertyNames().joinToString(", ").ifEmpty { "-" }
}

data class GattServiceInfo(
    val uuid: UUID,
    val characteristics: List<GattCharacteristicInfo>,
) {
    val name: String? get() = gattName(uuid)
}

data class GattDump(
    val address: String,
    val name: String,
    val services: List<GattServiceInfo>,
) {
    fun summary(): String {
        if (services.isEmpty()) return "сервисы отсутствуют"
        return services.joinToString(" | ") { service ->
            val label = service.name?.let { "($it)" }.orEmpty()
            val chars = service.characteristics.joinToString(", ") { char ->
                shortUuid(char.uuid) + "[" + char.describe() + "]"
            }
            shortUuid(service.uuid) + label + "{" + chars + "}"
        }
    }

    fun toText(): String {
        val text = StringBuilder()
        text.append("DanyScoot GATT\n")
        text.append("device: ").append(name.ifEmpty { "Unknown" }).append(" (").append(address).append(")\n")
        text.append("services: ").append(services.size).append('\n')
        for (service in services) {
            text.append("service ").append(service.uuid)
            service.name?.let { text.append(" ").append(it) }
            text.append('\n')
            if (service.characteristics.isEmpty()) text.append("  characteristics: none\n")
            for (char in service.characteristics) {
                text.append("  char ").append(char.uuid).append(' ').append(char.describe()).append('\n')
            }
        }
        return text.toString()
    }
}

private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

fun shortUuid(uuid: UUID): String {
    val text = uuid.toString()
    if (text.length == 36 && text.startsWith("0000") && text.endsWith(BASE_SUFFIX)) {
        return "0x" + text.substring(4, 8).uppercase()
    }
    return text
}

fun gattName(uuid: UUID): String? {
    val text = uuid.toString()
    return when (text) {
        "6e400001-b5a3-f393-e0a9-e50e24dcca9e" -> "Nordic UART"
        "6e400002-b5a3-f393-e0a9-e50e24dcca9e" -> "UART RX"
        "6e400003-b5a3-f393-e0a9-e50e24dcca9e" -> "UART TX"
        "00002902-0000-1000-8000-00805f9b34fb" -> "CCCD"
        "00001800-0000-1000-8000-00805f9b34fb" -> "Generic Access"
        "00001801-0000-1000-8000-00805f9b34fb" -> "Generic Attribute"
        "00001802-0000-1000-8000-00805f9b34fb" -> "Immediate Alert"
        "00001803-0000-1000-8000-00805f9b34fb" -> "Link Loss"
        "00001804-0000-1000-8000-00805f9b34fb" -> "Tx Power"
        "00001805-0000-1000-8000-00805f9b34fb" -> "Current Time"
        "0000180a-0000-1000-8000-00805f9b34fb" -> "Device Information"
        "0000180f-0000-1000-8000-00805f9b34fb" -> "Battery"
        "00001812-0000-1000-8000-00805f9b34fb" -> "HID"
        "0000181c-0000-1000-8000-00805f9b34fb" -> "User Data"
        "0000fe2c-0000-1000-8000-00805f9b34fb" -> "Fast Pair"
        "00001100-d102-11e1-9b23-00025b00a5a5" -> "Edifier"
        "00001101-d102-11e1-9b23-00025b00a5a5" -> "Edifier RX"
        "00001102-d102-11e1-9b23-00025b00a5a5" -> "Edifier TX"
        "00001103-d102-11e1-9b23-00025b00a5a5" -> "Edifier Event"
        "48091601-1a48-11e9-ab14-d663bd873d93" -> "Edifier Extended"
        "48090001-1a48-11e9-ab14-d663bd873d93" -> "Edifier State"
        "48090002-1a48-11e9-ab14-d663bd873d93" -> "Edifier Control"
        "00002a00-0000-1000-8000-00805f9b34fb" -> "Device Name"
        "00002a01-0000-1000-8000-00805f9b34fb" -> "Appearance"
        "00002a05-0000-1000-8000-00805f9b34fb" -> "Service Changed"
        "00002a06-0000-1000-8000-00805f9b34fb" -> "Alert Level"
        "00002a07-0000-1000-8000-00805f9b34fb" -> "Tx Power Level"
        "00002a19-0000-1000-8000-00805f9b34fb" -> "Battery Level"
        "00002a24-0000-1000-8000-00805f9b34fb" -> "Model Number"
        "00002a25-0000-1000-8000-00805f9b34fb" -> "Serial Number"
        "00002a26-0000-1000-8000-00805f9b34fb" -> "Firmware Revision"
        "00002a27-0000-1000-8000-00805f9b34fb" -> "Hardware Revision"
        "00002a28-0000-1000-8000-00805f9b34fb" -> "Software Revision"
        "00002a29-0000-1000-8000-00805f9b34fb" -> "Manufacturer"
        "00002b29-0000-1000-8000-00805f9b34fb" -> "Client Features"
        else -> null
    }
}

fun gattStatusText(status: Int): String = when (status) {
    0 -> "успех"
    1 -> "неверный handle"
    2 -> "чтение запрещено"
    3 -> "запись запрещена"
    4 -> "неверный PDU"
    5 -> "нужна аутентификация"
    6 -> "запрос не поддержан"
    7 -> "неверный offset"
    8 -> "недостаточно прав"
    9 -> "очередь подготовки переполнена"
    10 -> "атрибут не найден"
    11 -> "атрибут не длинный"
    12 -> "недостаточный размер ключа"
    13 -> "неверная длина атрибута"
    14 -> "непредвиденная ошибка"
    15 -> "нужно шифрование"
    16 -> "неподдерживаемый тип группы"
    17 -> "нет ресурсов"
    19 -> "устройство разорвало соединение"
    22 -> "локальный хост разорвал соединение"
    34 -> "LMP timeout"
    62 -> "сбой установки соединения"
    133 -> "общий сбой GATT"
    137 -> "превышен таймаут соединения"
    143 -> "перегрузка соединения"
    147 -> "сбой соединения"
    257 -> "общий сбой"
    else -> "код $status"
}
