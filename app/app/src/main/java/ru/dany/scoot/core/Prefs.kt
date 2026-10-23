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

    var autoUpdate: Boolean
        get() = sp.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) {
            sp.edit { putBoolean(KEY_AUTO_UPDATE, value) }
        }

    var lastUpdateCheck: Long
        get() = sp.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) {
            sp.edit { putLong(KEY_LAST_UPDATE_CHECK, value) }
        }

    companion object {
        const val DEFAULT_BLE_KEY = "4BKNwi77"
        const val DEFAULT_SPAM_INTERVAL = 100
        const val MIN_SPAM_INTERVAL = 20
        const val MAX_SPAM_INTERVAL = 10000
        const val DEFAULT_RECONNECT_ATTEMPTS = 3
        const val DEFAULT_RECONNECT_DELAY = 5000
        const val DEFAULT_THEME = "dark"

        private const val PREFS_NAME = "danyscoot_prefs"
        private const val LEGACY_PREFS_NAME = "danyurent_prefs"
        private const val KEY_BLE_KEY = "ble_key"
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
        private const val KEY_AUTO_UPDATE = "auto_update_enabled"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
