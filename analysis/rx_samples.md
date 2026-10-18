# Байтовые примеры ответов

Источник: references/frames.json, сгенерирован references/generate_frames.py. Кадры собраны при исходном rand=0x01, проводной rand=0x33, key=0, если не указано иное. Это тест-векторы для декодера, а не литералы из дампа.

## 1. Рукопожатие

| Вектор | Payload | Кадр | Разбор |
| --- | --- | --- | --- |
| handshake_ack_0 | 01 00 | A3 A4 02 33 01 00 00 01 06 | статус 1, sessionKey 0 |
| handshake_ack_42 | 01 42 | A3 A4 02 33 01 00 00 43 FC | статус 1, sessionKey 0x42 |

Правило: успех только при payload.length >= 2 и payload[0] == 1. sessionKey = payload[1]. Ноль допустим.

## 2. Статусы команд

| Вектор | Payload | Кадр | Разбор |
| --- | --- | --- | --- |
| error_lock | 15 02 | A3 A4 02 33 01 11 14 03 8C | ERROR: отвергнут 0x15, причина 0x02 |
| unlock_ok_1 | 01 | A3 A4 01 33 01 04 00 22 | unlock, статус 1 |
| unlock_ok_2 | 02 | A3 A4 01 33 01 04 03 C0 | unlock, статус 2 |
| lock_ok_0 | 00 | A3 A4 01 33 01 14 01 90 | lock, статус 0 |

LostBot A2/c.java:266 для 0x05 и 0x15 считает 2 подтверждением, 0 успехом, 1 ошибкой. NineHack ждет 1. Прошивки встречаются с кодами 0, 1 и 2, поэтому клиент должен принимать 0 и 1 и 2 по контексту команды.

Для 0x61 успех это payload[0] == 0. Для 0x81 LostBot ждет 0, NineHack ждет 1.

## 3. Device info 0x31

| Вектор | Payload | Кадр | Разбор |
| --- | --- | --- | --- |
| device_info_bin | 10 68 01 00 01 04 02 | A3 A4 07 33 01 30 11 69 00 01 00 05 03 FC | 4200, flags 01, unlocked, fw 1.4.2 |
| device_info_text | 49 4D 45 49 3A 38 36 30 31 32 33 34 35 36 37 38 39 30 31 32 | A3 A4 14 33 01 30 48 4C 44 48 3B 39 37 31 30 33 32 35 34 37 36 39 38 31 30 33 52 | IoT network: IMEI:860123456789012 |

Бинарный разбор A2/c.java:270-280:

| Смещение | Поле |
| --- | --- |
| 0..1 | voltage u16 BE, вольты = значение / 100 |
| 2 | flags |
| 3 | не используется |
| 4..6 | firmware major, minor, patch |

locked = (flags & 1) == 0. То есть flags 00 означает locked, flags 01 означает unlocked.

A2/c.e() задумана как проверка печатности, но для непустого массива всегда возвращает true. Затем A2/c.c() ищет байт 0x3A. Поэтому любой непустой бинарный payload с байтом 0x3A будет ошибочно показан как IoT-строка. DanyScoot использует более строгую проверку isAsciiDeviceInfo, но она также требует наличие 0x3A.

## 4. Телеметрия 0x60 и 0x63

| Вектор | Payload | Кадр | Разбор |
| --- | --- | --- | --- |
| scooter_info | 45 02 00 BB 00 7C 05 FA | A3 A4 08 33 01 61 44 03 01 BA 01 7D 04 FB E6 | 69%, mode 2, 18.7 км/ч, пробег 1.24 км, запас 15.30 км |
| realtime | 45 00 BB 00 7B 03 | A3 A4 06 33 01 62 44 01 BA 01 7A 02 2D | 69%, 18.7 км/ч, 1.23 А, flags 3 |

0x60, A2/c.java:296-300:

| Смещение | Поле |
| --- | --- |
| 0 | battery, проценты |
| 1 | mode |
| 2..3 | speed u16 BE, км/ч = значение / 10 |
| 4..5 | trip u16 BE, метры = значение * 10 |
| 6..7 | range u16 BE, метры = значение * 10 |

0x63, A2/c.java:302-306:

| Смещение | Поле |
| --- | --- |
| 0 | battery, проценты |
| 1..2 | speed u16 BE, км/ч = значение / 10 |
| 3..4 | current u16 BE, амперы = значение / 100 |
| 5 | flags |

## 5. RFID, свет, аксессуары

| Вектор | Payload | Кадр | Разбор |
| --- | --- | --- | --- |
| rfid | 12 34 56 78 | A3 A4 04 33 01 84 13 35 57 79 60 | RFID 12345678 |
| rfid_pair | 12 34 56 78 9A BC DE F0 | A3 A4 08 33 01 87 13 35 57 79 9B BD DF F1 04 | два RFID: 12345678 и 9ABCDEF0 |
| color | 01 00 7C 6A E8 | A3 A4 05 33 01 83 00 01 7D 6B E9 C6 | mode 1, RGB 7C6AE8 |
| accessory | 01 01 | A3 A4 02 33 01 C2 00 00 44 | type 1 saddle, action 1 open |

0x85: RFID читается как u32 BE из первых четырех байт. 0x86: первый ID из байт 0..3, второй из байт 4..7. 0x82: mode=b0, R=b2, G=b3, B=b4; байт b1 и хвост нулевые. 0xC3: type 1 saddle, 3 tail-box, 8 helmet; action 1 open, 2 close.

## 6. Прошивка 0xFB и 0xFC

| Вектор | Payload | Кадр | Разбор |
| --- | --- | --- | --- |
| fw_ready | 01 | A3 A4 01 33 01 FA 00 67 | READY |
| fw_complete | 02 | A3 A4 01 33 01 FA 03 85 | COMPLETE |
| fw_timeout | 03 | A3 A4 01 33 01 FA 02 DB | timeout |
| fw_crc_error | 04 | A3 A4 01 33 01 FA 05 58 | total CRC error |
| fw_pack_request | 00 05 | A3 A4 02 33 01 FD 01 04 60 | запрос пакета 5 |
| fw_pack_data | 00 05 00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F A2 68 | A3 A4 14 33 01 FD 01 04 01 00 03 02 05 04 07 06 09 08 0B 0A 0D 0C 0F 0E A3 69 5D | пакет 5 и CRC16 A268 |

0xFB статусы: 0 ошибка пароля или ключа, 1 READY, 2 COMPLETE, 3 timeout, 4 total CRC error.

0xFC входящий payload:

| Длина | Смысл |
| --- | --- |
| 2 | запрос пакета, idx u16 BE |
| 20 | пакет: idx u16 BE + 16 байт данных + crc16 BE |

CRC16 для пакета считается по 18 байтам idx + data и передается big-endian. Проверка CRC16 MODBUS: 123456789 = 4B37.

## 7. Сборка кадра из чанков

Полный кадр имеет размер 7 + payload. При payload 255 это 262 байта. BLE-запись режется на чанки.

LostBot: chunk = max(20, mtu - 3). При mtu 23 это 20 байт, при mtu 247 это 244 байта. Каждый чанк пишется отдельно через очередь GATT.

Порядок:

1. Собрать полный кадр.
2. Посчитать CRC8 по полному кадру без последнего байта.
3. Добавить CRC8.
4. Порезать на чанки.
5. Писать чанки по порядку.
6. На приеме склеить буфер и только потом искать A3 A4, проверять len и CRC8.

Нельзя считать CRC8 по каждому чанку отдельно. Нельзя резать до добавления CRC8.

## 8. Битые кадры

| Вектор | Кадр | Ожидаемая ошибка |
| --- | --- | --- |
| bad_crc | A3 A4 01 33 01 14 00 CF | CRC mismatch expected=CE actual=CF |
| bad_len | A3 A4 02 33 01 14 00 CE | Invalid frame length expected=9 got=8 |
| bad_header | A2 A4 01 33 01 14 00 CE | Invalid frame header |
| too_short | A3 A4 01 33 01 | Frame too short: 5 |

## 9. Проверка декодера

    python references/generate_frames.py
    python -c "import json; d=json.load(open('references/frames.json',encoding='utf-8')); print(len(d['tx']), len(d['rx']), len(d['malformed']))"

Ожидаемый вывод: 48 20 4.
