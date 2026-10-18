# Справочник опкодов

Источник: references/opcodes.csv. Всего 54 опкода.

- confirmed: 32.
- inferred: 4.
- catalog: 18.
- Поддержка DanyUrent: 18 отдельных функций, 2 декодера, 34 через консоль.
- Направления: 37 SET, 16 BIDIR, 1 NOTIF.

## 1. Полная таблица

| Код | Имя | Направление | TX payload | RX разбор | DanyUrent | Уверенность |
| --- | --- | --- | --- | --- | --- | --- |
| 01 | COMMUNICATION_KEY | BIDIR | ASCII key | status and sessionKey payload[0..1] | yes | confirmed |
| 05 | UNLOCK | BIDIR | 01 x10 or 01 31 31 32 39 t4 00 | status 0/1/2 | yes | confirmed |
| 10 | ERROR | NOTIF | unknown | rejected cmd and reason payload[0..1] | decoder | confirmed |
| 15 | LOCK | BIDIR | 01 | status 0/1/2 | yes | confirmed |
| 31 | DEVICE_INFO | BIDIR | 01 | voltage flags lock fw or IoT ASCII | yes | confirmed |
| 34 | HELMET_UNLOCK / MQTT_ACCOUNT | SET | ASCII account | empty ACK | manual | confirmed |
| 35 | MQTT_PASSWORD | SET | ASCII password | empty ACK | manual | confirmed |
| 37 | INSTALLATION_MODE | SET | unknown | catalog only | manual | catalog |
| 3D | AUTO_LOCK_MODE | SET | unknown | catalog only | manual | catalog |
| 40 | STEERING_MODE | SET | unknown | catalog only | manual | catalog |
| 50 | DISABLE_GSM_COMMUNICATION | SET | 00 or 01 | gsm state payload[0] | manual | confirmed |
| 51 | SCOOTER_OLD_DATA | BIDIR | unknown | catalog only | manual | catalog |
| 52 | CLEAR_DATA | SET | unknown | catalog only | manual | catalog |
| 53 | GSM_AUTO_DETECT | SET | 01 | detect result payload[0] | manual | confirmed |
| 54 | CLEAR_BT_PAIRED_USERS | SET | 01 | status payload[0] | yes | confirmed |
| 55 | GPS_TRACKING_SETTINGS | SET | unknown | flag and two u16 LE | manual | catalog |
| 56 | METER_CONFIG | SET | unknown | catalog only | manual | catalog |
| 60 | SCOOTER_INFO | BIDIR | 01 | battery mode speed trip range | yes | confirmed |
| 61 | SCOOTER_SET | SET | light mode throttle 00 | status payload[0] | yes | confirmed |
| 62 | SCOOTER_CONFIG | SET | unknown | config dump | manual | catalog |
| 63 | VEHICLE_REALTIME_INFO | BIDIR | 01 | battery speed current flags | yes | confirmed |
| 64 | SET_WHEEL_DIAMETER | SET | hi lo | set wheel diameter u16 BE | manual | inferred |
| 72 | CALIBRATE_INSTRUMENT_TIME | SET | epoch4 tz | epoch and timezone | manual | confirmed |
| 73 | METER_RESET | SET | 01 01 | meter reset fields | manual | confirmed |
| 74 | NAVIGATION | SET | unknown | catalog only | manual | catalog |
| 75 | AUDIO_TOGGLE | SET | unknown | audio state | manual | catalog |
| 76 | START_TESTING | SET | 01 | test mode state | manual | confirmed |
| 77 | CLEAR_MILEAGE | SET | 01 | clear mileage result | manual | confirmed |
| 78 | XM_FIND_CAR | SET | unknown | xm find result | manual | catalog |
| 79 | AUTOMATIC_SCREEN_OFF | SET | unknown | screen off result | manual | catalog |
| 81 | SCOOTER_BATTERY / OUT_LOCK | BIDIR | 05 for eject | tag and status | yes | confirmed |
| 82 | SCOOTER_COLOR_LIGHT | SET | mode 00 R G B 00 00 00 | mode and RGB | yes | confirmed |
| 85 | READ_RFID_CARD | BIDIR | 01 | RFID u32 BE | yes | confirmed |
| 86 | DELETE_RFID | SET | id1 u32 BE id2 u32 BE | delete result | decoder | confirmed |
| 90 | XM_POWER_CONTROL | SET | unknown | xm power state | manual | catalog |
| 91 | POWER_CONTROL | SET | 01 | power state | yes | confirmed |
| 92 | IOT_RESTART | SET | 01 | restart ACK | yes | confirmed |
| 95 | CHAIN_LOCK_PASSWORD_ENTRY | SET | unknown | catalog only | manual | catalog |
| 96 | CHAIN_LOCK_PASSWORD_DELETE | SET | unknown | catalog only | manual | catalog |
| 97 | CHAIN_LOCK_GET_USER_PASSWORD | BIDIR | unknown | catalog only | manual | catalog |
| A0 | VEHICLE_COMMANDS | BIDIR | raw bytes | raw reply | manual | inferred |
| A1 | QR_CODE_READ_WRITE | BIDIR | tag ASCII | tag and ASCII | manual | inferred |
| A2 | FIND_CAR (sound) | SET | 01 or 02 or 03 or 06 | find ACK | yes | confirmed |
| C0 | BATTERY_UNLOCK | SET | 01 | battery cover status | yes | confirmed |
| C3 | ACCESSORY_CONTROL | SET | type action | accessory state | manual | inferred |
| C4 | UPDATE_CERTIFICATE | SET | 01 | certificate ACK | manual | confirmed |
| C5 | SWITCH_SIM | SET | slot | slot state | manual | confirmed |
| C6 | SWITCH_NEXT_SIM | SET | 01 | ACK | manual | confirmed |
| C7 | BATTERY/LIGHT or LOG_DATA | BIDIR | unknown | catalog only | manual | catalog |
| DC | AVAS_SWITCH | SET | ASCII HBCS + 00 | AVAS state | manual | confirmed |
| DE | AVAS_SWITCH_2 | SET | unknown | AVAS2 state | manual | catalog |
| FA | FW_DEVICE_INFO | BIDIR | empty | ASCII firmware info | yes | confirmed |
| FB | FW_DEVICE_DATA | BIDIR | kind total2 crc2 type key | status 0..4 | yes | confirmed |
| FC | FW_GET_FIRMWARE_DATA | BIDIR | idx2 type or idx2 data16 crc2 | pack request or data | yes | confirmed |

## 2. Команды с подтверждённым сборщиком

- 01: keyCommand.
- 05: unlockCommand, spamUnlockCommand.
- 15: lockCommand.
- 31: requestTelemetry.
- 34, 35: ASCII account и password, только консоль.
- 50, 53: GSM, только консоль.
- 54: clear BT.
- 60, 61, 63: телеметрия и настройки.
- 72, 73, 76, 77: сервисные, только консоль.
- 81: eject.
- 82: RGB.
- 85, 86: RFID.
- 91, 92: питание и IoT.
- A2: поиск.
- C0: крышка батареи.
- C4, C5, C6: сертификат и SIM, только консоль.
- DC: AVAS, только консоль.
- FA, FB, FC: прошивка и конфиг.

## 3. Опкоды без подтверждённого сборщика

18 опкодов имеют только имя из каталога. Для них нельзя утверждать payload, длину и ответ. Это:

37, 3D, 40, 51, 52, 55, 56, 62, 74, 75, 78, 79, 90, 95, 96, 97, C7, DE.

Использовать только через консоль и только после проверки на hardware.

## 4. Восстановленные из декодера

4 опкода:

- 64: диаметр колеса, u16 big-endian, диапазон 100..400;
- A0: сырые команды, ответ сырой;
- A1: QR-код, ASCII tag;
- C3: аксессуар, type и action.

Эти данные inferred, не confirmed. Нужна проверка.

## 5. Расхождения

- 0x78 XM_FIND_CAR не имеет сборщика. LostBot ищет самокат через 0xA2 с payload 01, 02, 03 или 06.
- 0xC3 не имеет подтверждённого сборщика, только декодер.
- 0x64 не имеет подтверждённого сборщика, только декодер.
- 0x10 не отправляется приложением, только принимается.
- 0x05 имеет две формы payload.
- 0x81 в каталоге назван SCOOTER_BATTERY / OUT_LOCK, в UI используется как изъятие батареи.

## 6. Машиночитаемая форма

references/opcodes.csv содержит колонки:

- code_hex;
- code_dec;
- name;
- direction;
- tx_payload;
- tx_source;
- rx_decode;
- rx_source;
- danyurent;
- confidence.

Проверка:

```
python -c "import csv; r=list(csv.DictReader(open('references/opcodes.csv',encoding='utf-8'))); print(len(r))"
```

Ожидаемый вывод: 54.
