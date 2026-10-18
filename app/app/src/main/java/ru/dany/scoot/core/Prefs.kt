package ru.dany.scoot.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacy(context.applicationContext)
    }

    private fun migrateLegacy(context: Context) {
        if (sp.all.isNotEmpty()) return
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val values = legacy.all
        if (values.isEmpty()) return
        sp.edit {
            for ((key, value) in values) {
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }
        legacy.edit { clear() }
    }

    var bleKey: String
        get() = sp.getString(KEY_BLE_KEY, DEFAULT_BLE_KEY)?.takeIf { it.isNotEmpty() } ?: DEFAULT_BLE_KEY
        set(value) {
            val v = value.trim().ifEmpty { DEFAULT_BLE_KEY }
            sp.edit { putString(KEY_BLE_KEY, v) }
        }

    var provider: String
        get() = sp.getString(KEY_PROVIDER, Provider.URENT.key) ?: Provider.URENT.key
        set(value) {
            sp.edit { putString(KEY_PROVIDER, Provider.from(value).key) }
        }

    var whooshMode: String
        get() = sp.getString(KEY_WHOOSH_MODE, WhooshMode.OFFLINE.key) ?: WhooshMode.OFFLINE.key
        set(value) {
            sp.edit { putString(KEY_WHOOSH_MODE, WhooshMode.from(value).key) }
        }

    var whooshToken: String
        get() = sp.getString(KEY_WHOOSH_TOKEN, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_WHOOSH_TOKEN, value.trim()) }
        }

    var whooshStartId: String
        get() = sp.getString(KEY_WHOOSH_START_ID, DEFAULT_WHOOSH_START_ID) ?: DEFAULT_WHOOSH_START_ID
        set(value) {
            sp.edit { putString(KEY_WHOOSH_START_ID, value.trim().ifEmpty { DEFAULT_WHOOSH_START_ID }) }
        }

    var whooshStopId: String
        get() = sp.getString(KEY_WHOOSH_STOP_ID, DEFAULT_WHOOSH_STOP_ID) ?: DEFAULT_WHOOSH_STOP_ID
        set(value) {
            sp.edit { putString(KEY_WHOOSH_STOP_ID, value.trim().ifEmpty { DEFAULT_WHOOSH_STOP_ID }) }
        }

    var whooshUnlockHex: String
        get() = sp.getString(KEY_WHOOSH_UNLOCK_HEX, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_WHOOSH_UNLOCK_HEX, value.trim()) }
        }

    var whooshLockHex: String
        get() = sp.getString(KEY_WHOOSH_LOCK_HEX, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_WHOOSH_LOCK_HEX, value.trim()) }
        }

    var yandexServiceUuid: String
        get() = sp.getString(KEY_YANDEX_SERVICE_UUID, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_YANDEX_SERVICE_UUID, value.trim()) }
        }

    var yandexWriteUuid: String
        get() = sp.getString(KEY_YANDEX_WRITE_UUID, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_YANDEX_WRITE_UUID, value.trim()) }
        }

    var yandexNotifyUuid: String
        get() = sp.getString(KEY_YANDEX_NOTIFY_UUID, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_YANDEX_NOTIFY_UUID, value.trim()) }
        }

    var yandexCccdUuid: String
        get() = sp.getString(KEY_YANDEX_CCCD_UUID, DEFAULT_CCCD_UUID) ?: DEFAULT_CCCD_UUID
        set(value) {
            sp.edit { putString(KEY_YANDEX_CCCD_UUID, value.trim().ifEmpty { DEFAULT_CCCD_UUID }) }
        }

    var yandexDeviceId: String
        get() = sp.getString(KEY_YANDEX_DEVICE_ID, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_YANDEX_DEVICE_ID, value.trim()) }
        }

    var yandexNonce: String
        get() = sp.getString(KEY_YANDEX_NONCE, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_YANDEX_NONCE, value.trim()) }
        }

    var yandexKey: String
        get() = sp.getString(KEY_YANDEX_KEY, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_YANDEX_KEY, value.trim()) }
        }

    var spamIntervalMs: Int
        get() = sp.getInt(KEY_SPAM_INTERVAL, DEFAULT_SPAM_INTERVAL)
        set(value) {
            sp.edit { putInt(KEY_SPAM_INTERVAL, value.coerceIn(MIN_SPAM_INTERVAL, MAX_SPAM_INTERVAL)) }
        }

    var autoReconnect: Boolean
        get() = sp.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) {
            sp.edit { putBoolean(KEY_AUTO_RECONNECT, value) }
        }

    var autoReconnectAttempts: Int
        get() = sp.getInt(KEY_AUTO_RECONNECT_ATTEMPTS, DEFAULT_RECONNECT_ATTEMPTS)
        set(value) {
            sp.edit { putInt(KEY_AUTO_RECONNECT_ATTEMPTS, value.coerceIn(0, 20)) }
        }

    var autoReconnectDelayMs: Int
        get() = sp.getInt(KEY_AUTO_RECONNECT_DELAY, DEFAULT_RECONNECT_DELAY)
        set(value) {
            sp.edit { putInt(KEY_AUTO_RECONNECT_DELAY, value.coerceIn(500, 60000)) }
        }

    var autoResumeSpam: Boolean
        get() = sp.getBoolean(KEY_AUTO_RESUME_SPAM, false)
        set(value) {
            sp.edit { putBoolean(KEY_AUTO_RESUME_SPAM, value) }
        }

    var showAllDevices: Boolean
        get() = sp.getBoolean(KEY_SHOW_ALL_DEVICES, true)
        set(value) {
            sp.edit { putBoolean(KEY_SHOW_ALL_DEVICES, value) }
        }

    var showScanLog: Boolean
        get() = sp.getBoolean(KEY_SHOW_SCAN_LOG, false)
        set(value) {
            sp.edit { putBoolean(KEY_SHOW_SCAN_LOG, value) }
        }

    var demoMode: Boolean
        get() = sp.getBoolean(KEY_DEMO_MODE, false)
        set(value) {
            sp.edit { putBoolean(KEY_DEMO_MODE, value) }
        }

    var showAllOpcodes: Boolean
        get() = sp.getBoolean(KEY_SHOW_ALL_OPCODES, true)
        set(value) {
            sp.edit { putBoolean(KEY_SHOW_ALL_OPCODES, value) }
        }

    var theme: String
        get() = sp.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        set(value) {
            sp.edit { putString(KEY_THEME, value) }
        }

    var lastDeviceAddress: String
        get() = sp.getString(KEY_LAST_ADDRESS, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_LAST_ADDRESS, value) }
        }

    var lastDeviceName: String
        get() = sp.getString(KEY_LAST_NAME, "").orEmpty()
        set(value) {
            sp.edit { putString(KEY_LAST_NAME, value) }
        }

    companion object {
        const val DEFAULT_BLE_KEY = "4BKNwi77"
        const val DEFAULT_WHOOSH_START_ID = "01"
        const val DEFAULT_WHOOSH_STOP_ID = "02"
        const val DEFAULT_CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        const val DEFAULT_SPAM_INTERVAL = 100
        const val MIN_SPAM_INTERVAL = 20
        const val MAX_SPAM_INTERVAL = 10000
        const val DEFAULT_RECONNECT_ATTEMPTS = 3
        const val DEFAULT_RECONNECT_DELAY = 5000
        const val DEFAULT_THEME = "dark"

        private const val PREFS_NAME = "danyscoot_prefs"
        private const val LEGACY_PREFS_NAME = "danyurent_prefs"
        private const val KEY_BLE_KEY = "ble_key"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_WHOOSH_MODE = "whoosh_mode"
        private const val KEY_WHOOSH_TOKEN = "whoosh_token"
        private const val KEY_WHOOSH_START_ID = "whoosh_start_id"
        private const val KEY_WHOOSH_STOP_ID = "whoosh_stop_id"
        private const val KEY_WHOOSH_UNLOCK_HEX = "whoosh_unlock_hex"
        private const val KEY_WHOOSH_LOCK_HEX = "whoosh_lock_hex"
        private const val KEY_YANDEX_SERVICE_UUID = "yandex_service_uuid"
        private const val KEY_YANDEX_WRITE_UUID = "yandex_write_uuid"
        private const val KEY_YANDEX_NOTIFY_UUID = "yandex_notify_uuid"
        private const val KEY_YANDEX_CCCD_UUID = "yandex_cccd_uuid"
        private const val KEY_YANDEX_DEVICE_ID = "yandex_device_id"
        private const val KEY_YANDEX_NONCE = "yandex_nonce"
        private const val KEY_YANDEX_KEY = "yandex_key"
        private const val KEY_SPAM_INTERVAL = "unlock_spam_interval_ms"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect_enabled"
        private const val KEY_AUTO_RECONNECT_ATTEMPTS = "auto_reconnect_max_attempts"
        private const val KEY_AUTO_RECONNECT_DELAY = "auto_reconnect_delay_ms"
        private const val KEY_AUTO_RESUME_SPAM = "auto_resume_spam"
        private const val KEY_SHOW_ALL_DEVICES = "show_all_scan_devices"
        private const val KEY_SHOW_SCAN_LOG = "show_scan_debug_log"
        private const val KEY_DEMO_MODE = "use_demo"
        private const val KEY_SHOW_ALL_OPCODES = "show_all_opcodes"
        private const val KEY_THEME = "theme"
        private const val KEY_LAST_ADDRESS = "last_device_address"
        private const val KEY_LAST_NAME = "last_device_name"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
