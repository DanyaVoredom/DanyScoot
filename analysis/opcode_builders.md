# Матрица сборщиков опкодов

Источник: jadx-декомпиляция LostBot/FishBot и smali NineHack. Машиночитаемая версия: references/opcodes.csv. Векторы кадров: references/frames.json.

Всего 54 опкода из A2/c.java:19-36. Из них 31 имеет подтвержденный сборщик в коде, 18 известны только по каталогу, 4 восстановлены из декодера, 1 является только входящим. В таблице ниже 32 строки, потому что 0x05 имеет два варианта payload.

## 1. Подтвержденные сборщики

| Код | Payload | Сборщик | Вектор |
| --- | --- | --- | --- |
| 01 | ASCII ключа | LostBot C2/F.java:376, NineHack controlActivity.smali:736 | handshake_ninehack, handshake_lostbot |
| 05 | 01 31 31 32 39 + t4 BE + 00 | LostBot C2/F.java:1306, C2/o.java:54, D2/C0141b.java:48 | spam |
| 05 | 01 x10 | NineHack CommandCreator.smali:1147-1349 | unlock_ninehack |
| 15 | 01 | LostBot D2/C0141b.java:29, C2/o.java:57 | lock |
| 31 | 01 | LostBot D2/C0141b.java:31, D2/R0.java:51 | device_info |
| 34 | ASCII account | LostBot D2/C0148e0.java:55 | нет отдельного вектора |
| 35 | ASCII password | LostBot D2/C0148e0.java:62 | нет отдельного вектора |
| 50 | 00 или 01 | LostBot C2/o.java:125 и 128 | gsm_off, gsm_on |
| 53 | 01 | LostBot C2/o.java:131 | gsm_detect |
| 54 | 01 | LostBot D2/C0148e0.java:38 | clear_bt |
| 60 | 01 | LostBot D2/C0141b.java:33, D2/R0.java:48 | scooter_info |
| 61 | light mode throttle 00 | LostBot D2/C0141b.java:39 и 41, C2/o.java:79 и 82 | set_on, set_off |
| 63 | 01 | LostBot D2/C0141b.java:35 | realtime |
| 72 | epoch4 BE + tz | LostBot C2/o.java:105 | calibrate |
| 73 | 01 01 | LostBot D2/C0148e0.java:34 | meter_reset |
| 76 | 01 | LostBot C2/o.java:111 | start_test |
| 77 | 01 | LostBot C2/o.java:108 | clear_mileage |
| 81 | 05 | LostBot C2/o.java:62, D2/C0141b.java:37 | eject |
| 82 | mode 00 R G B 00 00 00 | LostBot D2/C0141b.java:58, D2/C0166n0.java:274 | color |
| 85 | 01 | LostBot C2/o.java:101, D2/C0141b.java:56 | rfid_read |
| 86 | id1 u32 BE + id2 u32 BE | LostBot D2/C0175s0.java:36 | rfid_delete |
| 91 | 01 | LostBot D2/C0148e0.java:30, C2/o.java:47 | power_off |
| 92 | 01 | LostBot D2/C0148e0.java:48, D2/C0141b.java:52 | iot_restart |
| A2 | 01, 02, 03 или 06 | LostBot D2/C0141b.java:43 и 45, C2/o.java:41, 44, 50 | find1, find2, find3, find6 |
| C0 | 01 | LostBot D2/C0141b.java:37, C2/o.java:62 | battery_cover |
| C4 | 01 | LostBot C2/o.java:141, D2/C0141b.java:62 | cert |
| C5 | slot | LostBot D2/C0148e0.java:66 | switch_sim1, switch_sim2 |
| C6 | 01 | LostBot C2/o.java:138, D2/C0141b.java:60 | next_sim |
| DC | ASCII HBCS + 00 | LostBot I1/a.java:737-745 | avas_on, avas_off |
| FA | пусто | LostBot D2/C0141b.java:50, C2/F.java:456 | fw_info |
| FB | kind total2 BE crc2 BE type key | LostBot C2/F.java:797-829 | fw_start |
| FC | idx2 BE + type | LostBot C2/F.java:1325 | fw_read |

## 2. Только каталог

Для этих кодов есть имя и декодер ответа, но сборщик в дампе не найден. Это не значит, что команда не работает; это значит, что payload не подтвержден.

| Код | Имя | Что известно |
| --- | --- | --- |
| 37 | INSTALLATION_MODE | только имя |
| 3D | AUTO_LOCK_MODE | только имя |
| 40 | STEERING_MODE | только имя |
| 51 | SCOOTER_OLD_DATA | только имя |
| 52 | CLEAR_DATA | только имя |
| 55 | GPS_TRACKING_SETTINGS | ответ: flag + два u16 LE |
| 56 | METER_CONFIG | только имя |
| 62 | SCOOTER_CONFIG | ответ: reset + три скорости |
| 74 | NAVIGATION | только имя |
| 75 | AUDIO_TOGGLE | ответ: b0 == 255 означает OFF, b1 уровень |
| 78 | XM_FIND_CAR | сборщика нет; поиск в приложении идет через A2 |
| 79 | AUTOMATIC_SCREEN_OFF | ответ: b0 |
| 90 | XM_POWER_CONTROL | ответ: b0 |
| 95 | CHAIN_LOCK_PASSWORD_ENTRY | только имя |
| 96 | CHAIN_LOCK_PASSWORD_DELETE | только имя |
| 97 | CHAIN_LOCK_GET_USER_PASSWORD | только имя |
| C7 | BATTERY/LIGHT или LOG_DATA | только имя |
| DE | AVAS_SWITCH_2 | только имя |

## 3. Восстановлено из декодера

| Код | Payload | Основание |
| --- | --- | --- |
| 64 | hi lo, u16 BE, 100..400 мм | A2/c.java:304 разбирает ответ как диаметр |
| A0 | произвольные байты | A2/c.java:365 печатает raw hex |
| A1 | tag + ASCII | A2/c.java:366 разбирает tag и строку |
| C3 | type action | A2/c.java:369-371 разбирает type 1/3/8 и action 1/2 |

## 4. Только прием

| Код | Причина |
| --- | --- |
| 10 | ERROR, отправляется самокатом, payload = отвергнутый cmd + причина |

## 5. Расхождения с protocol_full.md

В первой версии protocol_full.md были три неточности, здесь они исправлены:

- 0x78 не имеет сборщика. Поиск самоката в LostBot идет через 0xA2 с payload 01, 02, 03 или 06.
- 0xC3 не имеет подтвержденного сборщика. Payload type action восстановлен из декодера A2/c.java:369-371.
- 0x64 не имеет подтвержденного сборщика. Payload hi lo восстановлен из декодера A2/c.java:304.

## 6. Проверка

    python references/generate_frames.py

Скрипт пересобирает references/frames.json независимой реализацией CRC8, CRC16 и кадра. Векторы используют originalRand, а не проводной rand; поле wire_rand = (rand + 50) & 255.
