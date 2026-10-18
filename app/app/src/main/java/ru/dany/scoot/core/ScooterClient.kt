package ru.dany.scoot.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Handler
import android.os.Looper
import ru.dany.scoot.ble.BleProfiles
import ru.dany.scoot.ble.BleTransport
import ru.dany.scoot.ble.FrameCodec
import ru.dany.scoot.ble.IotInfo
import ru.dany.scoot.ble.ScannedDevice
import ru.dany.scoot.ble.ScooterCodec
import ru.dany.scoot.ble.ScooterFrame
import ru.dany.scoot.ble.VehicleInfo
import ru.dany.scoot.ble.WhooshOfflineKind
import ru.dany.scoot.ble.WhooshOnlineKind
import ru.dany.scoot.ble.WhooshProtocol
import ru.dany.scoot.ble.YandexProtocol
import ru.dany.scoot.ble.hex
import ru.dany.scoot.ble.hexByte
import ru.dany.scoot.ble.parseHex
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClientState { IDLE, SCANNING, CONNECTING, DISCOVERING, AUTHENTICATING, READY, DEMO, ERROR }

object ScooterClient : BleTransport.Listener {

    const val DEMO_ADDRESS = "DEMO:00:00:00:00:01"
    const val DEMO_NAME = "Демо-самокат"

    private const val TAG = "BLE"
    private const val AUTH_TAG = "AUTH"
    private const val SPAM_TAG = "SPAM"
    private const val DEMO_TAG = "DEMO"
    private const val AUTH_TIMEOUT_MS = 3000L
    private const val UNLOCK_TIMEOUT_MS = 3000L
    private const val DEMO_TICK_MS = 1500L
    private const val WHOOSH_TAG = "WHOOSH"
    private const val YANDEX_TAG = "YANDEX"
    private const val PROVIDER_TAG = "PROVIDER"
    private const val BYPASS_TAG = "BYPASS"
    private const val YANDEX_TIMEOUT_MS = 5000L
    private const val WHOOSH_KEY_TIMEOUT_MS = 3000L

    private enum class YandexPending {
        AUTH,
        LOCK1,
        LOCK2,
        UNLOCK1,
        UNLOCK2,
        SOUND,
        CABLE,
        BATTERY,
    }

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
    private var demoTick: Runnable? = null
    private var demoLocked = false
    private var provider = Provider.URENT
    private var whooshMode = WhooshMode.OFFLINE
    private var whooshAttempt = 0
    private var whooshRetry: Runnable? = null
    private var whooshDeadline = 0L
    private var whooshKey = ""
    private var whooshKeyTimeout: Runnable? = null
    private var yandexSession = 0
    private var yandexPending: YandexPending? = null
    private var yandexTimeout: Runnable? = null

    private val _state = MutableStateFlow(ClientState.IDLE)
    val state: StateFlow<ClientState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()

    private val _vehicle = MutableStateFlow<VehicleInfo?>(null)
    val vehicle: StateFlow<VehicleInfo?> = _vehicle.asStateFlow()

    private val _iot = MutableStateFlow<IotInfo?>(null)
    val iot: StateFlow<IotInfo?> = _iot.asStateFlow()

    private val _spamActive = MutableStateFlow(false)
    val spamActive: StateFlow<Boolean> = _spamActive.asStateFlow()

    private val _spamSent = MutableStateFlow(0)
    val spamSent: StateFlow<Int> = _spamSent.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    var sessionKey: Int = 0
        private set

    var deviceName: String = ""
        private set

    var deviceAddress: String = ""
        private set

    val isDemo: Boolean get() = _state.value == ClientState.DEMO

    val spamWanted: Boolean get() = wantedSpam

    val isReady: Boolean
        get() = when (_state.value) {
            ClientState.DEMO -> true
            ClientState.READY -> authenticated
            else -> false
        }

    val hasLink: Boolean get() = isDemo || isConnected()

    fun init(context: Context) {
        if (transport == null) {
            prefs = Prefs.get(context)
            transport = BleTransport(context.applicationContext).also { it.listener = this }
            provider = Provider.from(prefs.provider)
            whooshMode = WhooshMode.from(prefs.whooshMode)
            applyProfile()
        }
    }

    val currentProvider: Provider get() = provider

    val currentWhooshMode: WhooshMode get() = whooshMode

    val whooshKeyHex: String get() = whooshKey

    fun setProvider(value: Provider) {
        if (provider == value) return
        disconnect()
        provider = value
        prefs.provider = value.key
        whooshMode = WhooshMode.from(prefs.whooshMode)
        applyProfile()
        LogBus.info(PROVIDER_TAG, "Оператор: ${value.title}")
    }

    fun setWhooshMode(value: WhooshMode) {
        if (whooshMode == value) return
        whooshMode = value
        prefs.whooshMode = value.key
        if (provider == Provider.WHOOSH) {
            disconnect()
            applyProfile()
        }
        LogBus.info(WHOOSH_TAG, "Режим: ${value.title}")
    }

    fun refreshProfile() {
        if (_state.value == ClientState.IDLE || _state.value == ClientState.ERROR) {
            applyProfile()
        }
    }

    private fun applyProfile(): Boolean {
        val t = transport ?: return false
        val profile = when (provider) {
            Provider.URENT -> BleProfiles.URENT
            Provider.WHOOSH -> if (whooshMode == WhooshMode.OFFLINE) {
                BleProfiles.WHOOSH_OFFLINE
            } else {
                BleProfiles.WHOOSH_ONLINE
            }
            Provider.YANDEX -> BleProfiles.yandex(
                prefs.yandexServiceUuid,
                prefs.yandexWriteUuid,
                prefs.yandexNotifyUuid,
                prefs.yandexCccdUuid,
            )
        }
        if (profile == null) {
            LogBus.err(PROVIDER_TAG, "Yandex: заполните service/write/notify UUID")
            return false
        }
        t.profile = profile
        return true
    }

    fun bluetoothSupported(): Boolean = transport?.isSupported == true

    fun bluetoothEnabled(): Boolean = transport?.isBluetoothEnabled == true

    fun startScan() {
        if (prefs.demoMode) {
            LogBus.info(DEMO_TAG, "Режим демо: сканирование отключено")
            return
        }
        if (!applyProfile()) {
            _state.value = ClientState.IDLE
            return
        }
        cancelReconnect()
        _devices.value = emptyList()
        _state.value = ClientState.SCANNING
        LogBus.info(TAG, "Сканирование запущено (${provider.title})")
        val t = transport
        if (t == null) {
            _state.value = ClientState.IDLE
            LogBus.err(TAG, "Bluetooth не инициализирован")
            return
        }
        val filter = if (provider == Provider.URENT && !prefs.showAllDevices) {
            BleTransport.UART_SERVICE
        } else {
            null
        }
        t.startScan(filter)
    }

    fun stopScan() {
        transport?.stopScan()
        if (_state.value == ClientState.SCANNING) {
            _state.value = ClientState.IDLE
        }
    }

    fun connect(address: String, name: String) {
        if (!applyProfile()) {
            _state.value = ClientState.IDLE
            return
        }
        cancelReconnect()
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
        cancelWhoosh()
        cancelYandex()
        sessionKey = 0
        yandexSession = 0
        whooshKey = ""
        _lastError.value = ""
        stopSpam()
        if (prefs.demoMode && provider != Provider.URENT) prefs.demoMode = false
        if (prefs.demoMode && address == DEMO_ADDRESS) {
            transport?.disconnect()
            deviceAddress = DEMO_ADDRESS
            deviceName = name.ifEmpty { DEMO_NAME }
            reconnectAttempts = 0
            startDemo()
            return
        }
        if (prefs.demoMode) prefs.demoMode = false
        stopDemo()
        val t = transport
        if (t == null) {
            _state.value = ClientState.IDLE
            LogBus.err(TAG, "Bluetooth не инициализирован")
            return
        }
        deviceAddress = address
        deviceName = name.ifEmpty { address }
        prefs.lastDeviceAddress = address
        prefs.lastDeviceName = deviceName
        reconnectAttempts = 0
        _state.value = ClientState.CONNECTING
        LogBus.info(TAG, "Подключение к $deviceName ($address)")
        t.connect(address)
    }

    fun disconnect() {
        cancelReconnect()
        linkLost = true
        stopSpam()
        wantedSpam = false
        reconnectAttempts = prefs.autoReconnectAttempts + 1
        if (isDemo) {
            stopDemo()
        } else {
            transport?.disconnect()
        }
        sessionKey = 0
        yandexSession = 0
        whooshKey = ""
        authPending = false
        authenticated = false
        authKey = ""
        authTried.clear()
        authTimeout?.let { handler.removeCallbacks(it) }
        authTimeout = null
        cancelUnlock()
        stopKeyBrute()
        bypassPending = false
        cancelWhoosh()
        cancelYandex()
        rxBuffer = ByteArray(0)
        _state.value = ClientState.IDLE
        LogBus.info(TAG, "Отключено")
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    fun reconnectLast(): Boolean {
        if (prefs.demoMode) {
            connect(DEMO_ADDRESS, DEMO_NAME)
            return true
        }
        val address = prefs.lastDeviceAddress
        if (address.isEmpty()) return false
        connect(address, prefs.lastDeviceName)
        return true
    }

    fun hasLastDevice(): Boolean {
        if (prefs.demoMode) return true
        val address = prefs.lastDeviceAddress
        return address.isNotEmpty()
    }

    fun authenticate() {
        if (provider != Provider.URENT) {
            when (provider) {
                Provider.WHOOSH -> {
                    if (whooshMode == WhooshMode.ONLINE) readWhooshKey() else markReady("Whoosh offline готов")
                }
                Provider.YANDEX -> yandexAuth()
                else -> Unit
            }
            return
        }
        if (isDemo) {
            sessionKey = 0x42
            authenticated = true
            _state.value = ClientState.DEMO
            LogBus.ok(AUTH_TAG, "Ключ верный (демо)")
            onAuthenticated()
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
        LogBus.info(AUTH_TAG, "Отправка ключа: $key")
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
        LogBus.warn(AUTH_TAG, "$reason, пробую ключ $next")
        sendAuthKey(next)
    }

    fun unlock() {
        when (provider) {
            Provider.URENT -> {
                val key = requireKey() ?: return
                cancelUnlock()
                unlockKey = key
                unlockVariant = 0
                sendUnlockVariant()
            }
            Provider.WHOOSH -> {
                if (whooshMode == WhooshMode.OFFLINE) {
                    whooshStart()
                } else {
                    whooshOnlineSend(prefs.whooshUnlockHex, "WHOOSH unlock")
                }
            }
            Provider.YANDEX -> yandexUnlock()
        }
    }

    fun lock() {
        when (provider) {
            Provider.URENT -> {
                val key = requireKey() ?: return
                cancelUnlock()
                writeCommand(ScooterCodec.lockCommand(key), "блокировка")
            }
            Provider.WHOOSH -> {
                if (whooshMode == WhooshMode.OFFLINE) {
                    whooshStop()
                } else {
                    whooshOnlineSend(prefs.whooshLockHex, "WHOOSH lock")
                }
            }
            Provider.YANDEX -> yandexLock()
        }
    }

    fun ejectBattery() {
        when (provider) {
            Provider.URENT -> {
                val key = requireKey() ?: return
                writeCommand(ScooterCodec.ejectBatteryCommand(key), "изъятие батареи")
            }
            Provider.YANDEX -> yandexBatteryCover()
            else -> LogBus.warn(TAG, "Команда не поддерживается для ${provider.title}")
        }
    }

    fun findCar() {
        when (provider) {
            Provider.URENT -> {
                val key = requireKey() ?: return
                writeCommand(ScooterCodec.findCarCommand(key), "поиск самоката")
            }
            Provider.YANDEX -> yandexSound()
            else -> LogBus.warn(TAG, "Команда не поддерживается для ${provider.title}")
        }
    }

    fun setScooter(headlight: Boolean, modeIndex: Int, throttle: Boolean) {
        if (provider != Provider.URENT) {
            LogBus.warn(TAG, "Команда не поддерживается для ${provider.title}")
            return
        }
        val key = requireKey() ?: return
        writeCommand(ScooterCodec.setScooterFromUi(key, headlight, modeIndex, throttle), "настройки самоката")
    }

    fun sendRaw(cmd: Int, payload: ByteArray) {
        if (provider != Provider.URENT) {
            LogBus.warn(TAG, "Используйте вкладку Операторы для ${provider.title}")
            return
        }
        if (!isReady) {
            LogBus.warn(TAG, "Требуется авторизация")
            return
        }
        writeCommand(FrameCodec.build(cmd, sessionKey, payload), "ручная команда ${hexByte(cmd)}")
    }

    fun sendService(cmd: Int, payload: ByteArray) {
        val key = requireKey() ?: return
        writeCommand(FrameCodec.build(cmd, key, payload), "сервис ${hexByte(cmd)}")
    }

    fun bypassUnlock() {
        if (provider != Provider.URENT) {
            LogBus.warn(TAG, "Обход доступен только для Urent")
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
        if (provider != Provider.URENT) {
            LogBus.warn(TAG, "Перебор доступен только для Urent")
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
                handler.postDelayed(this, 60)
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
        _state.value = ClientState.READY
        LogBus.ok(BYPASS_TAG, message)
        onAuthenticated()
    }

    fun startSpam() {
        if (provider != Provider.URENT) {
            LogBus.warn(SPAM_TAG, "Спам доступен только для Urent")
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
        if (isDemo) {
            _spamSent.value += 1
            return
        }
        if (!isReady) return
        val frame = ScooterCodec.spamUnlockCommand(sessionKey, System.currentTimeMillis())
        if (writeFrame(frame, null)) {
            _spamSent.value += 1
        }
    }

    private fun requireKey(): Int? {
        if (isDemo) return 0x42
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

    private fun writeCommand(frame: ByteArray, label: String): Boolean {
        if (isDemo) {
            LogBus.tx("TX", "${label}: ${hex(frame)}")
            demoReply(frame)
            return true
        }
        return writeFrame(frame, label)
    }

    private fun sendUnlockVariant() {
        if (!isDemo && !isReady) {
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
        if (isDemo) return
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
            list[existing] = device.copy(name = if (device.name == "Unknown") old.name else device.name)
        } else {
            if (provider == Provider.URENT && !prefs.showAllDevices && !device.hasUart) return
            list.add(device)
            if (prefs.showScanLog) {
                LogBus.info(
                    "SCAN",
                    "${device.name} ${device.address} RSSI=${device.rssi} UART=${device.hasUart} PROFILE=${device.hasProfile}",
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
            LogBus.info(TAG, "Сервисы готовы, канал уведомлений включен")
            when (provider) {
                Provider.URENT -> authenticate()
                Provider.WHOOSH -> {
                    if (whooshMode == WhooshMode.ONLINE) {
                        readWhooshKey()
                    } else {
                        markReady("Whoosh offline готов")
                    }
                }
                Provider.YANDEX -> {
                    if (prefs.yandexKey.isBlank() || prefs.yandexDeviceId.isBlank()) {
                        authenticated = false
                        _state.value = ClientState.READY
                        LogBus.warn(YANDEX_TAG, "Заполните AUTH key и device id, затем нажмите AUTH")
                    } else {
                        yandexAuth()
                    }
                }
            }
        }
    }

    override fun onCharacteristicRead(uuid: UUID, value: ByteArray) {
        handler.post { handleCharacteristicRead(uuid, value) }
    }

    private fun handleCharacteristicRead(uuid: UUID, value: ByteArray) {
        if (provider == Provider.WHOOSH && uuid == WhooshProtocol.ONLINE_KEY) {
            whooshKey = value.joinToString("") { hexByte(it.toInt()) }
            whooshKeyTimeout?.let { handler.removeCallbacks(it) }
            whooshKeyTimeout = null
            markReady("Whoosh online: ключ $whooshKey")
            return
        }
        if (provider == Provider.WHOOSH && uuid == WhooshProtocol.ONLINE_DIGITAL) {
            LogBus.info(WHOOSH_TAG, "digital_output: ${hex(value)}")
            return
        }
        LogBus.info(TAG, "Read $uuid: ${hex(value)}")
    }

    override fun onFrame(data: ByteArray) {
        handler.post { processFrame(data) }
    }

    private fun processFrame(data: ByteArray) {
        if (provider != Provider.URENT) {
            handleProviderFrame(data)
            return
        }
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
        cancelWhoosh()
        cancelYandex()
        yandexSession = 0
        whooshKey = ""
        rxBuffer = ByteArray(0)
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
        val canReconnect = prefs.autoReconnect && reconnectAttempts < prefs.autoReconnectAttempts &&
            deviceAddress.isNotEmpty() && !prefs.demoMode
        if (canReconnect) {
            reconnectAttempts += 1
            LogBus.info(TAG, "Переподключение $reconnectAttempts/${prefs.autoReconnectAttempts} через ${prefs.autoReconnectDelayMs} мс")
            val runnable = Runnable {
                reconnectRunnable = null
                if (!prefs.demoMode && deviceAddress.isNotEmpty()) {
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
    }

    private fun markReady(message: String) {
        authenticated = true
        reconnectAttempts = 0
        _state.value = ClientState.READY
        LogBus.ok(TAG, message)
    }

    private fun handleProviderFrame(data: ByteArray) {
        when (provider) {
            Provider.WHOOSH -> handleWhooshFrame(data)
            Provider.YANDEX -> handleYandexFrame(data)
            else -> Unit
        }
    }

    private fun handleWhooshFrame(data: ByteArray) {
        if (whooshMode == WhooshMode.OFFLINE) {
            LogBus.rx(WHOOSH_TAG, hex(data))
            val response = WhooshProtocol.parseOfflineResponse(data)
            when (response.kind) {
                WhooshOfflineKind.OK -> {
                    cancelWhoosh()
                    LogBus.ok(WHOOSH_TAG, "Команда выполнена")
                    _state.value = ClientState.READY
                }
                WhooshOfflineKind.SMS -> {
                    cancelWhoosh()
                    LogBus.warn(WHOOSH_TAG, "Самокат online, SMS на ${WhooshProtocol.SMS_PHONE}")
                    _state.value = ClientState.READY
                }
                WhooshOfflineKind.STATUS -> {
                    cancelWhoosh()
                    val code = response.status ?: -1
                    LogBus.err(WHOOSH_TAG, "${WhooshProtocol.statusName(code)} ($code)")
                    _state.value = ClientState.READY
                }
                WhooshOfflineKind.ERROR -> {
                    cancelWhoosh()
                    LogBus.err(WHOOSH_TAG, "EEEEEE: ошибка самоката")
                    _state.value = ClientState.READY
                }
                WhooshOfflineKind.MALFORMED -> LogBus.warn(WHOOSH_TAG, "BBBBBB без статуса: ${response.raw}")
                WhooshOfflineKind.UNKNOWN -> LogBus.info(WHOOSH_TAG, "Неизвестный ответ: ${response.raw}")
            }
            return
        }
        val response = WhooshProtocol.parseOnlineResponse(data)
        LogBus.rx(WHOOSH_TAG, response.text)
        when (response.kind) {
            WhooshOnlineKind.SUCCESS -> {
                cancelWhoosh()
                LogBus.ok(WHOOSH_TAG, "Команда выполнена")
                _state.value = ClientState.READY
            }
            WhooshOnlineKind.ERROR -> {
                cancelWhoosh()
                LogBus.err(WHOOSH_TAG, "CMD_EXE_ERROR: ${response.text}")
            }
            WhooshOnlineKind.IGNORED -> LogBus.info(WHOOSH_TAG, "Ответ: ${response.text}")
        }
    }

    fun whooshStart() {
        if (provider != Provider.WHOOSH || whooshMode != WhooshMode.OFFLINE) {
            LogBus.warn(WHOOSH_TAG, "START доступен в offline режиме")
            return
        }
        if (!isConnected()) {
            LogBus.warn(WHOOSH_TAG, "Нет подключения")
            return
        }
        sendWhooshOffline(prefs.whooshStartId, "START")
    }

    fun whooshStop() {
        if (provider != Provider.WHOOSH || whooshMode != WhooshMode.OFFLINE) {
            LogBus.warn(WHOOSH_TAG, "STOP доступен в offline режиме")
            return
        }
        if (!isConnected()) {
            LogBus.warn(WHOOSH_TAG, "Нет подключения")
            return
        }
        sendWhooshOffline(prefs.whooshStopId, "STOP")
    }

    private fun sendWhooshOffline(commandIdHex: String, label: String) {
        val tokenHex = WhooshProtocol.tokenHex(prefs.whooshToken)
        if (tokenHex == null) {
            LogBus.err(WHOOSH_TAG, "Укажите binaryToken (Base64 или hex:)")
            return
        }
        cancelWhoosh()
        whooshAttempt = 0
        whooshDeadline = System.currentTimeMillis() + WhooshProtocol.OFFLINE_TIMEOUT_MS
        sendWhooshAttempt(commandIdHex, tokenHex, label)
    }

    private fun sendWhooshAttempt(commandIdHex: String, tokenHex: String, label: String) {
        if (System.currentTimeMillis() > whooshDeadline) {
            cancelWhoosh()
            LogBus.err(WHOOSH_TAG, "Таймаут ${WhooshProtocol.OFFLINE_TIMEOUT_MS / 1000} с")
            return
        }
        whooshAttempt += 1
        val payload = try {
            WhooshProtocol.offlinePayload(commandIdHex, whooshAttempt, tokenHex)
        } catch (e: IllegalArgumentException) {
            cancelWhoosh()
            LogBus.err(WHOOSH_TAG, e.message ?: "Ошибка payload")
            return
        }
        if (!writeCommand(payload, "$label ${whooshAttempt}")) {
            cancelWhoosh()
            return
        }
        whooshRetry?.let { handler.removeCallbacks(it) }
        val next = Runnable { sendWhooshAttempt(commandIdHex, tokenHex, label) }
        whooshRetry = next
        handler.postDelayed(next, WhooshProtocol.OFFLINE_RETRY_MS)
    }

    fun whooshOnlineSend(commandHex: String, label: String) {
        if (provider != Provider.WHOOSH || whooshMode != WhooshMode.ONLINE) {
            LogBus.warn(WHOOSH_TAG, "Команда доступна в online режиме")
            return
        }
        if (!isConnected()) {
            LogBus.warn(WHOOSH_TAG, "Нет подключения")
            return
        }
        if (commandHex.isBlank()) {
            LogBus.warn(WHOOSH_TAG, "Укажите hex-команду backend")
            return
        }
        val bytes = WhooshProtocol.decodeHex(commandHex)
        if (bytes == null) {
            LogBus.err(WHOOSH_TAG, "Неверный hex")
            return
        }
        cancelWhoosh()
        whooshAttempt = 0
        whooshDeadline = System.currentTimeMillis() + WhooshProtocol.ONLINE_TIMEOUT_MS
        sendWhooshOnlineAttempt(bytes, label)
    }

    private fun sendWhooshOnlineAttempt(bytes: ByteArray, label: String) {
        if (System.currentTimeMillis() > whooshDeadline || whooshAttempt >= 10) {
            cancelWhoosh()
            LogBus.err(WHOOSH_TAG, "Таймаут ${WhooshProtocol.ONLINE_TIMEOUT_MS / 1000} с")
            return
        }
        whooshAttempt += 1
        if (!writeCommand(bytes, "$label ${whooshAttempt}")) {
            cancelWhoosh()
            return
        }
        whooshRetry?.let { handler.removeCallbacks(it) }
        val next = Runnable { sendWhooshOnlineAttempt(bytes, label) }
        whooshRetry = next
        handler.postDelayed(next, WhooshProtocol.ONLINE_RETRY_MS)
    }

    private fun readWhooshKey() {
        if (provider != Provider.WHOOSH || whooshMode != WhooshMode.ONLINE) return
        if (!isConnected()) return
        whooshKeyTimeout?.let { handler.removeCallbacks(it) }
        whooshKeyTimeout = null
        val t = transport ?: return
        if (!t.read(WhooshProtocol.ONLINE_KEY)) {
            markReady("Whoosh online: ключ не прочитан")
            return
        }
        val timeout = Runnable {
            whooshKeyTimeout = null
            if (_state.value != ClientState.READY) markReady("Whoosh online: ключ не прочитан")
        }
        whooshKeyTimeout = timeout
        handler.postDelayed(timeout, WHOOSH_KEY_TIMEOUT_MS)
    }

    private fun cancelWhoosh() {
        whooshRetry?.let { handler.removeCallbacks(it) }
        whooshRetry = null
        whooshKeyTimeout?.let { handler.removeCallbacks(it) }
        whooshKeyTimeout = null
        whooshAttempt = 0
        whooshDeadline = 0L
    }

    fun yandexAuth() {
        if (provider != Provider.YANDEX) return
        if (!isConnected()) {
            LogBus.warn(YANDEX_TAG, "Нет подключения")
            return
        }
        val deviceId = yandexDeviceId() ?: run {
            authenticated = false
            if (_state.value == ClientState.DISCOVERING) _state.value = ClientState.READY
            return
        }
        val key = prefs.yandexKey
        if (key.isBlank()) {
            authenticated = false
            if (_state.value == ClientState.DISCOVERING) _state.value = ClientState.READY
            LogBus.warn(YANDEX_TAG, "Укажите AUTH key из server password")
            return
        }
        val frame = try {
            YandexProtocol.auth(deviceId, key)
        } catch (e: IllegalArgumentException) {
            authenticated = false
            if (_state.value == ClientState.DISCOVERING) _state.value = ClientState.READY
            LogBus.err(YANDEX_TAG, e.message ?: "Ошибка AUTH")
            return
        }
        yandexSession = 0
        sendYandex(frame, YandexPending.AUTH, "AUTH")
    }

    fun yandexLock() {
        if (provider != Provider.YANDEX) return
        if (yandexSession == 0) {
            LogBus.warn(YANDEX_TAG, "Сначала AUTH")
            return
        }
        val deviceId = yandexDeviceId() ?: return
        sendYandex(YandexProtocol.lock1(deviceId, yandexSession), YandexPending.LOCK1, "LOCK 1")
    }

    fun yandexUnlock() {
        if (provider != Provider.YANDEX) return
        if (yandexSession == 0) {
            LogBus.warn(YANDEX_TAG, "Сначала AUTH")
            return
        }
        val deviceId = yandexDeviceId() ?: return
        val nonce = yandexNonce() ?: return
        val timestamp = System.currentTimeMillis() / 1000
        sendYandex(
            YandexProtocol.unlock1(deviceId, yandexSession, nonce, timestamp),
            YandexPending.UNLOCK1,
            "UNLOCK 1",
        )
    }

    fun yandexSound() {
        if (provider != Provider.YANDEX) return
        if (yandexSession == 0) {
            LogBus.warn(YANDEX_TAG, "Сначала AUTH")
            return
        }
        val deviceId = yandexDeviceId() ?: return
        sendYandex(YandexProtocol.sound(deviceId, yandexSession), YandexPending.SOUND, "SOUND")
    }

    fun yandexCableUnlock() {
        if (provider != Provider.YANDEX) return
        if (yandexSession == 0) {
            LogBus.warn(YANDEX_TAG, "Сначала AUTH")
            return
        }
        val deviceId = yandexDeviceId() ?: return
        sendYandex(YandexProtocol.cableUnlock(deviceId, yandexSession), YandexPending.CABLE, "CABLE")
    }

    fun yandexBatteryCover() {
        if (provider != Provider.YANDEX) return
        if (yandexSession == 0) {
            LogBus.warn(YANDEX_TAG, "Сначала AUTH")
            return
        }
        val deviceId = yandexDeviceId() ?: return
        cancelYandexTimeout()
        yandexPending = null
        writeCommand(YandexProtocol.batteryCover(deviceId, yandexSession), "BATTERY COVER")
    }

    private fun handleYandexFrame(data: ByteArray) {
        LogBus.rx(YANDEX_TAG, hex(data))
        val pending = yandexPending
        if (pending == null) {
            LogBus.info(YANDEX_TAG, "Ответ без ожидания")
            return
        }
        cancelYandexTimeout()
        when (pending) {
            YandexPending.AUTH -> {
                val session = YandexProtocol.parseAuth(data)
                if (session == null) {
                    yandexPending = null
                    authenticated = false
                    _state.value = ClientState.READY
                    LogBus.err(YANDEX_TAG, "AUTH: короткий ответ")
                } else {
                    yandexPending = null
                    yandexSession = session
                    authenticated = true
                    reconnectAttempts = 0
                    _state.value = ClientState.READY
                    LogBus.ok(YANDEX_TAG, "AUTH ok, session 0x${hexByte(session)}")
                }
            }
            YandexPending.LOCK1 -> {
                if (YandexProtocol.parseLock(data)) {
                    val deviceId = yandexDeviceId()
                    if (deviceId == null) {
                        yandexPending = null
                    } else {
                        sendYandex(YandexProtocol.lock2(deviceId, yandexSession), YandexPending.LOCK2, "LOCK 2")
                    }
                } else {
                    failYandex("LOCK 1")
                }
            }
            YandexPending.LOCK2 -> {
                yandexPending = null
                if (YandexProtocol.parseLock(data)) {
                    LogBus.ok(YANDEX_TAG, "LOCK ok")
                } else {
                    LogBus.err(YANDEX_TAG, "LOCK failed")
                }
            }
            YandexPending.UNLOCK1 -> {
                if (YandexProtocol.parseUnlock(data)) {
                    val deviceId = yandexDeviceId()
                    if (deviceId == null) {
                        yandexPending = null
                    } else {
                        sendYandex(YandexProtocol.unlock2(deviceId, yandexSession), YandexPending.UNLOCK2, "UNLOCK 2")
                    }
                } else {
                    failYandex("UNLOCK 1")
                }
            }
            YandexPending.UNLOCK2 -> {
                yandexPending = null
                if (YandexProtocol.parseUnlock(data)) {
                    LogBus.ok(YANDEX_TAG, "UNLOCK ok")
                } else {
                    LogBus.err(YANDEX_TAG, "UNLOCK failed")
                }
            }
            YandexPending.SOUND -> {
                yandexPending = null
                if (YandexProtocol.parseSound(data)) {
                    LogBus.ok(YANDEX_TAG, "SOUND ok")
                } else {
                    LogBus.err(YANDEX_TAG, "SOUND failed")
                }
            }
            YandexPending.CABLE -> {
                yandexPending = null
                if (YandexProtocol.parseCable(data)) {
                    LogBus.ok(YANDEX_TAG, "CABLE ok")
                } else {
                    LogBus.err(YANDEX_TAG, "CABLE failed")
                }
            }
            YandexPending.BATTERY -> {
                yandexPending = null
                LogBus.ok(YANDEX_TAG, "BATTERY COVER отправлено")
            }
        }
    }

    private fun sendYandex(frame: ByteArray, pending: YandexPending, label: String) {
        cancelYandexTimeout()
        yandexPending = pending
        if (!writeCommand(frame, label)) {
            yandexPending = null
            if (pending == YandexPending.AUTH) {
                authenticated = false
                _state.value = ClientState.READY
            }
            return
        }
        val timeout = Runnable {
            if (yandexPending == pending) {
                yandexPending = null
                if (pending == YandexPending.AUTH) {
                    authenticated = false
                    _state.value = ClientState.READY
                }
                LogBus.err(YANDEX_TAG, "Таймаут $label")
            }
        }
        yandexTimeout = timeout
        handler.postDelayed(timeout, YANDEX_TIMEOUT_MS)
    }

    private fun failYandex(label: String) {
        yandexPending = null
        LogBus.err(YANDEX_TAG, "$label: ошибка")
    }

    private fun cancelYandexTimeout() {
        yandexTimeout?.let { handler.removeCallbacks(it) }
        yandexTimeout = null
    }

    private fun cancelYandex() {
        cancelYandexTimeout()
        yandexPending = null
    }

    private fun yandexDeviceId(): Int? {
        val text = prefs.yandexDeviceId.trim().removePrefix("0x").removePrefix("0X")
        val value = text.toIntOrNull(16)
        if (value == null || value !in 0..0xFFFF) {
            LogBus.warn(YANDEX_TAG, "Укажите device id (16-bit hex)")
            return null
        }
        return value
    }

    private fun yandexNonce(): ByteArray? {
        val nonce = parseHex(prefs.yandexNonce)
        if (nonce == null || nonce.size != 4) {
            LogBus.warn(YANDEX_TAG, "Укажите 4-байтовый nonce в hex")
            return null
        }
        return nonce
    }

    fun sendRawHex(text: String) {
        if (provider == Provider.URENT) {
            LogBus.warn(TAG, "Для Urent используйте ручную команду")
            return
        }
        val bytes = parseHex(text)
        if (bytes == null || bytes.isEmpty()) {
            LogBus.err(TAG, "Неверный hex")
            return
        }
        if (!isConnected()) {
            LogBus.warn(TAG, "Нет подключения")
            return
        }
        cancelYandexTimeout()
        yandexPending = null
        writeCommand(bytes, "raw ${provider.title}")
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
            ScooterCodec.CMD_EJECT_BATTERY -> report(frame, "Успешное изъятие батареи", "Ошибка изъятия батареи")
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

    private fun report(frame: ScooterFrame, okText: String, failText: String, successCodes: Set<Int> = setOf(1)) {
        val code = frame.payload.firstOrNull()?.toInt()?.and(0xFF)
        val ok = code != null && code in successCodes
        LogBus.log(if (ok) LogLevel.OK else LogLevel.WARN, "SCOOTER", if (ok) okText else failText)
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
        if (provider == Provider.WHOOSH && whooshMode == WhooshMode.ONLINE) {
            val ok = transport?.read(WhooshProtocol.ONLINE_DIGITAL) == true
            LogBus.info(WHOOSH_TAG, if (ok) "Чтение digital_output" else "digital_output недоступен")
            return
        }
        if (provider != Provider.URENT) return
        if (!isReady) return
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

    private fun startDemo() {
        stopDemo()
        linkLost = true
        demoLocked = false
        _state.value = ClientState.DEMO
        sessionKey = 0x42
        authenticated = true
        _vehicle.value = VehicleInfo(batteryPercent = 87, mode = 2, speed = 0.0, singleMileage = 45230, remainingRange = 31000)
        _iot.value = IotInfo(value = 4200, flags = 0x01, locked = false, version = "1.4.2")
        LogBus.info(DEMO_TAG, "Демо-режим: подключено к $deviceName")
        LogBus.ok(AUTH_TAG, "Ключ верный, сессионный ключ 0x42")
        var speed = 0.0
        val tick = object : Runnable {
            override fun run() {
                if (_state.value != ClientState.DEMO) return
                speed = when {
                    speed > 24.0 -> 0.0
                    else -> speed + 1.7
                }
                val v = _vehicle.value ?: VehicleInfo(87, 2, 0.0, 0, 0)
                _vehicle.value = v.copy(
                    speed = speed,
                    singleMileage = v.singleMileage + 3,
                    remainingRange = (v.remainingRange - 3).coerceAtLeast(0),
                )
                handler.postDelayed(this, DEMO_TICK_MS)
            }
        }
        demoTick = tick
        handler.postDelayed(tick, DEMO_TICK_MS)
    }

    private fun stopDemo() {
        demoTick?.let { handler.removeCallbacks(it) }
        demoTick = null
        demoLocked = false
        authenticated = false
        _vehicle.value = null
        _iot.value = null
    }

    private fun demoReply(frame: ByteArray) {
        try {
            val parsed = FrameCodec.parse(frame)
            val vehicle = _vehicle.value
            if (parsed.cmd == ScooterCodec.CMD_UNLOCK) demoLocked = false
            if (parsed.cmd == ScooterCodec.CMD_LOCK) demoLocked = true
            val reply = when (parsed.cmd) {
                ScooterCodec.CMD_KEY -> FrameCodec.build(0x01, 0x42, byteArrayOf(1, 0x42))
                ScooterCodec.CMD_UNLOCK -> FrameCodec.build(0x05, 0x42, byteArrayOf(1))
                ScooterCodec.CMD_LOCK -> FrameCodec.build(0x15, 0x42, byteArrayOf(1))
                ScooterCodec.CMD_EJECT_BATTERY -> FrameCodec.build(0x81, 0x42, byteArrayOf(1))
                ScooterCodec.CMD_SCOOTER_SET -> FrameCodec.build(0x61, 0x42, byteArrayOf(0))
                ScooterCodec.CMD_DEVICE_INFO -> FrameCodec.build(
                    ScooterCodec.CMD_DEVICE_INFO,
                    0x42,
                    byteArrayOf(0x10, 0x68, if (demoLocked) 0x00 else 0x01, 0x00, 1, 4, 2),
                )
                ScooterCodec.CMD_SCOOTER_INFO -> {
                    val v = vehicle ?: VehicleInfo(87, 2, 0.0, 45230, 31000)
                    FrameCodec.build(
                        ScooterCodec.CMD_SCOOTER_INFO,
                        0x42,
                        byteArrayOf(
                            v.batteryPercent.toByte(),
                            v.mode.toByte(),
                            ((v.speed * 10).toInt() shr 8).toByte(),
                            ((v.speed * 10).toInt() and 0xFF).toByte(),
                            ((v.singleMileage / 10) shr 8).toByte(),
                            ((v.singleMileage / 10) and 0xFF).toByte(),
                            ((v.remainingRange / 10) shr 8).toByte(),
                            ((v.remainingRange / 10) and 0xFF).toByte(),
                        ),
                    )
                }
                ScooterCodec.CMD_REALTIME -> {
                    val v = vehicle ?: VehicleInfo(87, 2, 0.0, 45230, 31000)
                    val speedRaw = (v.speed * 10).toInt()
                    FrameCodec.build(
                        ScooterCodec.CMD_REALTIME,
                        0x42,
                        byteArrayOf(
                            v.batteryPercent.toByte(),
                            (speedRaw shr 8).toByte(),
                            (speedRaw and 0xFF).toByte(),
                            0x00,
                            0x8C.toByte(),
                            0x01,
                        ),
                    )
                }
                ScooterCodec.CMD_RFID -> FrameCodec.build(
                    ScooterCodec.CMD_RFID,
                    0x42,
                    byteArrayOf(0x12, 0x34, 0x56, 0x78),
                )
                ScooterCodec.CMD_FW_INFO -> FrameCodec.build(
                    ScooterCodec.CMD_FW_INFO,
                    0x42,
                    "FW 1.2.3 demo".toByteArray(Charsets.US_ASCII),
                )
                ScooterCodec.CMD_RFID_DELETE -> FrameCodec.build(
                    ScooterCodec.CMD_RFID_DELETE,
                    0x42,
                    byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x00, 0x00, 0x00, 0x01),
                )
                ScooterCodec.CMD_FIND_CAR -> FrameCodec.build(ScooterCodec.CMD_FIND_CAR, 0x42, byteArrayOf(1))
                else -> FrameCodec.build(parsed.cmd, 0x42, byteArrayOf(1))
            }
            handler.postDelayed({
                if (_state.value == ClientState.DEMO) handleFrame(FrameCodec.parse(reply))
            }, 120)
            if (parsed.cmd == ScooterCodec.CMD_LOCK || parsed.cmd == ScooterCodec.CMD_UNLOCK) {
                val info = FrameCodec.build(
                    ScooterCodec.CMD_DEVICE_INFO,
                    0x42,
                    byteArrayOf(0x10, 0x68, if (demoLocked) 0x00 else 0x01, 0x00, 1, 4, 2),
                )
                handler.postDelayed({
                    if (_state.value == ClientState.DEMO) handleFrame(FrameCodec.parse(info))
                }, 260)
            }
        } catch (e: Exception) {
            LogBus.err(DEMO_TAG, "Ошибка демо-ответа: ${e.message}")
        }
    }
}
