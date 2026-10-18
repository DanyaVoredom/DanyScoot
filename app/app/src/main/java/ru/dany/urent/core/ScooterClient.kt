package ru.dany.urent.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Handler
import android.os.Looper
import ru.dany.urent.ble.BleTransport
import ru.dany.urent.ble.FrameCodec
import ru.dany.urent.ble.IotInfo
import ru.dany.urent.ble.ScannedDevice
import ru.dany.urent.ble.ScooterCodec
import ru.dany.urent.ble.ScooterFrame
import ru.dany.urent.ble.VehicleInfo
import ru.dany.urent.ble.hex
import ru.dany.urent.ble.hexByte
import java.util.Locale
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
    private const val DEMO_TICK_MS = 1500L
    private val LOCK_SUCCESS_CODES = setOf(1, 2)

    private lateinit var prefs: Prefs
    @SuppressLint("StaticFieldLeak")
    private var transport: BleTransport? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rxBuffer = ByteArray(0)
    private var authPending = false
    private var authenticated = false
    private var authTimeout: Runnable? = null
    private var spamRunning = false
    private var spamRunnable: Runnable? = null
    private var reconnectAttempts = 0
    private var reconnectRunnable: Runnable? = null
    private var linkLost = true
    private var wantedSpam = false
    private var demoTick: Runnable? = null
    private var demoLocked = false

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

    fun init(context: Context) {
        if (transport == null) {
            prefs = Prefs.get(context)
            transport = BleTransport(context.applicationContext).also { it.listener = this }
        }
    }

    fun bluetoothSupported(): Boolean = transport?.isSupported == true

    fun bluetoothEnabled(): Boolean = transport?.isBluetoothEnabled == true

    fun startScan() {
        if (prefs.demoMode) {
            LogBus.info(DEMO_TAG, "Режим демо: сканирование отключено")
            return
        }
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
        t.startScan(filterUart = !prefs.showAllDevices)
    }

    fun stopScan() {
        transport?.stopScan()
        if (_state.value == ClientState.SCANNING) {
            _state.value = ClientState.IDLE
        }
    }

    fun connect(address: String, name: String) {
        cancelReconnect()
        linkLost = false
        rxBuffer = ByteArray(0)
        authPending = false
        authenticated = false
        authTimeout?.let { handler.removeCallbacks(it) }
        authTimeout = null
        sessionKey = 0
        _lastError.value = ""
        stopSpam()
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
        authPending = false
        authenticated = false
        authTimeout?.let { handler.removeCallbacks(it) }
        authTimeout = null
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
        val key = prefs.bleKey
        LogBus.info(AUTH_TAG, "Отправка ключа: $key")
        authPending = true
        _state.value = ClientState.AUTHENTICATING
        if (!writeFrame(ScooterCodec.keyCommand(key), "ключ")) {
            authPending = false
            authenticated = false
            _state.value = ClientState.READY
            return
        }
        authTimeout?.let { handler.removeCallbacks(it) }
        val timeout = Runnable {
            if (authPending) {
                authPending = false
                authenticated = false
                sessionKey = 0
                LogBus.err(AUTH_TAG, "Таймаут авторизации")
                _state.value = ClientState.READY
            }
        }
        authTimeout = timeout
        handler.postDelayed(timeout, AUTH_TIMEOUT_MS)
    }

    fun unlock() {
        val key = requireKey() ?: return
        writeCommand(ScooterCodec.unlockCommand(key), "разблокировка")
    }

    fun lock() {
        val key = requireKey() ?: return
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

    fun startSpam() {
        if (spamRunning) return
        if (!isReady) {
            LogBus.warn(SPAM_TAG, "Сначала авторизуйтесь")
            return
        }
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

    private fun writeCommand(frame: ByteArray, label: String) {
        if (isDemo) {
            LogBus.tx("TX", "${label}: ${hex(frame)}")
            demoReply(frame)
            return
        }
        writeFrame(frame, label)
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
            if (!prefs.showAllDevices && !device.hasUart) return
            list.add(device)
            if (prefs.showScanLog) {
                LogBus.info("SCAN", "${device.name} ${device.address} RSSI=${device.rssi} UART=${device.hasUart}")
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
            authenticate()
        }
    }

    override fun onFrame(data: ByteArray) {
        handler.post { processFrame(data) }
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
        authTimeout?.let { handler.removeCallbacks(it) }
        authTimeout = null
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
            }
            ScooterCodec.CMD_UNLOCK -> report(frame, "Самокат разблокирован", "Ошибка разблокировки", LOCK_SUCCESS_CODES)
            ScooterCodec.CMD_LOCK -> report(frame, "Самокат заблокирован", "Ошибка блокировки", LOCK_SUCCESS_CODES)
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

    private fun handleAuthFrame(frame: ScooterFrame) {
        if (!authPending) return
        authPending = false
        authTimeout?.let { handler.removeCallbacks(it) }
        authTimeout = null
        if (frame.payload.size >= 2 && (frame.payload[0].toInt() and 0xFF) == 1) {
            sessionKey = frame.payload[1].toInt() and 0xFF
            authenticated = true
            reconnectAttempts = 0
            LogBus.ok(AUTH_TAG, "Ключ верный, сессионный ключ 0x${hexByte(sessionKey)}")
            _state.value = ClientState.READY
            onAuthenticated()
        } else {
            sessionKey = 0
            authenticated = false
            LogBus.err(AUTH_TAG, "Неверный ключ: попробуйте другой ключ в настройках")
            _state.value = ClientState.READY
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
