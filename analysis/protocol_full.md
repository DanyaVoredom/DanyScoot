# Полный протокол BLE по референсам LostBot, FishBot, NineHack, Urent

Документ собран по smali и jadx-декомпиляции references/LostBot.apk, references/FishBot.apk, references/NineHack.apk, а также по официальным ru.urentbike.app 1.90.2 и 2.7.0. Факты без ссылки на источник являются вычисленными тест-векторами, а не литералами из дампа.

## 1. Транспорт

### 1.1 GATT

| Роль | UUID |
| --- | --- |
| Сервис Nordic UART | 6e400001-b5a3-f393-e0a9-e50e24dcca9e |
| Write characteristic, кадры от телефона | 6e400002-b5a3-f393-e0a9-e50e24dcca9e |
| Notify characteristic, кадры от самоката | 6e400003-b5a3-f393-e0a9-e50e24dcca9e |
| CCCD | 00002902-0000-1000-8000-00805f9b34fb |

Сканирование: LostBot фильтрует по сервисному UUID, если show_all_scan_devices=false; NineHack сканирует без фильтра и показывает все BLE-устройства. Подключение: connectGatt с TRANSPORT_LE, без pairing и bonding. Ни один референс не вызывает createBond.

### 1.2 LostBot

- MTU: requestMtu(247), таймаут 3000 мс.
- Запись: чанк max(20, MTU-3), при MTU 23 это 20 байт. Тип WRITE_TYPE_DEFAULT, если у характеристики есть PROPERTY_WRITE, иначе WRITE_TYPE_NO_RESPONSE.
- Все операции write, discoverServices, requestMtu, descriptor write идут через одну последовательную очередь.
- CCCD: setCharacteristicNotification(true) и запись ENABLE_NOTIFICATION_VALUE. Для API 33+ отдельный вызов writeDescriptor с проверкой кода.
- Watchdog подключения 20000 мс.
- При status 133 (GATT_ERROR) отложенный disconnect/close через 150 мс.
- Состояния линка: DISCONNECTED, SCANNING, CONNECTING, CONNECTED, AUTHENTICATED, ERROR.
- Повторное рукопожатие возможно, кнопка Re-handshake.

### 1.3 NineHack

- MTU не запрашивается, onMtuChanged отсутствует.
- Запись одним writeCharacteristic, WRITE_TYPE_DEFAULT, без чанкинга.
- CCCD включается через readDescriptor, затем setCharacteristicNotification и writeDescriptor ENABLE_NOTIFICATION_VALUE.
- Рукопожатие стартует только из onDescriptorWrite.
- Переподключение только при status != 0, максимум 3 раза за жизнь Activity, счетчик не сбрасывается.
- Чистый разрыв status=0, newState=0 не переподключает.

### 1.4 Практические отличия

| Свойство | LostBot | NineHack |
| --- | --- | --- |
| MTU | 247 | не запрашивает |
| Чанкинг | да, max(20, MTU-3) | нет |
| Тип записи | 1 или 2 по PROPERTY_WRITE | WRITE_TYPE_DEFAULT |
| Очередь операций | да | нет |
| Проверка CRC входящего кадра | да | нет |
| Проверка длины входящего кадра | да | нет |
| Таймаут рукопожатия | 3000 мс | нет |
| Автопереподключение | по prefs, до 20 попыток | только при ошибке, до 3 |
| Фильтр сканирования | по сервисному UUID | нет |

## 2. Кадр

### 2.1 Раскладка

| Смещение | Поле | Формула | Примечание |
| --- | --- | --- | --- |
| 0 | header | A3 | константа |
| 1 | header | A4 | константа |
| 2 | len | длина payload | 0..255 |
| 3 | randWire | (rand + 50) & 255 | +0x32 |
| 4 | key | key ^ rand | sessionKey |
| 5 | cmd | cmd ^ rand | опкод |
| 6..6+len-1 | data | payload[i] ^ rand | payload |
| 6+len | crc8 | CRC-8/MAXIM | по байтам 0..6+len-1 |

Длина всего кадра 7 + len. Максимальный payload 255, максимальный кадр 262 байта, поэтому LostBot режет его на BLE-чанки. NineHack такие кадры не строит.

### 2.2 Rand

- LostBot: rand = (nextInt >>> 1) % 255, диапазон 0..254.
- NineHack: rand = Random.nextInt(254) + 1, диапазон 1..254.
- В эфир идет rand + 50, а XOR выполняется исходным rand. Это главная ловушка при разборе.
- При rand больше 205 сумма переполняет байт, поэтому декодер обязан брать (randWire - 50) & 255.
- При rand=0 проводной байт 3 равен 50, а key и cmd не маскируются.

### 2.3 CRC8

CRC-8/MAXIM:

- polynomial 0x31, отраженный;
- init 0x00;
- refin true, refout true;
- xorout 0x00;
- check "123456789" = 0xA1;
- пустой ввод = 0x00.

Битовая реализация:

    crc = 0
    для каждого байта b:
        crc = crc ^ b
        8 раз:
            если crc & 1: crc = (crc >> 1) ^ 0x8C
            иначе: crc = crc >> 1

Табличная реализация LostBot: 256 значений, первые 00 5E BC E2 61 3F DD 83. Табличная реализация NineHack: 256 значений, первые 00 31 62 53. Обе дают одинаковый результат; разные таблицы отражают разный порядок отражения.

### 2.4 Валидация

LostBot A2/f.d:

1. Длина не меньше 7, иначе Frame too short.
2. Header A3 A4, иначе Invalid frame header.
3. Ожидаемая длина 7 + b[2], иначе Invalid frame length. expected=N got=M.
4. CRC8 по всем байтам кроме последнего, иначе CRC mismatch expected=XX got=YY.
5. rand = (b[3] - 50) & 255, key = b[4] ^ rand, cmd = b[5] ^ rand, payload[i] = b[6+i] ^ rand.

NineHack interceptMessage:

1. Проверяет только header A3 A4.
2. Берет len = msg[2], rand = (msg[3] - 0x32), cmd = msg[5] ^ rand, payload[i] = msg[6+i] ^ rand.
3. CRC не проверяет, длину не проверяет. При коротком массиве возможен выход за границы.
4. Неизвестный cmd молча игнорирует.

### 2.5 Тест-векторы

Все векторы ниже вычислены независимой реализацией CRC8 и сборки кадра, затем сверены с формулами A2/f.java и CommandCreator.smali.

CRC:

| Вход | CRC8 |
| --- | --- |
| пусто | 00 |
| "123456789" | A1 |

Кадры при rand=0x01, key=0x00:

| Команда | Payload | Кадр hex |
| --- | --- | --- |
| handshake 4BKNwi77 | 34 42 4B 4E 77 69 37 37 | A3 A4 08 33 01 00 35 43 4A 4F 76 68 36 36 E7 |
| handshake w4S3Hqn5 | 77 34 53 33 48 71 6E 35 | A3 A4 08 33 01 00 76 35 52 32 49 70 6F 34 89 |
| lock 0x15 | 01 | A3 A4 01 33 01 14 00 CE |
| unlock 0x05, десять 01 | 01 x10 | A3 A4 0A 33 01 04 00 00 00 00 00 00 00 00 00 00 31 |
| set 0x61, light 2, mode 2, throttle 2 | 02 02 02 00 | A3 A4 04 33 01 60 03 03 03 01 E4 |
| eject 0x81 | 05 | A3 A4 01 33 01 80 04 57 |
| find car 0xA2 | 01 | A3 A4 01 33 01 A3 00 A2 |
| find car 0xA2 | 06 | A3 A4 01 33 01 A3 07 21 |
| device info 0x31 | 01 | A3 A4 01 33 01 30 00 34 |
| scooter info 0x60 | 01 | A3 A4 01 33 01 61 00 87 |
| realtime 0x63 | 01 | A3 A4 01 33 01 62 00 D2 |
| read RFID 0x85 | 01 | A3 A4 01 33 01 84 00 0D |
| color 0x82, mode 1, RGB 7C 6A E8 | 01 00 7C 6A E8 00 00 00 | A3 A4 08 33 01 83 00 01 7D 6B E9 01 01 01 09 |

Ответы рукопожатия при rand=0x01, key=0x00:

| Payload | Смысл | Кадр hex |
| --- | --- | --- |
| 01 00 | статус 1, sessionKey 0 | A3 A4 02 33 01 00 00 01 06 |
| 01 01 | статус 1, sessionKey 1 | A3 A4 02 33 01 00 00 00 58 |
| 01 42 | статус 1, sessionKey 0x42 | A3 A4 02 33 01 00 00 43 FC |

Кадры с sessionKey 0x42 и rand=0x10:

| Команда | Payload | Кадр hex |
| --- | --- | --- |
| lock 0x15 | 01 | A3 A4 01 42 52 05 11 9D |
| lock 0x15, key=0 | 01 | A3 A4 01 42 10 05 11 E3 |
| spam 0x05, t=1700000000 | 01 31 31 32 39 65 53 F1 00 00 | A3 A4 0A 42 52 15 11 21 21 22 29 75 43 E1 10 10 F9 |
| spam 0x05, key=0, t=1700000000 | 01 31 31 32 39 65 53 F1 00 00 | A3 A4 0A 42 10 15 11 21 21 22 29 75 43 E1 10 10 5A |
| transmission START 0xFB | 00 00 01 4B 37 07 + w4S3Hqn5 | A3 A4 0E 42 52 EB 10 10 11 5B 27 17 67 24 43 23 58 61 7E 25 DE |

## 3. Рукопожатие

### 3.1 Ключи

| Ключ | Источник | Тип |
| --- | --- | --- |
| 4BKNwi77 | NineHack strings.xml control_defaultBleKey | 8 ASCII |
| w4S3Hqn5 | LostBot z2/l.java, D2/C0141b.java case 27 | 8 ASCII |
| DEMOKEY | LostBot demo firmware key | строка |
| 66 | LostBot demo session key | число |

LostBot получает w4S3Hqn5 не из ID самоката. Массивы:

    a = 2C CC 70 F4 76 B1 33 F1
    b = 5B E7 1D 9A 42
    key[i] = (b[i % 5] ^ a[i]) ^ ((i * 31) & 255)

для i=0..7 дает 77 34 53 33 48 71 6E 35, то есть w4S3Hqn5. Это обфускация константы.

NineHack хранит ключ в SharedPreferences ninehack_settings/ble_key, по умолчанию 4BKNwi77, в открытом виде. LostBot хранит ble_saved_keys и ble_selected_key, но auth использует C0141b(27), то есть константу. Пользовательский магазин ключей в LostBot 1.2.1 не подключен к рукопожатию.

### 3.2 Процедура

1. Отправить cmd 0x01, payload = ASCII-байты ключа, key=0.
2. Ждать кадр cmd 0x01.
3. Если payload.length >= 2 и payload[0] == 1, то sessionKey = payload[1], статус AUTHENTICATED.
4. Иначе ключ неверный.

Особенности:

- LostBot таймаут 3000 мс, ошибки Handshake timed out, Handshake failed payload=..., Handshake failed: ....
- LostBot проверяет пустой ключ и пишет BLE key is empty.
- NineHack таймаута и повторов не имеет.
- Session key используется как key в последующих кадрах, а не как AES-ключ.
- Описание COMMUNICATION_KEY в каталоге LostBot содержит слова session AES key exchange. Это неточность: AES в протоколе нет.

### 3.3 Session key 0

Session key может быть 0. Ответ 01 00 означает статус 1 и ключ 0. NineHack явно обрабатывает data[1]=0, LostBot формула key ^ rand допускает 0. Приложение не должно считать 0 признаком отсутствия авторизации. Нужен отдельный флаг authenticated.

### 3.4 Ответы на другие команды

Успех зависит от команды:

- 0x05 unlock: NineHack ждет payload[0] == 1. LostBot h() считает 2 подтверждением, 0 успехом, 1 ошибкой. На практике встречаются коды 1 и 2.
- 0x15 lock: то же.
- 0x61 set: LostBot g() считает payload[0] == 0 успехом, иначе FAIL.
- 0x81 eject: NineHack ждет 1, LostBot g() ждет 0.
- 0x10 ERROR: payload[0] = отвергнутая команда, payload[1] = причина. NineHack отдельно трактует payload[0] == 1 как BUG CRC Bad.
- Пустой payload в g() означает ACK без статуса.

## 4. Каталог опкодов

Источник: LostBot A2/c.java, LinkedHashMap f299a. Направление: BIDIR - двусторонний, SET - только от телефона, NOTIF - только от самоката.

| Код | Имя | Направление | Описание |
| --- | --- | --- | --- |
| 0x01 | COMMUNICATION_KEY | BIDIR | Handshake / session key exchange |
| 0x05 | UNLOCK | BIDIR | |
| 0x10 | ERROR | NOTIF | Scooter rejected previous command |
| 0x15 | LOCK | BIDIR | |
| 0x31 | DEVICE_INFO | BIDIR | |
| 0x34 | HELMET_UNLOCK / MQTT_ACCOUNT | SET | |
| 0x35 | MQTT_PASSWORD | SET | |
| 0x37 | INSTALLATION_MODE | SET | |
| 0x3D | AUTO_LOCK_MODE | SET | |
| 0x40 | STEERING_MODE | SET | |
| 0x50 | DISABLE_GSM_COMMUNICATION | SET | |
| 0x51 | SCOOTER_OLD_DATA | BIDIR | |
| 0x52 | CLEAR_DATA | SET | |
| 0x53 | GSM_AUTO_DETECT | SET | |
| 0x54 | CLEAR_BT_PAIRED_USERS | SET | |
| 0x55 | GPS_TRACKING_SETTINGS | SET | |
| 0x56 | METER_CONFIG | SET | |
| 0x60 | SCOOTER_INFO | BIDIR | |
| 0x61 | SCOOTER_SET | SET | |
| 0x62 | SCOOTER_CONFIG | SET | |
| 0x63 | VEHICLE_REALTIME_INFO | BIDIR | |
| 0x64 | SET_WHEEL_DIAMETER | SET | |
| 0x72 | CALIBRATE_INSTRUMENT_TIME | SET | |
| 0x73 | METER_RESET | SET | |
| 0x74 | NAVIGATION | SET | |
| 0x75 | AUDIO_TOGGLE | SET | |
| 0x76 | START_TESTING | SET | |
| 0x77 | CLEAR_MILEAGE | SET | |
| 0x78 | XM_FIND_CAR | SET | |
| 0x79 | AUTOMATIC_SCREEN_OFF | SET | |
| 0x81 | SCOOTER_BATTERY / OUT_LOCK | BIDIR | |
| 0x82 | SCOOTER_COLOR_LIGHT | SET | |
| 0x85 | READ_RFID_CARD | BIDIR | |
| 0x86 | DELETE_RFID | SET | |
| 0x90 | XM_POWER_CONTROL | SET | |
| 0x91 | POWER_CONTROL | SET | |
| 0x92 | IOT_RESTART | SET | |
| 0x95 | CHAIN_LOCK_PASSWORD_ENTRY | SET | |
| 0x96 | CHAIN_LOCK_PASSWORD_DELETE | SET | |
| 0x97 | CHAIN_LOCK_GET_USER_PASSWORD | BIDIR | |
| 0xA0 | VEHICLE_COMMANDS | BIDIR | |
| 0xA1 | QR_CODE_READ_WRITE | BIDIR | |
| 0xA2 | FIND_CAR (sound) | SET | |
| 0xC0 | BATTERY_UNLOCK | SET | |
| 0xC3 | ACCESSORY_CONTROL | SET | Saddle / tail-box / helmet open or close |
| 0xC4 | UPDATE_CERTIFICATE | SET | |
| 0xC5 | SWITCH_SIM | SET | |
| 0xC6 | SWITCH_NEXT_SIM | SET | |
| 0xC7 | BATTERY/LIGHT or LOG_DATA | BIDIR | |
| 0xDC | AVAS_SWITCH | SET | |
| 0xDE | AVAS_SWITCH_2 | SET | |
| 0xFA | FW_DEVICE_INFO | BIDIR | |
| 0xFB | FW_DEVICE_DATA | BIDIR | |
| 0xFC | FW_GET_FIRMWARE_DATA | BIDIR | |

## 5. Команды от телефона

Наблюдаемые payload. Пустая ячейка означает, что билдер в дампе не найден, а не то, что команда не поддерживается.

| Код | Payload | Смысл | Источник |
| --- | --- | --- | --- |
| 0x01 | ASCII ключа | Рукопожатие | LostBot, NineHack |
| 0x05 | 01 + 31 31 32 39 + t4 + 00 | Спам-разблокировка, t = UNIX-секунды big-endian | LostBot |
| 0x05 | 01 x10 | Разблокировка | NineHack |
| 0x15 | 01 | Блокировка | LostBot, NineHack |
| 0x31 | 01 | Запрос device info / IoT config | LostBot |
| 0x34 | ASCII account | MQTT account / helmet unlock | LostBot service |
| 0x35 | ASCII password | MQTT password | LostBot service |
| 0x50 | 00 или 01 | GSM disable/enable | LostBot decoder |
| 0x54 | 01 | Очистить BLE-пары | LostBot service |
| 0x60 | 01 | Запрос телеметрии | LostBot |
| 0x61 | light, mode, throttle, 00 | Настройки: light 1 off, 2 on, 0 keep; mode 0 keep, 1 low, 2 med, 3 high | LostBot, NineHack |
| 0x63 | 01 | Запрос realtime | LostBot |
| 0x64 | hi, lo | Диаметр колеса, 100..400 мм | Декодер A2/c.java:304, сборщик не найден |
| 0x73 | 01 01 | Сброс счетчика | LostBot service |
| 0x78 | | Найти самокат, сборщик не найден; в LostBot используется 0xA2 | Каталог |
| 0x81 | 05 | Извлечь батарею | LostBot, NineHack |
| 0x82 | mode, 00, R, G, B, 00, 00, 00 | Цветной свет | LostBot |
| 0x85 | 01 | Прочитать RFID | LostBot |
| 0x86 | id1 u32 BE + id2 u32 BE | Удалить два RFID | LostBot |
| 0x91 | 01 | Выключение питания | LostBot service |
| 0x92 | 01 | Перезапуск IoT | LostBot |
| 0xA1 | tag + ASCII | QR read/write | Декодер A2/c.java:366, сборщик не найден |
| 0xA2 | 01, 02, 03 или 06 | Найти самокат звуком | LostBot |
| 0xC0 | 01 | Открыть крышку батареи | LostBot |
| 0xC3 | type, action | type 1 saddle, 3 tail-box, 8 helmet; action 1 open, 2 close | Декодер A2/c.java:369-371, сборщик не найден |
| 0xC4 | 01 | Обновить сертификат | LostBot |
| 0xC5 | slot | Переключить SIM | LostBot service |
| 0xC6 | 01 | Следующая SIM | LostBot |
| 0xDC | ASCII *HBCS,NB,IMEI,V2,1,,,,,# + 00, где 1 или 2 | AVAS | LostBot I1/a.java:737-745 |
| 0xDE | | AVAS2, билдер в дампе не найден | Каталог |
| 0xFA | пусто | Запрос firmware info / IoT read | LostBot |
| 0xFB | kind, total hi, total lo, crc hi, crc lo, devType, key | START прошивки | LostBot |
| 0xFC | idx hi, idx lo, devType | Запрос чанка конфига | LostBot |

Замечания:

- 0x31 и 0x60/0x63/0x85 требуют key = sessionKey, иначе самокат отвергнет команду.
- 0x61 в LostBot отправляется как [2,0,0,0] или [1,0,0,0], то есть меняет только свет, режим keep. NineHack отправляет все три поля.
- 0x78 в каталоге называется XM_FIND_CAR, но сборщика нет; LostBot ищет самокат через 0xA2 FIND_CAR (sound).
- 0xDC payload заканчивается решеткой и нулем.

## 6. Ответы и телеметрия

| Код | Условие | Разбор |
| --- | --- | --- |
| 0x01 | len >= 2 | handshake ACK, sessionKey = payload[1] |
| 0x05 | payload[0] == 2 или пусто | unlock ACK; 0 тоже успех в LostBot; 1 ошибка |
| 0x10 | пусто | ERROR без деталей |
| 0x10 | иначе | ERROR: rejected cmd=0xNN reason=0xNN |
| 0x31 | есть 0x3A и payload непустой | iot network: печатная строка, нули удалены |
| 0x31 | len < 7 | device info (short) |
| 0x31 | len >= 7 | voltage = u16 BE / 100 В; flags = b2; locked = (flags & 1) == 0; fw = b4.b5.b6 |
| 0x34 | пусто | mqtt account ACK |
| 0x35 | пусто | mqtt password ACK |
| 0x50 | b0 == 0 | gsm DISABLE, иначе ENABLE |
| 0x53 | b0 | gsm auto-detect = b0 |
| 0x54 | b0 | clear paired users OK/FAIL |
| 0x55 | len >= 5 | gps tracking: flag=b0, i1=u16 LE b1..b2, i2=u16 LE b3..b4 |
| 0x56 | | meter config |
| 0x60 | len < 8 | info (short) |
| 0x60 | len >= 8 | battery=b0 %, mode=b1, speed=u16 BE /10 км/ч, trip=u16 BE x10 /1000 км, range=u16 BE x10 /1000 км |
| 0x61 | b0 == 0 | set OK, иначе FAIL status |
| 0x62 | | config |
| 0x63 | len < 6 | realtime (short) |
| 0x63 | len >= 6 | battery=b0 %, speed=u16 BE /10, current=u16 BE /100 А, flags=b5 |
| 0x64 | len >= 2 | set wheel diameter = u16 BE |
| 0x72 | len >= 5 | epoch = u32 BE, tz = signed b4 часы |
| 0x73 | len >= 2 | meter reset b2=b0 b3=b1 |
| 0x74 | | navigation |
| 0x75 | b0 == 255 | audio OFF (b1), иначе audio ON (b0) |
| 0x76 | b0 == 0 | start_testing EXIT, иначе ENTER |
| 0x77 | b0 | clear mileage = b0 |
| 0x78 | b0 | xm find car = b0 |
| 0x79 | b0 | auto screen off = b0 |
| 0x81 | len < 2 | battery/out_lock (short) |
| 0x81 | len >= 2 | tag=b0, status=b1 |
| 0x82 | len < 5 | color light |
| 0x82 | len >= 5 | mode=b0, rgb=#b2b3b4 |
| 0x85 | len < 4 | read RFID (raw) |
| 0x85 | len >= 4 | RFID id = u32 BE, hex |
| 0x86 | | delete RFID |
| 0x90 | b0 | xm power = b0 |
| 0x91 | b0 == 1 | power OFF (shutdown) |
| 0x92 | | iot restart |
| 0xA0 | | vehicle reply, raw hex |
| 0xA1 | | qr reply, ascii |
| 0xA2 | | find car (play sound) |
| 0xC0 | | battery cover |
| 0xC3 | len < 2 | accessory control |
| 0xC3 | len >= 2 | accessory: saddle/tail-box/helmet open/close |
| 0xC4 | | update certificate |
| 0xC5 | пусто | switch SIM |
| 0xC5 | b0 | switch SIM slot=b0 |
| 0xC6 | | switch next SIM |
| 0xDC | | AVAS |
| 0xDE | | AVAS2 |
| 0xFA | | firmware info, ascii |
| 0xFB | b0 == 0 | transmission: password error |
| 0xFB | b0 == 1 | transmission: READY |
| 0xFB | b0 == 2 | transmission: COMPLETE |
| 0xFB | b0 == 3 | transmission: timeout |
| 0xFB | b0 == 4 | transmission: total CRC error |
| 0xFB | иначе | transmission status=0xNN |
| 0xFC | len >= 2 | pack request idx = u16 BE |
| 0xFC | len < 2 | firmware data chunk, len |

Аномалия 0x31: функция A2/c.e() задумана как проверка печатности, но для непустого массива всегда возвращает true. Поэтому любой бинарный device info, содержащий байт 0x3A, будет ошибочно показан как iot network. Это не влияет на сам протокол, но влияет на декодер.

## 7. Прошивка и transmission

CRC16: init 0xFFFF, отраженный полином 0xA001, без финального XOR. Check "123456789" = 0x4B37.

START, cmd 0xFB:

| Смещение | Поле | Значение |
| --- | --- | --- |
| 0 | kind | 0 OTA_UPDATE, 1 SYSTEM_CONFIG |
| 1..2 | totalPack | u16 BE, число 16-байтных пакетов |
| 3..4 | crc16 | u16 BE, CRC16 всего образа |
| 5 | deviceType | 0..255, prefs transmission_device_type |
| 6.. | key | байты prefs transmission_update_key, могут быть пустыми |

Образ дополняется нулями до кратности 16. CRC16 считается по всему дополненному образу. Число пакетов totalPack = размер образа / 16.

Пакет: buf = [idx hi, idx lo] + 16 байт данных. CRC16 по 18 байтам. В эфир идет 20 байт: buf + crc hi + crc lo.

Запрос пакета: самокат отправляет cmd 0xFC с payload [idx hi, idx lo]. Телефон отвечает пакетом. Для чтения конфига IoT телефон сам отправляет cmd 0xFC [idx hi, idx lo, devType].

Статусы 0xFB: 0 ошибка пароля/ключа, 1 READY, 2 COMPLETE, 3 timeout, 4 total CRC error.

Таймаут чтения конфига 5000 мс, ошибка Read config failed: timeout.

Демо: задержка 300 мс, затем пакет каждые 250 мс, после последнего COMPLETE.

## 8. Спам-разблокировка

Кадр: cmd 0x05, payload:

| Смещение | Байт | Значение |
| --- | --- | --- |
| 0 | 01 | константа |
| 1..4 | 31 31 32 39 | ASCII "1129" |
| 5..8 | t>>24, t>>16, t>>8, t | UNIX-секунды, big-endian |
| 9 | 00 | терминатор |

Интервал LostBot по умолчанию 500 мс, диапазон 2..3000 мс. Цикл: delay(interval) -> tick -> повтор. Защита от реэнтрантности. При реконнекте и auto_resume_spam спам возобновляется после успешного рукопожатия.

Foreground service LostBot: канал lostbot_spam, id 42, importance 2, тип connectedDevice, action остановки ru.lostbotapp.action.STOP_SPAM, заголовок Unlock spam active, текст <Scooter> · N sent · every M ms.

Демо: спам не стартует сервис, а снимает лок.

## 9. Демо-режим

LostBot demo controller:

- sessionKey = 66;
- state AUTHENTICATED;
- IoT: 5230, flags 1, version 1.2.3, locked false;
- telemetry: battery 69, mode 2, speed 18.7, trip 1240, range 15300;
- deviceType 7;
- firmware key DEMOKEY.

Автоответы:

| Команда | Ответ |
| --- | --- |
| 0x05, 0x15 | [1] |
| 0x31 | [voltage hi, voltage lo, locked ? 0 : 1, 0, maj, min, patch] |
| 0x60 | [battery, mode, speed*10 hi/lo, trip/10 hi/lo, range/10 hi/lo] |
| 0x85 | [18, 52, 86, 120] |
| 0xFA | ASCII "FW 1.2.3 demo" |
| прочее | [1] |

Демо IoT-сеть: IMEI 860123456789012, ICCID 8901234567890123456, internet, 52.28.100.10, 1883, version 1.2.3.

## 10. Аномалии референсов

- LostBot называет рукопожатие session AES key exchange, но AES не использует. Это XOR с одним байтом.
- XDDbot wiki трактует payload[0] ответа рукопожатия как deviceType. LostBot и NineHack трактуют payload[0] как статус 1, payload[1] как sessionKey. Приоритет у кода APK.
- LostBot хранит ble_saved_keys и ble_selected_key, но auth берет константу w4S3Hqn5. Магазин ключей не влияет на подключение.
- LostBot A2/c.e() всегда true для непустого payload, из-за чего бинарный 0x31 с байтом 0x3A может быть принят за IoT-строку.
- LostBot h() для 0x05/0x15 считает 2 ACK, а NineHack считает 1 успехом. Коды 1 и 2 встречаются, приложение должно принимать оба.
- NineHack не проверяет CRC и длину входящего кадра, возможен выход за границы массива.
- NineHack фиксирует len=8 в кадре ключа. Ключ другой длины ломает кадр.
- NineHack сравнивает результат writeDescriptor для API 33+ с 1, тогда как успех равен 0. В лог попадает ложная ошибка.
- NineHack не сбрасывает connectCount и не переподключается при чистом разрыве.
- NineHack содержит Crashlytics и dump_syms, хотя INTERNET отсутствует и сетевой код не вызывается.
- FishBot - клон LostBot с инжектором com.px/com.cloudinject, payload отсутствует, extra permissions: BIND_VPN_SERVICE, MANAGE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES, REQUEST_INSTALL_PACKAGES, usesCleartextTraffic=true.
- FishBot и LostBot имеют одинаковые A2/c, A2/f, z2/i, D2/b; C2/F отличается логированием и полями, протокол тот же.
- Urent 1.90.2 и 2.7.0 требуют hardware.bluetooth_le, но не содержат android.bluetooth и не имеют BLE-разрешений. useBluetooth всегда false.
- Urent LockType: NA, Code, Electro, Carbine, Electronic, TwoInOne. Это серверная модель, не BLE-протокол.

## 11. Чеклист для DanyUrent

1. GATT: сервис 6e400001, write 6e400002, notify 6e400003, CCCD 00002902. Подключаться с TRANSPORT_LE.
2. Включить notifications: setCharacteristicNotification(true) плюс запись ENABLE_NOTIFICATION_VALUE в CCCD. Для API 33+ использовать writeDescriptor с проверкой кода 0.
3. Запрашивать MTU 247, но не зависеть от него. Писать чанками max(20, MTU-3). Тип WRITE_TYPE_DEFAULT, если есть PROPERTY_WRITE.
4. Собирать кадр A3 A4 len (rand+50) (key^rand) (cmd^rand) payload^rand CRC8.
5. Использовать rand 1..254. Не путать проводной rand и исходный.
6. CRC8 MAXIM по всем байтам кроме последнего. Проверять header, длину и CRC входящих кадров.
7. Рукопожатие: cmd 0x01, ASCII-ключ, key=0. Успех payload[0]==1. sessionKey=payload[1], включая 0. Таймаут 3000 мс, повтор.
8. Не считать sessionKey==0 ошибкой. Хранить отдельный флаг authenticated.
9. Команды: 0x05 unlock, 0x15 lock, 0x61 set, 0x81 eject, 0xA2 find car, 0x31/0x60/0x63 telemetry, 0x85 RFID, 0x82 color, 0xFB/0xFC firmware.
10. Ответы 0x05/0x15 принимать по кодам 1 и 2. 0x61 успех при 0. 0x81 успех при 1 (NineHack) или 0 (LostBot), зависит от прошивки.
11. Спам: 0x05 payload 01 31 31 32 39 + 4 байта времени + 00, интервал 500 мс.
12. Прошивка: 0xFB START, пакеты по 16 байт с CRC16, запрос 0xFC.
13. Логировать TX/RX с raw hex, но не логировать ключ в production.
14. Не переиспользовать NineHack-баги: отсутствие CRC, отсутствие таймаутов, фиксированный len ключа, сравнение writeDescriptor с 1.

## 12. Добыча ключа

Ключ передается в рукопожатии как ASCII, замаскированный XOR с rand. rand выводится из байта 3 кадра. Поэтому BLE-сниффер, захвативший рукопожатие, получает ключ без криптоанализа. Это самый надежный способ для устройства, которое подключается по BLE.

Ограничения:

- Официальное Urent 1.90.2/2.7.0 BLE не использует, поэтому у него сниффить нечего.
- Если самокат требует pairing или BLE-шифрование на уровне Link Layer, нужен сниффер с поддержкой разбора encryption, иначе payload будет зашифрован.
- Брутфорс 8 ASCII-символов: 95^8 = 6.6e15, 62^8 = 2.18e14. Практически бесполезен.
- Дамп прошивки или UART-лог может дать ключ быстрее.
- Для парка Urent альтернатива - серверный openlock/closelock, но это требует аккаунта и авторизации.
