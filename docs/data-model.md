# Модель данных

## 1. Хранение

Приложение использует SharedPreferences и кэш APK обновления. Базы данных, файлов журнала и сетевого хранилища нет.

| Хранилище | Имя | Назначение |
| --- | --- | --- |
| SharedPreferences | danyscoot_prefs | все настройки и последнее устройство |
| Память | LogBus | до 1000 строк журнала |
| Память | StateFlow | состояние клиента, список устройств, GATT-дамп, телеметрия |
| Кэш | cacheDir/updates | скачанный APK обновления |
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
| hideUnknownDevices | hide_unknown_devices | Boolean | true | - |
| deviceSort | device_sort | String | distance | distance, name, address, kind |
| theme | theme | String | dark | black, white, light, dark, amoled |
| lastDeviceAddress | last_device_address | String | пусто | MAC |
| lastDeviceName | last_device_name | String | пусто | произвольная строка |
| lastDeviceKind | last_device_kind | String | SCOOTER | SCOOTER, HEADSET, OTHER |
| autoUpdate | auto_update_enabled | Boolean | true | - |
| lastUpdateCheck | last_update_check | Long | 0 | время в мс |
| aiBaseUrl | ai_base_url | String | https://api.openai.com/v1 | URL |
| aiApiKey | ai_api_key | String | пусто | ключ провайдера |
| aiModel | ai_model | String | gpt-4o-mini | имя модели |
| aiFormat | ai_format | String | auto | auto, openai_chat, openai_responses, anthropic |

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
| gatt | GattDump? | сервисы и характеристики последнего подключения |
| remote | RemoteInfo? | данные Device Information и батарея внешнего устройства |
| notifications | List<BleNotification> | последние уведомления внешнего устройства |
| incompatible | Set<String> | адреса без подходящего профиля |

Дополнительные поля:

- sessionKey: Int, текущий сессионный ключ;
- deviceName: String, имя подключённого устройства;
- deviceAddress: String, адрес;
- activeKind: DeviceKind, тип активного устройства;
- isScooter: Boolean;
- isHeadset: Boolean;
- isReady: Boolean;
- hasLink: Boolean;
- spamWanted: Boolean.

## 4. Модели

ScannedDevice:

- address;
- name;
- rssi;
- hasUart;
- advert: строка с service UUID и manufacturer data из рекламы;
- hasEdifier;
- kind: SCOOTER, HEADSET или OTHER;
- distanceMeters: примерное расстояние по RSSI;
- reachable: найден известный профиль.

RemoteInfo:

- name;
- batteryPercent;
- manufacturer;
- model;
- serial;
- firmware;
- hardware;
- software;
- txPower;
- lastUuid и lastHex последнего уведомления.

BleNotification:

- timeMs;
- uuid;
- data.

GattDump:

- address;
- name;
- services: список GattServiceInfo с UUID и characteristics.

GattServiceInfo:

- uuid;
- name;
- characteristics: список GattCharacteristicInfo.

GattCharacteristicInfo:

- uuid;
- properties;
- writable;
- notifiable.

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

- Настройки сохраняются автоматически при каждом изменении.
- Интервал спама сохраняется при вводе и при запуске спама.
- Последнее устройство сохраняется при подключении.
- sessionKey живёт только в памяти и сбрасывается при разрыве.
- GATT-дамп переживает отключение и заменяется при новом discovery.
- Телеметрия и данные внешнего устройства живут только в памяти и очищаются при отключении.
- Список устройств очищается перед новым сканированием.
- Журнал переживает смену вкладок, но не перезапуск приложения.
- Время последней проверки обновлений хранится в prefs, скачанный APK в кэше.

## 7. Приватность

В SharedPreferences хранятся:

- BLE-ключ в открытом виде;
- последний MAC-адрес устройства;
- имя и тип устройства;
- base URL, модель и API-ключ AI-провайдера в открытом виде.

В журнале могут оказаться:

- IMEI или серийный номер из ответа 0x31;
- идентификаторы RFID;
- полные hex-кадры, включая зашифрованный ключ;
- названия и адреса окружающих BLE-устройств.

Приложение не отправляет журнал и телеметрию в сеть само. Данные уходят только в AI-сервер, если пользователь отправил сообщение: текст запроса, история чата и результаты вызванных инструментов, включая состояние устройства и журнал. Запрос релиза Codeberg не содержит пользовательских данных. Риск возникает при демонстрации экрана, снятии скриншота, подключении отладчика или создании backup. Подробнее: docs/security.md.

## 8. Миграции

Версия схемы не хранится. При изменении ключей или форматов нужно:

1. Прочитать старое значение.
2. Проверить его допустимость.
3. Записать новое значение.
4. Не удалять старые ключи без необходимости.

Текущая версия хранилища 1.5.0 не имеет миграций: ключи 1.1.0 совместимы, удалённые настройки игнорируются, а прежнее хранилище danyurent_prefs переносится при первом запуске.
