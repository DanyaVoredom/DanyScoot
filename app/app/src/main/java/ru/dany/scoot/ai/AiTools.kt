package ru.dany.scoot.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.dany.scoot.AiActivity
import ru.dany.scoot.AppTheme
import ru.dany.scoot.BuildConfig
import ru.dany.scoot.MainActivity
import ru.dany.scoot.ble.DeviceKind
import ru.dany.scoot.ble.DeviceSort
import ru.dany.scoot.ble.OpcodeCatalog
import ru.dany.scoot.ble.hex
import ru.dany.scoot.ble.parseHex
import ru.dany.scoot.ble.shortUuid
import ru.dany.scoot.core.ClientState
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.LogLevel
import ru.dany.scoot.core.Prefs
import ru.dany.scoot.core.RemoteInfo
import ru.dany.scoot.core.ScooterClient
import ru.dany.scoot.core.UpdateManager

object AiTools {

    private const val EDIFIER_WRITE = "00001101-d102-11e1-9b23-00025b00a5a5"
    private const val EDIFIER_NOTIFY = "00001102-d102-11e1-9b23-00025b00a5a5"
    private const val EDIFIER_EVENT = "00001103-d102-11e1-9b23-00025b00a5a5"
    private const val BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb"

    val specs: List<AiToolSpec> = listOf(
        tool(
            "list_devices",
            "Показать найденные при сканировании BLE-устройства: адрес, имя, RSSI, примерное расстояние и тип.",
            noArgs(),
        ),
        tool(
            "scan_devices",
            "Запустить сканирование BLE и дождаться его завершения. Возвращает список найденных устройств.",
            noArgs(),
        ),
        tool(
            "stop_scan",
            "Остановить текущее сканирование BLE.",
            noArgs(),
        ),
        tool(
            "connect_device",
            "Подключиться к BLE-устройству по адресу или имени из списка сканирования и дождаться готовности.",
            objectParams(
                required = emptyList(),
                "address" to stringProp("MAC-адрес устройства"),
                "name" to stringProp("Часть имени устройства"),
                "kind" to enumProp("Тип устройства", "scooter", "headset", "other"),
                "timeout_ms" to intProp("Таймаут ожидания готовности", 1000, 30000),
            ),
        ),
        tool(
            "connect_last",
            "Подключиться к последнему использованному устройству и дождаться готовности.",
            objectParams(
                required = emptyList(),
                "timeout_ms" to intProp("Таймаут ожидания готовности", 1000, 30000),
            ),
        ),
        tool(
            "disconnect_device",
            "Отключиться от текущего BLE-устройства.",
            noArgs(),
        ),
        tool(
            "wait_for_ready",
            "Дождаться состояния готовности текущего подключения.",
            objectParams(
                required = emptyList(),
                "timeout_ms" to intProp("Таймаут ожидания", 500, 60000),
            ),
        ),
        tool(
            "get_state",
            "Текущее состояние BLE: подключение, устройство, готовность, телеметрия самоката, данные гарнитуры.",
            noArgs(),
        ),
        tool(
            "get_gatt",
            "Полный дамп GATT: сервисы, характеристики, свойства и разрешения.",
            noArgs(),
        ),
        tool(
            "read_characteristic",
            "Прочитать характеристику по UUID и вернуть hex и ASCII.",
            objectParams(
                required = listOf("uuid"),
                "uuid" to stringProp("UUID характеристики, короткий или полный"),
                "timeout_ms" to intProp("Таймаут чтения", 500, 15000),
            ),
        ),
        tool(
            "write_characteristic",
            "Записать данные в характеристику по UUID.",
            objectParams(
                required = listOf("uuid", "hex"),
                "uuid" to stringProp("UUID характеристики, короткий или полный"),
                "hex" to stringProp("Данные в hex, например 01A0FF"),
                "without_response" to boolProp("Без ответа (write without response)"),
            ),
        ),
        tool(
            "subscribe_characteristic",
            "Включить или выключить уведомления характеристики по UUID.",
            objectParams(
                required = listOf("uuid"),
                "uuid" to stringProp("UUID характеристики, короткий или полный"),
                "enable" to boolProp("Включить уведомления, по умолчанию да"),
            ),
        ),
        tool(
            "get_notifications",
            "Последние уведомления от устройства с временем, UUID и данными.",
            objectParams(
                required = emptyList(),
                "limit" to intProp("Количество, по умолчанию 30", 1, 120),
            ),
        ),
        tool(
            "get_remote_info",
            "Данные удалённого устройства: батарея, производитель, модель, серийный номер, прошивка, железо, ПО.",
            noArgs(),
        ),
        tool(
            "refresh_remote_info",
            "Перечитать стандартные характеристики устройства и вернуть обновлённые данные.",
            noArgs(),
        ),
        tool(
            "authenticate",
            "Авторизоваться на самокате ключом. Если ключ передан, он сохраняется в настройках.",
            objectParams(
                required = emptyList(),
                "key" to stringProp("Ключ устройства"),
            ),
        ),
        tool(
            "unlock_scooter",
            "Разблокировать самокат. Требуется подключение и авторизация.",
            noArgs(),
        ),
        tool(
            "lock_scooter",
            "Заблокировать самокат. Требуется подключение и авторизация.",
            noArgs(),
        ),
        tool(
            "eject_battery",
            "Изъять батарею самоката командой 0x81. Опасное действие, только по явной просьбе.",
            noArgs(),
        ),
        tool(
            "find_scooter",
            "Подать звуковой сигнал на самокате командой 0xA2.",
            noArgs(),
        ),
        tool(
            "set_scooter",
            "Применить настройки самоката: фара, режим и газ.",
            objectParams(
                required = emptyList(),
                "headlight" to boolProp("Фара включена, по умолчанию да"),
                "mode" to intProp("Режим 1, 2 или 3, по умолчанию 3", 1, 3),
                "throttle" to boolProp("Газ разрешён, по умолчанию да"),
            ),
        ),
        tool(
            "request_telemetry",
            "Запросить у самоката телеметрию: батарея, скорость, пробег, запас хода, RFID.",
            noArgs(),
        ),
        tool(
            "send_scooter_frame",
            "Собрать и отправить кадр самоката с указанным опкодом, payload и ключом.",
            objectParams(
                required = listOf("cmd"),
                "cmd" to intProp("Опкод, например 5 или 0xA2", 0, 255),
                "payload" to stringProp("Payload в hex, по умолчанию пусто"),
                "key" to intProp("Ключ кадра, по умолчанию сессионный", 0, 255),
            ),
        ),
        tool(
            "send_raw_command",
            "Отправить команду самоката с сессионным ключом сборщиком приложения.",
            objectParams(
                required = listOf("cmd"),
                "cmd" to intProp("Опкод", 0, 255),
                "payload" to stringProp("Payload в hex"),
            ),
        ),
        tool(
            "send_service_command",
            "Отправить сервисную команду самоката с сессионным ключом.",
            objectParams(
                required = listOf("cmd"),
                "cmd" to intProp("Опкод", 0, 255),
                "payload" to stringProp("Payload в hex"),
            ),
        ),
        tool(
            "send_profile_bytes",
            "Записать произвольные байты в основной канал профиля устройства без сборки кадра.",
            objectParams(
                required = listOf("hex"),
                "hex" to stringProp("Байты в hex"),
            ),
        ),
        tool(
            "bypass_unlock",
            "Попробовать разблокировку без авторизации ключом 0. Опасное экспериментальное действие.",
            noArgs(),
        ),
        tool(
            "start_key_brute",
            "Запустить перебор session key 0..255. Опасное экспериментальное действие.",
            noArgs(),
        ),
        tool(
            "stop_key_brute",
            "Остановить перебор session key.",
            noArgs(),
        ),
        tool(
            "start_spam",
            "Запустить периодическую разблокировку самоката. Требуется авторизация.",
            noArgs(),
        ),
        tool(
            "stop_spam",
            "Остановить спам разблокировки.",
            noArgs(),
        ),
        tool(
            "opcode_catalog",
            "Каталог известных опкодов самоката с направлением и описанием.",
            objectParams(
                required = emptyList(),
                "query" to stringProp("Фильтр по имени, коду или описанию"),
            ),
        ),
        tool(
            "get_log",
            "Последние строки журнала приложения.",
            objectParams(
                required = emptyList(),
                "limit" to intProp("Количество строк, по умолчанию 40", 1, 200),
                "tag" to stringProp("Фильтр по тегу"),
                "level" to enumProp("Фильтр по уровню", "INFO", "TX", "RX", "OK", "WARN", "ERR"),
            ),
        ),
        tool(
            "get_settings",
            "Текущие настройки приложения без BLE-ключа и API-ключа AI.",
            noArgs(),
        ),
        tool(
            "set_setting",
            "Изменить настройку приложения по имени.",
            objectParams(
                required = listOf("name", "value"),
                "name" to enumProp(
                    "Имя настройки",
                    "ble_key",
                    "spam_interval_ms",
                    "auto_reconnect",
                    "auto_reconnect_attempts",
                    "auto_reconnect_delay_ms",
                    "auto_resume_spam",
                    "show_all_devices",
                    "hide_unknown_devices",
                    "device_sort",
                    "show_scan_log",
                    "auto_update",
                    "theme",
                    "ai_base_url",
                    "ai_model",
                    "ai_format",
                ),
                "value" to stringProp("Новое значение"),
            ),
        ),
        tool(
            "app_info",
            "Версия приложения, модель и версия Android, доступность Bluetooth.",
            noArgs(),
        ),
        tool(
            "navigate",
            "Открыть экран приложения: устройства, управление, GATT, журнал, настройки или AI.",
            objectParams(
                required = listOf("screen"),
                "screen" to enumProp(
                    "Экран",
                    "devices",
                    "control",
                    "gatt",
                    "log",
                    "settings",
                    "ai",
                ),
            ),
        ),
        tool(
            "clear_log",
            "Очистить журнал приложения.",
            noArgs(),
        ),
        tool(
            "check_update",
            "Проверить обновления на Codeberg и скачать APK при наличии новой версии.",
            noArgs(),
        ),
        tool(
            "install_update",
            "Запустить установщик уже скачанного обновления.",
            noArgs(),
        ),
        tool(
            "edifier_send",
            "Отправить байты в канал управления Edifier. По умолчанию характеристика 1101.",
            objectParams(
                required = listOf("hex"),
                "hex" to stringProp("Байты в hex"),
                "uuid" to stringProp("UUID характеристики, по умолчанию Edifier 1101"),
            ),
        ),
        tool(
            "edifier_read",
            "Прочитать характеристику Edifier: battery, notify или event.",
            objectParams(
                required = emptyList(),
                "target" to enumProp("Что читать", "battery", "notify", "event"),
            ),
        ),
    )

    suspend fun execute(context: Context, call: AiToolCall): String = try {
        withContext(Dispatchers.Main.immediate) {
            run(context.applicationContext, call.name, arguments(call.arguments))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "Ошибка: ${e.message ?: e.javaClass.simpleName}"
    }

    fun contextSnapshot(): String {
        val kind = ScooterClient.activeKind
        val ready = ScooterClient.isReady
        val error = ScooterClient.lastError.value
        return buildString {
            append("DanyScoot ").append(BuildConfig.VERSION_NAME).append(". ")
            append("Bluetooth ").append(if (ScooterClient.bluetoothEnabled()) "включен" else "выключен").append(". ")
            append("Состояние: ").append(ScooterClient.state.value.name).append(". ")
            if (ScooterClient.deviceName.isNotEmpty()) {
                append("Устройство: ").append(ScooterClient.deviceName)
                append(" (").append(ScooterClient.deviceAddress).append("), тип: ").append(kind.name)
                append(", готово: ").append(if (ready) "да" else "нет").append(". ")
            } else {
                append("Устройство не выбрано. ")
            }
            if (error.isNotEmpty()) append("Последняя ошибка: ").append(error).append(". ")
        }
    }

    private suspend fun run(context: Context, name: String, args: JSONObject): String {
        val prefs = Prefs.get(context)
        return when (name) {
            "list_devices" -> devicesJson()
            "scan_devices" -> {
                ScooterClient.startScan()
                val deadline = System.currentTimeMillis() + 17000
                while (System.currentTimeMillis() < deadline && ScooterClient.state.value == ClientState.SCANNING) {
                    delay(250)
                }
                if (ScooterClient.state.value == ClientState.SCANNING) ScooterClient.stopScan()
                devicesJson()
            }
            "stop_scan" -> {
                ScooterClient.stopScan()
                ok("Сканирование остановлено")
            }
            "connect_device" -> connectDevice(context, args)
            "connect_last" -> {
                if (!ScooterClient.hasLastDevice()) throw IllegalStateException("Нет последнего устройства")
                if (!ScooterClient.reconnectLast()) throw IllegalStateException("Не удалось начать подключение")
                awaitReady(args.optInt("timeout_ms", 15000).coerceIn(1000, 30000).toLong())
            }
            "disconnect_device" -> {
                ScooterClient.disconnect()
                ok("Отключено")
            }
            "wait_for_ready" -> awaitReady(args.optInt("timeout_ms", 15000).coerceIn(500, 60000).toLong())
            "get_state" -> stateJson().toString()
            "get_gatt" -> ScooterClient.gatt.value?.toText()?.takeIf { it.isNotBlank() }
                ?: "GATT недоступен, подключитесь к устройству"
            "read_characteristic" -> readCharacteristic(args)
            "write_characteristic" -> writeCharacteristic(args)
            "subscribe_characteristic" -> subscribeCharacteristic(args)
            "get_notifications" -> notificationsJson(args.optInt("limit", 30).coerceIn(1, 120))
            "get_remote_info" -> remoteJson(ScooterClient.remote.value).toString()
            "refresh_remote_info" -> {
                requireLink()
                ScooterClient.refreshRemoteInfo()
                delay(2400)
                remoteJson(ScooterClient.remote.value).toString()
            }
            "authenticate" -> {
                if (!ScooterClient.isScooter) throw IllegalStateException("Авторизация только для самоката")
                stringArg(args, "key")?.let { prefs.bleKey = it }
                requireLink()
                ScooterClient.authenticate()
                awaitReady(6000)
            }
            "unlock_scooter" -> scooterAction("Разблокировка отправлена") { ScooterClient.unlock() }
            "lock_scooter" -> scooterAction("Блокировка отправлена") { ScooterClient.lock() }
            "eject_battery" -> scooterAction("Команда изъятия батареи отправлена") { ScooterClient.ejectBattery() }
            "find_scooter" -> scooterAction("Сигнал отправлен") { ScooterClient.findCar() }
            "set_scooter" -> {
                requireReady()
                val headlight = boolArg(args, "headlight") ?: true
                val mode = (intArg(args, "mode") ?: 3).coerceIn(1, 3)
                val throttle = boolArg(args, "throttle") ?: true
                ScooterClient.setScooter(headlight, mode - 1, throttle)
                ok("Настройки отправлены: фара=${if (headlight) "вкл" else "выкл"}, режим=$mode, газ=${if (throttle) "вкл" else "выкл"}")
            }
            "request_telemetry" -> {
                requireReady()
                ScooterClient.requestTelemetry()
                delay(600)
                stateJson().toString()
            }
            "send_scooter_frame" -> {
                requireLink()
                val cmd = requireInt(args, "cmd", 0, 255)
                val payload = bytesArg(args, "payload")
                val key = intArg(args, "key")?.coerceIn(0, 255) ?: ScooterClient.sessionKey
                val sent = ScooterClient.sendFrameKey(cmd, payload, key)
                ok("Кадр ${hexByteText(cmd)} отправлен: $sent")
            }
            "send_raw_command" -> {
                requireReady()
                val cmd = requireInt(args, "cmd", 0, 255)
                val payload = bytesArg(args, "payload")
                ScooterClient.sendRaw(cmd, payload)
                ok("Команда ${hexByteText(cmd)} отправлена")
            }
            "send_service_command" -> {
                requireReady()
                val cmd = requireInt(args, "cmd", 0, 255)
                val payload = bytesArg(args, "payload")
                ScooterClient.sendService(cmd, payload)
                ok("Сервисная команда ${hexByteText(cmd)} отправлена")
            }
            "send_profile_bytes" -> {
                requireLink()
                val data = bytesArg(args, "hex")
                if (data.isEmpty()) throw IllegalArgumentException("Пустые данные")
                val sent = ScooterClient.sendProfileBytes(data)
                ok("Записано ${data.size} байт: $sent")
            }
            "bypass_unlock" -> scooterLinkAction("Попытка обхода отправлена") { ScooterClient.bypassUnlock() }
            "start_key_brute" -> scooterLinkAction("Перебор запущен") { ScooterClient.startKeyBrute() }
            "stop_key_brute" -> {
                ScooterClient.stopKeyBrute()
                ok("Перебор остановлен")
            }
            "start_spam" -> scooterAction("Спам запущен") { ScooterClient.startSpam() }
            "stop_spam" -> {
                ScooterClient.stopSpam()
                ok("Спам остановлен")
            }
            "opcode_catalog" -> opcodeCatalog(stringArg(args, "query"))
            "get_log" -> logText(args)
            "get_settings" -> settingsJson(prefs).toString()
            "set_setting" -> setSetting(prefs, args)
            "app_info" -> appInfo().toString()
            "navigate" -> {
                val screen = stringArg(args, "screen") ?: throw IllegalArgumentException("Не указан экран")
                openScreen(context, screen)
            }
            "clear_log" -> {
                LogBus.clear()
                ok("Журнал очищен")
            }
            "check_update" -> checkUpdate(context)
            "install_update" -> installUpdate(context)
            "edifier_send" -> {
                requireLink()
                val uuid = stringArg(args, "uuid")?.let { parseUuid(it) } ?: UUID.fromString(EDIFIER_WRITE)
                val data = bytesArg(args, "hex")
                if (data.isEmpty()) throw IllegalArgumentException("Пустые данные")
                val sent = ScooterClient.writeCharacteristic(uuid, data, null)
                ok("Отправлено ${data.size} байт в ${shortUuid(uuid)}: $sent")
            }
            "edifier_read" -> {
                requireLink()
                val target = stringArg(args, "target") ?: "battery"
                val uuid = when (target.lowercase(Locale.ROOT)) {
                    "notify" -> EDIFIER_NOTIFY
                    "event" -> EDIFIER_EVENT
                    else -> BATTERY_LEVEL
                }
                readCharacteristic(JSONObject().put("uuid", uuid).put("timeout_ms", 3500))
            }
            else -> "Неизвестный инструмент: $name"
        }
    }

    private suspend fun connectDevice(context: Context, args: JSONObject): String {
        val prefs = Prefs.get(context)
        val address = stringArg(args, "address")
        val name = stringArg(args, "name")
        val scanned = ScooterClient.devices.value
        val found = when {
            address != null -> scanned.firstOrNull { it.address.equals(address, ignoreCase = true) }
            name != null -> scanned.firstOrNull { it.name.contains(name, ignoreCase = true) }
            else -> null
        }
        if (name != null && found == null) throw IllegalStateException("Устройство не найдено в списке сканирования")
        val targetAddress = found?.address ?: address ?: prefs.lastDeviceAddress
        if (targetAddress.isEmpty()) throw IllegalStateException("Не указан адрес и нет последнего устройства")
        val targetName = found?.name ?: name ?: prefs.lastDeviceName
        val kind = found?.kind ?: when (stringArg(args, "kind")?.lowercase(Locale.ROOT)) {
            "scooter" -> DeviceKind.SCOOTER
            "headset" -> DeviceKind.HEADSET
            "other" -> DeviceKind.OTHER
            else -> when {
                targetAddress.equals(prefs.lastDeviceAddress, ignoreCase = true) -> prefs.lastDeviceKind
                targetName.contains("edifier", ignoreCase = true) -> DeviceKind.HEADSET
                else -> DeviceKind.SCOOTER
            }
        }
        ScooterClient.connect(targetAddress, targetName, kind)
        return awaitReady(args.optInt("timeout_ms", 15000).coerceIn(1000, 30000).toLong())
    }

    private suspend fun awaitReady(timeoutMs: Long): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var busy = false
        while (System.currentTimeMillis() < deadline) {
            val state = ScooterClient.state.value
            if (ScooterClient.isReady) break
            if (state == ClientState.ERROR) break
            if (state == ClientState.READY && !ScooterClient.isReady) break
            if (state == ClientState.CONNECTING || state == ClientState.DISCOVERING ||
                state == ClientState.AUTHENTICATING
            ) {
                busy = true
            }
            if (state == ClientState.IDLE && busy) break
            delay(150)
        }
        return stateJson().toString()
    }

    private fun stateJson(): JSONObject {
        val json = JSONObject()
        json.put("state", ScooterClient.state.value.name)
        json.put("device_name", ScooterClient.deviceName)
        json.put("address", ScooterClient.deviceAddress)
        json.put("kind", ScooterClient.activeKind.name)
        json.put("ready", ScooterClient.isReady)
        json.put("link", ScooterClient.hasLink)
        json.put("session_key", ScooterClient.sessionKey)
        json.put("spam_active", ScooterClient.spamActive.value)
        json.put("spam_sent", ScooterClient.spamSent.value)
        json.put("last_error", ScooterClient.lastError.value)
        ScooterClient.vehicle.value?.let { v ->
            json.put(
                "vehicle",
                JSONObject()
                    .put("battery_percent", v.batteryPercent)
                    .put("mode", v.mode)
                    .put("speed_kmh", v.speed)
                    .put("mileage_m", v.singleMileage)
                    .put("range_m", v.remainingRange),
            )
        }
        ScooterClient.iot.value?.let { i ->
            json.put(
                "iot",
                JSONObject()
                    .put("value", i.value)
                    .put("flags", i.flags)
                    .put("locked", i.locked)
                    .put("version", i.version)
                    .put("text", i.text ?: ""),
            )
        }
        val remote = ScooterClient.remote.value
        if (remote != null) json.put("remote", remoteJson(remote))
        return json
    }

    private fun remoteJson(info: RemoteInfo?): JSONObject {
        val json = JSONObject()
        if (info == null) {
            json.put("available", false)
            return json
        }
        json.put("available", true)
        json.put("name", info.name)
        info.batteryPercent?.let { json.put("battery_percent", it) }
        json.put("manufacturer", info.manufacturer)
        json.put("model", info.model)
        json.put("serial", info.serial)
        json.put("firmware", info.firmware)
        json.put("hardware", info.hardware)
        json.put("software", info.software)
        info.txPower?.let { json.put("tx_power", it) }
        json.put("last_uuid", info.lastUuid)
        json.put("last_hex", info.lastHex)
        return json
    }

    private fun devicesJson(): String {
        val list = ScooterClient.devices.value.sortedByDescending { it.rssi }
        val array = JSONArray()
        for (device in list) {
            array.put(
                JSONObject()
                    .put("address", device.address)
                    .put("name", device.name)
                    .put("rssi", device.rssi)
                    .put("distance_m", String.format(Locale.ROOT, "%.1f", device.distanceMeters))
                    .put("kind", device.kind.name)
                    .put("uart", device.hasUart)
                    .put("edifier", device.hasEdifier)
                    .put("advert", device.advert),
            )
        }
        return JSONObject().put("count", list.size).put("devices", array).toString()
    }

    private suspend fun readCharacteristic(args: JSONObject): String {
        requireLink()
        val uuid = uuidArg(args)
        val timeout = args.optInt("timeout_ms", 3500).coerceIn(500, 15000).toLong()
        val value = ScooterClient.readValue(uuid, timeout)
            ?: throw IllegalStateException("Нет ответа от ${shortUuid(uuid)}")
        return JSONObject()
            .put("uuid", uuid.toString())
            .put("length", value.size)
            .put("hex", hex(value))
            .put("ascii", ascii(value))
            .toString()
    }

    private fun writeCharacteristic(args: JSONObject): String {
        requireLink()
        val uuid = uuidArg(args)
        val data = bytesArg(args, "hex")
        if (data.isEmpty()) throw IllegalArgumentException("Пустые данные")
        val withoutResponse = boolArg(args, "without_response")
        val sent = ScooterClient.writeCharacteristic(uuid, data, withoutResponse)
        return ok("Запись ${data.size} байт в ${shortUuid(uuid)}: $sent")
    }

    private fun subscribeCharacteristic(args: JSONObject): String {
        requireLink()
        val uuid = uuidArg(args)
        val enable = boolArg(args, "enable") ?: true
        val done = ScooterClient.subscribeCharacteristic(uuid, enable)
        return ok("Уведомления ${shortUuid(uuid)} ${if (enable) "включены" else "выключены"}: $done")
    }

    private fun notificationsJson(limit: Int): String {
        val array = JSONArray()
        for (item in ScooterClient.recentNotifications(limit)) {
            array.put(
                JSONObject()
                    .put("time_ms", item.timeMs)
                    .put("uuid", item.uuid.toString())
                    .put("hex", hex(item.data))
                    .put("ascii", ascii(item.data)),
            )
        }
        return JSONObject().put("count", array.length()).put("notifications", array).toString()
    }

    private fun scooterAction(message: String, block: () -> Unit): String {
        requireLink()
        requireReady()
        block()
        return ok(message)
    }

    private fun scooterLinkAction(message: String, block: () -> Unit): String {
        requireLink()
        if (!ScooterClient.isScooter) throw IllegalStateException("Действие только для самоката")
        block()
        return ok(message)
    }

    private fun requireLink() {
        if (!ScooterClient.hasLink) throw IllegalStateException("Нет подключения к устройству")
    }

    private fun requireReady() {
        if (!ScooterClient.isScooter) throw IllegalStateException("Действие только для самоката")
        if (!ScooterClient.isReady) throw IllegalStateException("Самокат не авторизован")
    }

    private fun opcodeCatalog(query: String?): String {
        val lower = query?.lowercase(Locale.ROOT)
        val lines = OpcodeCatalog.all.filter { info ->
            if (lower.isNullOrBlank()) {
                true
            } else {
                info.name.lowercase(Locale.ROOT).contains(lower) ||
                    info.description?.lowercase(Locale.ROOT)?.contains(lower) == true ||
                    "0x${hexByteText(info.code).lowercase(Locale.ROOT)}".contains(lower) ||
                    info.code.toString() == lower
            }
        }.map { info ->
            val description = info.description?.let { " - $it" }.orEmpty()
            "0x${hexByteText(info.code)} ${info.name} ${info.direction.name.lowercase(Locale.ROOT)}$description"
        }
        return if (lines.isEmpty()) "Ничего не найдено" else lines.joinToString("\n")
    }

    private fun logText(args: JSONObject): String {
        val limit = args.optInt("limit", 40).coerceIn(1, 200)
        val tag = stringArg(args, "tag")?.lowercase(Locale.ROOT)
        val levelText = stringArg(args, "level")?.uppercase(Locale.ROOT)
        val level = levelText?.let { value -> LogLevel.entries.firstOrNull { it.name == value } }
        val format = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
        val lines = LogBus.lines.value.filter { line ->
            (tag == null || line.tag.lowercase(Locale.ROOT).contains(tag)) &&
                (level == null || line.level == level)
        }.takeLast(limit)
        if (lines.isEmpty()) return "Журнал пуст"
        return lines.joinToString("\n") { line ->
            "${format.format(Date(line.timeMs))} ${line.level.name} ${line.tag} ${line.text}"
        }
    }

    private fun settingsJson(prefs: Prefs): JSONObject = JSONObject()
        .put("ble_key_custom", prefs.bleKey != Prefs.DEFAULT_BLE_KEY)
        .put("spam_interval_ms", prefs.spamIntervalMs)
        .put("auto_reconnect", prefs.autoReconnect)
        .put("auto_reconnect_attempts", prefs.autoReconnectAttempts)
        .put("auto_reconnect_delay_ms", prefs.autoReconnectDelayMs)
        .put("auto_resume_spam", prefs.autoResumeSpam)
        .put("show_all_devices", prefs.showAllDevices)
        .put("hide_unknown_devices", prefs.hideUnknownDevices)
        .put("device_sort", prefs.deviceSort.id)
        .put("show_scan_log", prefs.showScanLog)
        .put("auto_update", prefs.autoUpdate)
        .put("theme", prefs.theme)
        .put("ai_base_url", prefs.aiBaseUrl)
        .put("ai_model", prefs.aiModel)
        .put("ai_format", prefs.aiFormat)
        .put("ai_key_set", prefs.aiApiKey.isNotEmpty())

    private fun setSetting(prefs: Prefs, args: JSONObject): String {
        val name = stringArg(args, "name") ?: throw IllegalArgumentException("Не указано имя настройки")
        val value = valueText(args)
        if (value.isEmpty()) throw IllegalArgumentException("Пустое значение")
        when (name) {
            "ble_key" -> prefs.bleKey = value
            "spam_interval_ms" -> prefs.spamIntervalMs = value.toIntOrNull()
                ?: throw IllegalArgumentException("Нужно число")
            "auto_reconnect" -> prefs.autoReconnect = valueBool(args)
            "auto_reconnect_attempts" -> prefs.autoReconnectAttempts = value.toIntOrNull()
                ?: throw IllegalArgumentException("Нужно число")
            "auto_reconnect_delay_ms" -> prefs.autoReconnectDelayMs = value.toIntOrNull()
                ?: throw IllegalArgumentException("Нужно число")
            "auto_resume_spam" -> prefs.autoResumeSpam = valueBool(args)
            "show_all_devices" -> prefs.showAllDevices = valueBool(args)
            "hide_unknown_devices" -> prefs.hideUnknownDevices = valueBool(args)
            "device_sort" -> {
                val sort = DeviceSort.entries.firstOrNull { it.id.equals(value, true) }
                    ?: throw IllegalArgumentException("Допустимо: ${DeviceSort.entries.joinToString { it.id }}")
                prefs.deviceSort = sort
            }
            "show_scan_log" -> prefs.showScanLog = valueBool(args)
            "auto_update" -> prefs.autoUpdate = valueBool(args)
            "theme" -> {
                val theme = AppTheme.entries.firstOrNull { it.id.equals(value, true) }
                    ?: throw IllegalArgumentException("Допустимо: ${AppTheme.entries.joinToString { it.id }}")
                prefs.theme = theme.id
            }
            "ai_base_url" -> prefs.aiBaseUrl = value
            "ai_model" -> prefs.aiModel = value
            "ai_format" -> {
                val format = AiFormat.entries.firstOrNull { it.id.equals(value, true) }
                    ?: throw IllegalArgumentException("Допустимо: ${AiFormat.entries.joinToString { it.id }}")
                prefs.aiFormat = format.id
            }
            else -> throw IllegalArgumentException("Неизвестная настройка: $name")
        }
        return settingsJson(prefs).toString()
    }

    private fun appInfo(): JSONObject = JSONObject()
        .put("version_name", BuildConfig.VERSION_NAME)
        .put("version_code", BuildConfig.VERSION_CODE)
        .put("android", Build.VERSION.RELEASE)
        .put("sdk", Build.VERSION.SDK_INT)
        .put("model", Build.MODEL)
        .put("manufacturer", Build.MANUFACTURER)
        .put("ble_supported", ScooterClient.bluetoothSupported())
        .put("bluetooth_enabled", ScooterClient.bluetoothEnabled())

    private fun openScreen(context: Context, screen: String): String {
        if (screen == "ai") {
            context.startActivity(
                Intent(context, AiActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return ok("Открыт экран ai")
        }
        val tab = when (screen) {
            "devices", "control", "gatt", "log", "settings" -> screen
            else -> throw IllegalArgumentException("Неизвестный экран: $screen")
        }
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_TAB, tab),
        )
        return ok("Открыт экран $tab")
    }

    private suspend fun checkUpdate(context: Context): String {
        UpdateManager.check(context)
        delay(250)
        val deadline = System.currentTimeMillis() + 90000
        while (System.currentTimeMillis() < deadline) {
            val status = UpdateManager.status.value
            val done = status is UpdateManager.Status.Ready || status is UpdateManager.Status.Failed ||
                status is UpdateManager.Status.UpToDate || status is UpdateManager.Status.Available
            if (done) break
            delay(250)
        }
        return updateStatus().toString()
    }

    private fun installUpdate(context: Context): String {
        val status = UpdateManager.status.value
        if (status !is UpdateManager.Status.Ready) {
            return ok("Нет готового обновления: ${updateStatus().optString("state")}")
        }
        val started = UpdateManager.install(context, status)
        return "{\"ok\":$started,\"version\":\"${status.version}\"}"
    }

    private fun updateStatus(): JSONObject {
        val json = JSONObject()
        when (val status = UpdateManager.status.value) {
            UpdateManager.Status.Idle -> json.put("state", "idle")
            UpdateManager.Status.Checking -> json.put("state", "checking")
            UpdateManager.Status.UpToDate -> json.put("state", "up_to_date")
            is UpdateManager.Status.Available -> json.put("state", "available").put("version", status.version)
            is UpdateManager.Status.Downloading -> json.put("state", "downloading")
                .put("version", status.version).put("percent", status.percent)
            is UpdateManager.Status.Ready -> json.put("state", "ready").put("version", status.version)
            is UpdateManager.Status.Failed -> json.put("state", "failed").put("message", status.message)
        }
        return json
    }

    private fun uuidArg(args: JSONObject): UUID {
        val text = stringArg(args, "uuid") ?: throw IllegalArgumentException("Не указан UUID")
        return parseUuid(text)
    }

    private fun parseUuid(text: String): UUID {
        var value = text.trim()
        if (value.startsWith("0x", ignoreCase = true)) value = value.substring(2)
        val full = if (value.length <= 8 && !value.contains('-')) {
            "0000$value-0000-1000-8000-00805f9b34fb"
        } else {
            value
        }
        return UUID.fromString(full)
    }

    private fun bytesArg(args: JSONObject, key: String): ByteArray {
        val text = stringArg(args, key) ?: return ByteArray(0)
        return parseHex(text) ?: throw IllegalArgumentException("Некорректный hex")
    }

    private fun requireInt(args: JSONObject, key: String, min: Int, max: Int): Int {
        val value = intArg(args, key) ?: throw IllegalArgumentException("Не указан $key")
        if (value !in min..max) throw IllegalArgumentException("$key вне диапазона $min..$max")
        return value
    }

    private fun stringArg(args: JSONObject, key: String): String? {
        if (!args.has(key) || args.isNull(key)) return null
        return args.optString(key).trim().ifEmpty { null }
    }

    private fun intArg(args: JSONObject, key: String): Int? {
        if (!args.has(key) || args.isNull(key)) return null
        return when (val value = args.opt(key)) {
            is Number -> value.toInt()
            is String -> value.trim().let { text ->
                if (text.startsWith("0x", true)) text.substring(2).toIntOrNull(16) else text.toIntOrNull()
            }
            else -> null
        }
    }

    private fun boolArg(args: JSONObject, key: String): Boolean? {
        if (!args.has(key) || args.isNull(key)) return null
        return when (val value = args.opt(key)) {
            is Boolean -> value
            is String -> value.trim().toBooleanStrictOrNull()
            else -> null
        }
    }

    private fun valueText(args: JSONObject): String = when (val value = args.opt("value")) {
        is Boolean -> value.toString()
        is Number -> value.toString()
        is String -> value.trim()
        else -> ""
    }

    private fun valueBool(args: JSONObject): Boolean =
        (args.opt("value") as? Boolean) ?: valueText(args).toBooleanStrictOrNull()
        ?: throw IllegalArgumentException("Нужно true или false")

    private fun ascii(value: ByteArray): String {
        var end = value.size
        while (end > 0 && value[end - 1].toInt() == 0) end--
        var printable = 0
        for (i in 0 until end) {
            val b = value[i].toInt() and 0xFF
            if (b in 0x20..0x7E || b == 0x09 || b == 0x0A || b == 0x0D) printable++
        }
        if (printable < end / 2) return ""
        return String(value, 0, end, Charsets.UTF_8).trim()
    }

    private fun arguments(text: String): JSONObject =
        runCatching { JSONObject(text) }.getOrElse { JSONObject() }

    private fun ok(message: String): String = JSONObject().put("ok", true).put("message", message).toString()

    private fun hexByteText(value: Int): String =
        String.format(Locale.ROOT, "%02X", value and 0xFF)

    private fun tool(name: String, description: String, parameters: JSONObject): AiToolSpec =
        AiToolSpec(name, description, parameters)

    private fun noArgs(): JSONObject = objectParams(emptyList())

    private fun objectParams(
        required: List<String>,
        vararg properties: Pair<String, JSONObject>,
    ): JSONObject {
        val props = JSONObject()
        for ((key, value) in properties) props.put(key, value)
        return JSONObject()
            .put("type", "object")
            .put("properties", props)
            .put("required", JSONArray(required))
            .put("additionalProperties", false)
    }

    private fun stringProp(description: String): JSONObject =
        JSONObject().put("type", "string").put("description", description)

    private fun intProp(description: String, min: Int, max: Int): JSONObject =
        JSONObject().put("type", "integer").put("description", description)
            .put("minimum", min).put("maximum", max)

    private fun boolProp(description: String): JSONObject =
        JSONObject().put("type", "boolean").put("description", description)

    private fun enumProp(description: String, vararg values: String): JSONObject =
        JSONObject().put("type", "string").put("description", description)
            .put("enum", JSONArray(values.toList()))
}
