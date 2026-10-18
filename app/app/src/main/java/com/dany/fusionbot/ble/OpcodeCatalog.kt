package com.dany.fusionbot.ble

enum class OpcodeDirection { BIDIRECTIONAL, SET_ONLY, NOTIFICATION }

data class OpcodeInfo(
    val code: Int,
    val name: String,
    val direction: OpcodeDirection,
    val description: String? = null,
)

object OpcodeCatalog {

    val all: List<OpcodeInfo> = listOf(
        OpcodeInfo(0x01, "COMMUNICATION_KEY", OpcodeDirection.BIDIRECTIONAL, "Handshake / session AES key exchange"),
        OpcodeInfo(0x05, "UNLOCK", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0x10, "ERROR", OpcodeDirection.NOTIFICATION, "Scooter rejected previous command"),
        OpcodeInfo(0x15, "LOCK", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0x31, "DEVICE_INFO", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0x34, "HELMET_UNLOCK / MQTT_ACCOUNT", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x35, "MQTT_PASSWORD", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x37, "INSTALLATION_MODE", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x3D, "AUTO_LOCK_MODE", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x40, "STEERING_MODE", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x50, "DISABLE_GSM_COMMUNICATION", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x51, "SCOOTER_OLD_DATA", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0x52, "CLEAR_DATA", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x53, "GSM_AUTO_DETECT", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x54, "CLEAR_BT_PAIRED_USERS", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x55, "GPS_TRACKING_SETTINGS", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x56, "METER_CONFIG", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x60, "SCOOTER_INFO", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0x61, "SCOOTER_SET", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x62, "SCOOTER_CONFIG", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x63, "VEHICLE_REALTIME_INFO", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0x64, "SET_WHEEL_DIAMETER", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x72, "CALIBRATE_INSTRUMENT_TIME", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x73, "METER_RESET", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x74, "NAVIGATION", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x75, "AUDIO_TOGGLE", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x76, "START_TESTING", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x77, "CLEAR_MILEAGE", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x78, "XM_FIND_CAR", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x79, "AUTOMATIC_SCREEN_OFF", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x81, "SCOOTER_BATTERY / OUT_LOCK", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0x82, "SCOOTER_COLOR_LIGHT", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x85, "READ_RFID_CARD", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0x86, "DELETE_RFID", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x90, "XM_POWER_CONTROL", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x91, "POWER_CONTROL", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x92, "IOT_RESTART", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x95, "CHAIN_LOCK_PASSWORD_ENTRY", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x96, "CHAIN_LOCK_PASSWORD_DELETE", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0x97, "CHAIN_LOCK_GET_USER_PASSWORD", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0xA0, "VEHICLE_COMMANDS", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0xA1, "QR_CODE_READ_WRITE", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0xA2, "FIND_CAR (sound)", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0xC0, "BATTERY_UNLOCK", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0xC3, "ACCESSORY_CONTROL", OpcodeDirection.SET_ONLY, "Saddle / tail-box / helmet open or close"),
        OpcodeInfo(0xC4, "UPDATE_CERTIFICATE", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0xC5, "SWITCH_SIM", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0xC6, "SWITCH_NEXT_SIM", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0xC7, "BATTERY/LIGHT or LOG_DATA", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0xDC, "AVAS_SWITCH", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0xDE, "AVAS_SWITCH_2", OpcodeDirection.SET_ONLY),
        OpcodeInfo(0xFA, "FW_DEVICE_INFO", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0xFB, "FW_DEVICE_DATA", OpcodeDirection.BIDIRECTIONAL),
        OpcodeInfo(0xFC, "FW_GET_FIRMWARE_DATA", OpcodeDirection.BIDIRECTIONAL),
    )

    private val byCode = all.associateBy { it.code }

    fun byCode(code: Int): OpcodeInfo? = byCode[code]

    fun nameOf(code: Int): String = byCode[code]?.name ?: "UNKNOWN(0x${hexByte(code)})"
}
