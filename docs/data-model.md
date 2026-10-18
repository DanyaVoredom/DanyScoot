# Модель данных

## 1. Хранение

Приложение использует только SharedPreferences. Базы данных, файлов журнала и сетевого хранилища нет.

| Хранилище | Имя | Назначение |
| --- | --- | --- |
| SharedPreferences | danyurent_prefs | все настройки и последнее устройство |
| Память | LogBus | до 1000 строк журнала |
| Память | StateFlow | состояние клиента, список устройств, телеметрия |
| Система | SpamService notification | отображение активного спама |

Файл SharedPreferences находится в приватном каталоге приложения и доступен через root или backup. android:allowBackup=false, поэтому обычный backup не копирует настройки.

## 2. Ключи настроек

| Поле Prefs | Ключ | Тип | По умолчанию | Диапазон |
| --- | --- | --- | --- | --- |
| bleKey | ble_key | String | 4BKNwi77 | непустая строка |
| spamIntervalMs | unlock_spam_interval_ms | Int | 100 | 20..10000 |
| autoReconnect | auto_reconnect_enabled | Boolean | true | - |
| autoReconnectAttempts | auto_reconnect_max_attempts | Int | 3 | 0..20 |
| autoReconnectDelayMs | auto_reconnect_delay_ms | Int | 5000 | 500..60000 |
| autoResumeSpam | auto_resume_spam | Boolean | false | - |
| showAllDevices | show_all_scan_devices | Boolean | true | - |
| showScanLog | show_scan_debug_log | Boolean | false | - |
| demoMode | use_demo | Boolean | false | - |
| showAllOpcodes | show_all_opcodes | Boolean | true | - |
| theme | theme | String | dark | black, white, light, dark, amoled |
| lastDeviceAddress | last_device_address | String | пусто | MAC или DEMO |
| lastDeviceName | last_device_name | String | пусто | произвольная строка |

Ключи названы так же, как в LostBot, где это возможно. Это упрощает сравнение поведения, но не означает совместимость хранилищ.

## 3. Состояния клиента

ScooterClient публикует следующие StateFlow:

| Поле | Тип | Смысл |
| --- | --- | --- |
| state | ClientState | текущая фаза соединения |
| devices | List<ScannedDevice> | найденные устройства |
| vehicle | VehicleInfo? | телеметрия 0x60 или 0x63 |
| iot | IotInfo? | данные 0x31 |
| spamActive | Boolean | спам запущен |
| spamSent | Int | число отправленных кадров |
| lastError | String | последняя ошибка |

Дополнительные поля:

- sessionKey: Int, текущий сессионный ключ;
- deviceName: String, имя подключённого устройства;
- deviceAddress: String, адрес;
- isDemo: Boolean;
- isReady: Boolean;
- spamWanted: Boolean.

## 4. Модели

ScannedDevice:

- address;
- name;
- rssi;
- hasUart.

IotInfo:

- value: u16, обычно напряжение в сотых вольта;
- flags: u8;
- locked: Boolean, инвертированный бит 0;
- version: строка major.minor.patch;
- text: текстовая строка, если ответ ASCII.

VehicleInfo:

- batteryPercent;
- mode;
- speed в км/ч;
- singleMileage в метрах;
- remainingRange в метрах.

RealtimeInfo:

- batteryPercent;
- speed в км/ч;
- current в амперах;
- flags.

RfidPair:

- first;
- second.

ScooterFrame:

- cmd;
- key;
- rand;
- payload;
- raw.

LogLine:

- timeMs;
- level;
- tag;
- text.

## 5. Журнал

LogBus хранит до 1000 строк. При переполнении удаляются самые старые. Каждое изменение публикует новый список через StateFlow, поэтому RecyclerView обновляется автоматически.

Уровни: INFO, TX, RX, OK, WARN, ERR. Журнал не фильтруется по уровню на уровне шины, фильтрация выполняется в LogFragment.

Журнал исчезает при завершении процесса. Кнопка очистки доступна в LogFragment.

## 6. Жизненный цикл данных

- Настройки сохраняются сразу при нажатии кнопки сохранения.
- Интервал спама сохраняется при запуске спама.
- Последнее устройство сохраняется при подключении.
- sessionKey живёт только в памяти и сбрасывается при разрыве.
- Телеметрия живёт только в памяти и очищается при отключении или старте демо.
- Список устройств очищается перед новым сканированием.
- Журнал переживает смену вкладок, но не перезапуск приложения.

## 7. Приватность

В SharedPreferences хранятся:

- BLE-ключ в открытом виде;
- последний MAC-адрес самоката;
- имя устройства.

В журнале могут оказаться:

- IMEI или серийный номер из ответа 0x31;
- идентификаторы RFID;
- полные hex-кадры, включая зашифрованный ключ;
- названия и адреса окружающих BLE-устройств.

Приложение не отправляет эти данные в сеть. Риск возникает при демонстрации экрана, снятии скриншота, подключении отладчика или создании backup. Подробнее: docs/security.md.

## 8. Миграции

Версия схемы не хранится. При изменении ключей или форматов нужно:

1. Прочитать старое значение.
2. Проверить его допустимость.
3. Записать новое значение.
4. Не удалять старые ключи без необходимости.

Текущая версия 1.0.0 не имеет миграций, потому что это первый публичный формат.
