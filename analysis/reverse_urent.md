# Реверс BLE-протокола Urent

Независимая проверка протокола по LostBot, NineHack и FishBot, сверка с кодом DanyScoot. Метки: confirmed (подтверждено кодом референса), inferred (вывод без прямого подтверждения), missing (данных нет).

## Артефакты

| Артефакт | Путь | Роль |
|---|---|---|
| LostBot (smali) | analysis/LostBot/smali/z2/i.smali, analysis/LostBot/smali/z2/g.smali, analysis/LostBot/smali/D2/s.1.smali | транспорт, GATT, MTU, запись |
| FishBot (jadx) | analysis/java/fishbot/sources/A2/f.java, analysis/java/fishbot/sources/A2/c.java, analysis/java/fishbot/sources/C2/F.java | рама, каталог опкодов, auth, спам |
| FishBot builders | analysis/java/fishbot/sources/D2/C0141b.java, analysis/java/fishbot/sources/C2/o.java | полезные нагрузки команд |
| FishBot ключ | analysis/java/fishbot/sources/z2/l.java | массивы для генерации ключа |
| NineHack (smali) | analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali, .../Crc8.smali, .../controlActivity.smali | независимая реализация рамы и CRC |
| NineHack ресурсы | analysis/NineHack/res/values/strings.xml | ключ по умолчанию |
| Urent APK | references/apks/urent-2.7.0-base.apk, 82 428 322 байта, SHA-256 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 | официальное приложение, BLE-кода нет |
| Urent распаковки | C:\Users\Dmode\AppData\Local\Temp\opencode\urent_dec, urent1902_dec | поиск BLE-классов, UUID и ключей не дал совпадений |

LostBot и FishBot - один и тот же код: Java-декомпиляция FishBot используется для цитирования логики, smali LostBot - для транспорта. Тождество подтверждено совпадающими значениями: UUID (analysis/java/fishbot/sources/z2/i.java:115-121 и analysis/LostBot/smali/z2/i.smali:68-98), MTU по умолчанию 23 (analysis/java/fishbot/sources/z2/i.java:137 и analysis/LostBot/smali/z2/i.smali:159-161), чанк max(20, mtu-3) (analysis/java/fishbot/sources/z2/i.java:402 и analysis/LostBot/smali/z2/i.smali:1146-1154), подключение 20000 мс и TRANSPORT_LE (analysis/java/fishbot/sources/z2/i.java:333-335 и analysis/LostBot/smali/z2/i.smali:943-956). Поля обфусцированы по-разному, сопоставление по значениям (inferred по именам, confirmed по числам).

Urent BLE не содержит. Проверено по references/apks/urent-2.7.0-base.apk и распаковкам urent_dec и urent1902_dec: поиск BluetoothGatt, BluetoothLeScanner, 6e400001, 4BKNwi77 и w4S3Hqn5 не дал совпадений. Это согласуется с analysis/report_urent.md:102-109, analysis/report_urent.md:129, analysis/network.md:73 и analysis/source_map.md:72: нет BLE-клиента, нет UUID Nordic UART, useBluetooth всегда false, AvailableViaBluetooth - только серверный enum. Вывод: BLE-протокол Urent в официальных APK не реализован, единственные источники - LostBot и NineHack.

Каталоги analysis/payload и analysis/payload_apktool к Urent не относятся: это ассет плагина FishBot (com.cloudinject.resplugin), см. analysis/fishbot_handoff.md:11-12 и analysis/report_fishbot.md:30. Использовать их как доказательство по Urent нельзя.

## GATT

| Параметр | Значение | Источник |
|---|---|---|
| Сервис | 6e400001-b5a3-f393-e0a9-e50e24dcca9e | analysis/LostBot/smali/z2/i.smali:68-78 |
| Запись (RX) | 6e400002-b5a3-f393-e0a9-e50e24dcca9e | analysis/LostBot/smali/z2/i.smali:80-88 |
| Уведомления (TX) | 6e400003-b5a3-f393-e0a9-e50e24dcca9e | analysis/LostBot/smali/z2/i.smali:90-98 |
| CCCD | 00002902-0000-1000-8000-00805f9b34fb | analysis/LostBot/smali/z2/i.smali:100 |
| Включение уведомлений | setCharacteristicNotification | analysis/LostBot/smali/b/j.smali:160 |
| MTU запрос | 247 | analysis/LostBot/smali/D2/s.1.smali:128-131 |
| MTU по умолчанию | 23 | analysis/LostBot/smali/z2/i.smali:159-161 |
| Таймаут MTU | 3000 мс | analysis/LostBot/smali/D2/s.1.smali:182-184 |
| Транспорт | TRANSPORT_LE (2) | analysis/LostBot/smali/z2/i.smali:954-956 |
| Таймаут подключения | 20000 мс | analysis/LostBot/smali/z2/i.smali:943-945 |
| Размер чанка | max(20, mtu-3) | analysis/LostBot/smali/z2/i.smali:1146-1154 |
| Тип записи | 1 при PROPERTY_WRITE_NO_RESPONSE (0x04), иначе 2 | analysis/LostBot/smali/z2/i.smali:1128-1145 |
| Фильтр сканирования | по сервису UART, ScanMode 2, CallbackType 1 | analysis/LostBot/smali/z2/i.smali:641-671 |

LostBot отправляет кадр по частям через очередь (analysis/LostBot/smali/z2/i.smali:1157-1177). NineHack пишет кадр целиком с типом 2 (analysis/NineHack/smali_classes3/com/timeylies/ninehack/controlActivity.smali:653-663) и requestMtu не вызывает (поиск по каталогу NineHack пуст, confirmed отсутствие). Все кадры NineHack укладываются в 20 байт (auth 15, unlock 17), поэтому MTU не нужен (inferred по длинам, см. тестовые векторы).

Приём: LostBot копирует value только для UUID TX и обрабатывает в Handler (analysis/LostBot/smali/z2/g.smali:115-152). Сборка/разбор кадра живёт отдельно от транспорта (analysis/LostBot/smali/z2/g.smali:23-99).

## Рама

Формат (confirmed, analysis/java/fishbot/sources/A2/f.java:20-31):

A3 A4 len rand key cmd payload... crc

| Поле | Смысл | Источник |
|---|---|---|
| A3 A4 | заголовок | analysis/java/fishbot/sources/A2/f.java:20 |
| len | длина payload, максимум 255 | analysis/java/fishbot/sources/A2/f.java:16-20 |
| rand | (originalRand + 50) & 0xFF | analysis/java/fishbot/sources/A2/f.java:20 |
| key | originalKey XOR originalRand | analysis/java/fishbot/sources/A2/f.java:20 |
| cmd | originalCmd XOR originalRand | analysis/java/fishbot/sources/A2/f.java:20 |
| payload | байт XOR originalRand | analysis/java/fishbot/sources/A2/f.java:22-25 |
| crc | CRC8 по всем байтам до CRC | analysis/java/fishbot/sources/A2/f.java:27-31 |

CRC-8/MAXIM: poly 0x8C (отражённый 0x31), init 0x00, refin true, refout true, xorout 0x00 (analysis/java/fishbot/sources/A2/f.java:34-40). Таблица FishBot и таблица NineHack эквивалентны (analysis/NineHack/smali_classes3/com/timeylies/ninehack/Crc8.smali:60-317, полином 0x8c на строке 278). Контрольное значение CRC8("123456789") = 0xA1 (вычислено независимой побитовой реализацией и табличной, результаты совпали).

Разбор: длина кадра строго 7 + len, иначе ошибка; обратное преобразование rand = (wire - 50) & 0xFF, key и cmd XOR с восстановленным rand, payload XOR с тем же (analysis/java/fishbot/sources/A2/f.java:71-96). Экранирования и контрольной суммы по частям нет (confirmed).

Диапазон originalRand: LostBot nextInt(0, 255) то есть 0..254 (analysis/java/fishbot/sources/K1/a.java:10-11, analysis/java/fishbot/sources/J1/e.java:19-27); NineHack Random.nextInt(0xFE)+1 то есть 1..254 (analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:1155-1171). originalRand = 0, а значит wire-байт 0x32, возможен только в LostBot (inferred, на разбор не влияет).

Максимальная длина кадра 262 байта (255 + 6 + 1), analysis/java/fishbot/sources/A2/f.java:16-20.

## Auth

| Шаг | Значение | Источник |
|---|---|---|
| Команда | 0x01 | analysis/java/fishbot/sources/A2/c.java:21 |
| Поле key кадра | 0, явно передаётся Integer(0) | analysis/java/fishbot/sources/C2/F.java:376, analysis/java/fishbot/sources/C2/F.java:564-572 |
| Payload запроса | ASCII ключа без завершающего нуля | analysis/java/fishbot/sources/C2/F.java:372-376 |
| Успех | payload[0] == 1 | analysis/java/fishbot/sources/C2/F.java:394 |
| Сессионный ключ | payload[1] | analysis/java/fishbot/sources/C2/F.java:395 |
| Таймаут | 3000 мс | analysis/java/fishbot/sources/C2/F.java:379, analysis/java/fishbot/sources/cd/bn.java:117 |
| Ключ до auth | 66 (0x42) по умолчанию | analysis/java/fishbot/sources/C2/F.java:280 |
| После auth | поле key кадра равно payload[1] | analysis/java/fishbot/sources/C2/F.java:279-282, analysis/java/fishbot/sources/C2/F.java:394-395 |

NineHack: key=0, cmd=0x01, payload - строка ключа, len жёстко 8 (analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:578-635). Успех: payload[0]==1, иначе сообщение о неверном ключе (analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:93-107, 157-172). Таймаута auth в NineHack нет (поиск пуст, confirmed отсутствие).

Ключ по умолчанию:
- NineHack: 4BKNwi77 (analysis/NineHack/res/values/strings.xml:44).
- LostBot: вычисляется из массивов, результат w4S3Hqn5 (analysis/java/fishbot/sources/D2/C0141b.java:81-85, analysis/java/fishbot/sources/z2/l.java:10-13; значение получено исполнением формулы b[i%5] ^ a[i] ^ ((i*31)&255)).
- DanyScoot: 4BKNwi77 (app/app/src/main/java/ru/dany/scoot/core/Prefs.kt:116), второй пресет w4S3Hqn5 (app/app/src/main/java/ru/dany/scoot/ble/ScooterCodec.kt:54).

Отличие ключей LostBot и NineHack - confirmed расхождение референсов. Какой ключ у конкретной прошивки, не определено (missing: нужен журнал auth с реального скутера).

Повторный auth не предусмотрен ни в LostBot, ни в NineHack: явного повторного вызова в обработчиках не найдено (inferred; missing: поведение прошивки при повторном 0x01).

## Команды

Каталог LostBot (десятичные коды, analysis/java/fishbot/sources/A2/c.java:21-27). Перевод в hex: 0x01, 0x05, 0x10, 0x15, 0x31, 0x34, 0x35, 0x37, 0x3D, 0x40, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x60, 0x61, 0x62, 0x63, 0x64, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x81, 0x82, 0x85, 0x86, 0x90, 0x91, 0x92, 0x95, 0x96, 0x97, 0xA0, 0xA1, 0xA2, 0xC0, 0xC3, 0xC4, 0xC5, 0xC6, 0xC7, 0xDC, 0xDE, 0xFA, 0xFB, 0xFC.

Полезные нагрузки (confirmed, analysis/java/fishbot/sources/D2/C0141b.java, analysis/java/fishbot/sources/C2/o.java):

| Cmd | Payload | Смысл | Источник |
|---|---|---|---|
| 0x15 | 01 | lock | analysis/java/fishbot/sources/D2/C0141b.java:29 |
| 0x31 | 01 | device info | analysis/java/fishbot/sources/D2/C0141b.java:31 |
| 0x60 | 01 | scooter info | analysis/java/fishbot/sources/D2/C0141b.java:33 |
| 0x63 | 01 | realtime | analysis/java/fishbot/sources/D2/C0141b.java:35 |
| 0xC0 | 01 | battery unlock | analysis/java/fishbot/sources/D2/C0141b.java:37 |
| 0x61 | 02 00 00 00 | режим 2 | analysis/java/fishbot/sources/D2/C0141b.java:39 |
| 0x61 | 01 00 00 00 | режим 1 | analysis/java/fishbot/sources/D2/C0141b.java:41 |
| 0xA2 | 01 | find car | analysis/java/fishbot/sources/D2/C0141b.java:43 |
| 0xA2 | 06 | find car вариант | analysis/java/fishbot/sources/D2/C0141b.java:45 |
| 0x05 | 01 31 31 32 39 + UNIX секунды BE + 00 | unlock со временем | analysis/java/fishbot/sources/D2/C0141b.java:47-48 |
| 0xFA | пусто | FW info | analysis/java/fishbot/sources/D2/C0141b.java:50 |
| 0x92 | 01 | IOT restart | analysis/java/fishbot/sources/D2/C0141b.java:52 |
| 0x85 | 01 | read RFID | analysis/java/fishbot/sources/D2/C0141b.java:56 |
| 0x82 | 01 00 7C 6A E8 00 00 00 | цвет | analysis/java/fishbot/sources/D2/C0141b.java:58 |
| 0xC6 | 01 | switch next SIM | analysis/java/fishbot/sources/D2/C0141b.java:60 |
| 0xC4 | 01 | update certificate | analysis/java/fishbot/sources/D2/C0141b.java:62 |
| 0x81 | 05 | изъятие батареи, вместе с 0xC0 01 | analysis/java/fishbot/sources/C2/o.java:62 |
| 0x15 | 01 | lock | analysis/java/fishbot/sources/C2/o.java:57 |

NineHack поддерживает только 0x01, 0x10, 0x05, 0x15, 0x61, 0x81 (analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:52-60):

| Cmd | Payload | Источник |
|---|---|---|
| 0x01 | ASCII ключа, len 8 | analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:578 |
| 0x05 | 10 байт 01 | analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:1151, 1337-1348 |
| 0x15 | 01 | analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:754-816 |
| 0x61 | headlight, mode, throttle, 00 | analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:989-1003 |
| 0x81 | 05 | analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:417-427 |

Мэппинг 0x61 в NineHack: переключатели дают headlight 1 или 2 и throttle 1 или 2 (No Switches = 1/1, Headlight On = 2/1, Throttle On = 1/2), mode приходит параметром UI (analysis/NineHack/smali_classes3/com/timeylies/ninehack/controlActivity.smali:990-1032). DanyScoot повторяет это и переводит mode 0..2 в 1..3 (app/app/src/main/java/ru/dany/scoot/ble/ScooterCodec.kt:174-178); соответствие mode у NineHack не подтверждено (missing).

0x05 с 10 байтами 01 есть только в NineHack (confirmed). LostBot 0x05 всегда с меткой времени 1129 (analysis/java/fishbot/sources/C2/o.java:52-54, analysis/java/fishbot/sources/C2/F.java:1306). Спам LostBot - та же команда 0x05 с меткой времени (analysis/java/fishbot/sources/C2/o.java:53-54).

Опкоды 0x34, 0x35, 0x37, 0x40, 0x50, 0x52, 0x53, 0x54, 0x55, 0x56, 0x62, 0x64, 0x72-0x79, 0x86, 0x90, 0x91, 0x95, 0x96, 0x97, 0xA0, 0xA1, 0xC3, 0xC5, 0xC7, 0xDC, 0xDE, 0xFB, 0xFC присутствуют только в каталоге LostBot без построителей payload (analysis/java/fishbot/sources/A2/c.java:21-27). Payload для них - missing.

## Телеметрия

0x31 device info (analysis/java/fishbot/sources/A2/c.java:271-286, analysis/java/fishbot/sources/C2/F.java:1020):
- Текстовая ветка: если payload содержит ':' (0x3A) и все байты печатные, payload - ASCII строка (analysis/java/fishbot/sources/A2/c.java:272-277).
- Бинарная ветка: b0..b1 напряжение u16 BE в сотых вольта, b2 flags, locked = (flags & 1) == 0, b4.b5.b6 версия (analysis/java/fishbot/sources/A2/c.java:282-286).

0x60 scooter info: b0 батарея %, b1 режим, b2..b3 скорость u16 BE / 10 км/ч, b4..b5 пробег u16 BE * 10 м, b6..b7 запас хода u16 BE * 10 м; минимум 8 байт (analysis/java/fishbot/sources/A2/c.java:299-303).

0x63 realtime: b0 батарея %, b1..b2 скорость u16 BE / 10, b3..b4 ток u16 BE / 100, b5 flags; минимум 6 байт (analysis/java/fishbot/sources/A2/c.java:308-312).

0x85 read RFID: b0..b3 идентификатор big-endian, выводится как 32-битное число (analysis/java/fishbot/sources/A2/c.java:338-342).

0x86 delete RFID: запрос содержит два 32-битных идентификатора, второй b4..b7 (analysis/java/fishbot/sources/A2/c.java:101-105); ответ декодируется только как ACK (analysis/java/fishbot/sources/A2/c.java:343-344).

0x81: tag b0, status b1 (analysis/java/fishbot/sources/A2/c.java:331-335).

0xFC: индекс пакета b0<<8 | b1, иначе длина чанка (analysis/java/fishbot/sources/A2/c.java:391-395).

0xFA: ASCII информация о прошивке (analysis/java/fishbot/sources/A2/c.java:371-372).

0xFB: статусы передачи прошивки (analysis/java/fishbot/sources/A2/c.java:373-390).

Опрос после auth в DanyScoot: 0x31 01, 0x60 01, 0x63 01 (app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt:629-633). У LostBot те же запросы подтверждены построителями (analysis/java/fishbot/sources/D2/C0141b.java:31-35), автоматическая отправка сразу после auth в обработчике не найдена (analysis/java/fishbot/sources/C2/F.java:392-400), порядок опроса не установлен (inferred).

## Статусы

0x05 и 0x15 (LostBot, analysis/java/fishbot/sources/A2/c.java:438-443): пусто или payload[0]==2 - ACK; payload[0]==0 - OK; иначе FAIL status (analysis/java/fishbot/sources/A2/c.java:427-436). Итог: успех = 0, 2, пусто.

0x61 (LostBot, analysis/java/fishbot/sources/A2/c.java:304-305): успех payload[0]==0 (analysis/java/fishbot/sources/A2/c.java:428-435).

0x10 ERROR (analysis/java/fishbot/sources/A2/c.java:264-268): payload[0] - отклонённая команда, payload[1] - причина. NineHack трактует payload[0]==1 как ошибку CRC (analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:194-208).

NineHack (analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali): 0x01 успех payload[0]==1 (100); 0x05 успех payload[0]==1 (221-237); 0x15 успех payload[0]==1 (262-278); 0x61 успех payload[0]==0 (303-317); 0x81 успех payload[0]==1 (343-358).

0xFB (analysis/java/fishbot/sources/A2/c.java:374-390): 0 ошибка пароля, 1 готовность, 2 завершено, 3 таймаут, 4 ошибка CRC всей прошивки, иное - код.

0x01 (analysis/java/fishbot/sources/A2/c.java:260-261): payload[0] не используется в декодере LostBot, но условие успеха в обработчике auth требует payload[0]==1 (analysis/java/fishbot/sources/C2/F.java:394).

Сводка расхождений статусов для 0x05/0x15: LostBot 0/2/пусто, NineHack 1, DanyScoot 1/2 (app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt:35). Три несовместимые трактовки, истина прошивки не установлена (missing: нужен RX-лог успешного unlock).

## Таймауты

| Параметр | LostBot | NineHack | DanyScoot |
|---|---|---|---|
| Ожидание ответа на команду | 3000 мс (analysis/java/fishbot/sources/cd/bn.java:117, analysis/java/fishbot/sources/C2/F.java:298) | нет (confirmed отсутствие) | нет отдельного ожидания, только auth 3000 (app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt:33) |
| Auth | 3000 мс (analysis/java/fishbot/sources/C2/F.java:379) | нет | 3000 мс (app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt:33, 245) |
| MTU fallback | 3000 мс (analysis/LostBot/smali/D2/s.1.smali:182-184) | нет MTU | 1500 мс (app/app/src/main/java/ru/dany/scoot/ble/BleTransport.kt:63, 210) |
| Подключение | 20000 мс (analysis/LostBot/smali/z2/i.smali:943-945) | не найдено | 20000 мс (app/app/src/main/java/ru/dany/scoot/ble/BleTransport.kt:55) |
| Запись | опрос очереди после onCharacteristicWrite (analysis/LostBot/smali/z2/g.smali:158) | нет | 2000 мс (app/app/src/main/java/ru/dany/scoot/ble/BleTransport.kt:58) |
| Дескриптор | не найдено | нет | 3000 мс (app/app/src/main/java/ru/dany/scoot/ble/BleTransport.kt:59) |
| Интервал спама | 500 мс, диапазон 2..макс (analysis/java/fishbot/sources/C2/F.java:255) | нет | 100 мс, 20..10000 (app/app/src/main/java/ru/dany/scoot/core/Prefs.kt:117-119) |
| Переподключение | 20 попыток, 250 мс, диапазоны 1..200 и 50..10000 (analysis/java/fishbot/sources/C2/F.java:252-253) | нет | 3 попытки, 5000 мс, 0..20 и 500..60000 (app/app/src/main/java/ru/dany/scoot/core/Prefs.kt:120-121, 58, 64) |

Таймаут 3000 мс у LostBot один и тот же для auth и для ожидания ответа (analysis/java/fishbot/sources/C2/F.java:298, 379 в связке с analysis/java/fishbot/sources/cd/bn.java:117).

## Расхождения с DanyScoot

1. Тип записи. DanyScoot выбирает NO_RESPONSE, только если бит PROPERTY_WRITE (0x08) снят (app/app/src/main/java/ru/dany/scoot/ble/BleTransport.kt:264, 510-514). LostBot выбирает NO_RESPONSE при установленном PROPERTY_WRITE_NO_RESPONSE (0x04) и DEFAULT иначе (analysis/LostBot/smali/z2/i.smali:1128-1145). При обеих установленных характеристиках приложения выберут разные типы: DanyScoot DEFAULT, LostBot NO_RESPONSE. Какие свойства реально отдаёт прошивка, неизвестно (missing: нужен GATT-дамп с реального скутера). NineHack всегда DEFAULT (analysis/NineHack/smali_classes3/com/timeylies/ninehack/controlActivity.smali:656-658).

2. Коды успеха 0x05/0x15. DanyScoot: {1, 2} (app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt:35, 593-596). LostBot: {0, 2, пусто} (analysis/java/fishbot/sources/A2/c.java:427-443). NineHack: {1}. Если прошивка отвечает так, как ожидает LostBot, то payload 00 DanyScoot покажет как ошибку, а payload 01 LostBot покажет как FAIL. Это расхождение отображения результата, а не формата кадра (confirmed расхождение трактовок).

3. 0x10 ERROR. DanyScoot читает payload[0] как отклонённую команду и payload[1] как причину (app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt:494-503), совпадает с LostBot (analysis/java/fishbot/sources/A2/c.java:264-268). NineHack видит payload[0]==1 как ошибку CRC (analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:194-208). Совпадение с LostBot подтверждено, расхождение только с NineHack.

4. Auth. Совпадает полностью: cmd 0x01, key 0, ASCII, успех payload[0]==1, session payload[1], таймаут 3000 (app/app/src/main/java/ru/dany/scoot/ble/ScooterCodec.kt:56-57, app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt:599-617, 245). Отличие: DanyScoot при ошибке auth оставляет state READY и пишет "неверный ключ" без повторов (app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt:611-616); LostBot при пустом ключе вообще не отправляет кадр (analysis/java/fishbot/sources/C2/F.java:365-370). В DanyScoot пустой ключ заменяется дефолтным при сохранении (app/app/src/main/java/ru/dany/scoot/core/Prefs.kt:36-41).

5. Payload unlock. DanyScoot unlock шлёт 10 байт 01 (app/app/src/main/java/ru/dany/scoot/ble/ScooterCodec.kt:59-60), спам шлёт 1129 + время (app/app/src/main/java/ru/dany/scoot/ble/ScooterCodec.kt:78-93). LostBot unlock и спам шлют только вариант с временем (analysis/java/fishbot/sources/C2/o.java:52-54, analysis/java/fishbot/sources/C2/F.java:1306). Вариант 10x01 подтверждён только NineHack (analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:1337-1348). Совместимость 10x01 с прошивкой Urent не подтверждена (missing).

6. Ключ по умолчанию. DanyScoot 4BKNwi77 (app/app/src/main/java/ru/dany/scoot/core/Prefs.kt:116), LostBot w4S3Hqn5 (analysis/java/fishbot/sources/D2/C0141b.java:81-85), NineHack 4BKNwi77 (analysis/NineHack/res/values/strings.xml:44). Пресеты DanyScoot содержат оба (app/app/src/main/java/ru/dany/scoot/ble/ScooterCodec.kt:54), выбор вручную.

7. Диапазон rand. DanyScoot 1..254 (app/app/src/main/java/ru/dany/scoot/ble/Frame.kt:61-62), LostBot 0..254 (analysis/java/fishbot/sources/K1/a.java:10-11), NineHack 1..254 (analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali:1155-1171). Совместимо, отличие только статистическое.

8. Тайминги и переподключение: см. таблицу таймаутов. Практические отличия: fallback MTU 1500 против 3000, интервал спама 100 против 500, попытки 3/5000 против 20/250.

9. Отсутствующие команды. В DanyScoot нет построителей для 0x34, 0x35, 0x55, 0x62, 0x64, 0x72-0x79, 0x86, 0xA0, 0xA1, 0xC3, 0xC5, 0xC7, 0xDC, 0xDE и статусов 0xFB/0xFC кроме отображения; payload этих команд в референсах отсутствует (missing). Каталог DanyScoot содержит только имена (app/app/src/main/java/ru/dany/scoot/ble/OpcodeCatalog.kt:15-68), что совпадает с именами LostBot по кодам.

10. Разбор входящих кадров. DanyScoot ищет заголовок, проверяет длину и CRC, при ошибке сдвигается на 1 байт (app/app/src/main/java/ru/dany/scoot/ble/Frame.kt:104-130). LostBot разбирает кадр строго с точной длиной 7 + len и без поиска в потоке (analysis/LostBot/smali/A2/f.smali:596, analysis/LostBot/smali/A2/f.smali:767-789; FishBot analysis/java/fishbot/sources/A2/f.java:71-82). Повторная синхронизация по потоку в LostBot не найдена (inferred). Поиск по потоку в DanyScoot - расширение, не противоречащее формату.

11. Demo. DanyScoot в demo-режиме отвечает сам с ключом 0x42 и session 0x42 (app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt:212-218, 699). LostBot demo тоже ставит 66 (analysis/java/fishbot/sources/C2/F.java:360). Не влияет на реальный протокол.

## Тестовые векторы

Все кадры построены при originalRand = 1 (wire rand = 0x33), key = 0x42 для команд после auth и key = 0 для auth. CRC проверен независимой побитовой реализацией.

Запросы:

| Имя | Cmd | Key | Payload | Кадр |
|---|---|---|---|---|
| auth ключ 4BKNwi77 | 01 | 00 | 34 42 4B 4E 77 69 37 37 | A3A40833010035434A4F76683636E7 |
| auth ключ w4S3Hqn5 | 01 | 00 | 77 34 53 33 48 71 6E 35 | A3A4083301007635523249706F3489 |
| unlock 10x01 | 05 | 42 | 01 x10 | A3A40A3343040000000000000000000092 |
| lock | 15 | 42 | 01 | A3A40133431400B0 |
| device info | 31 | 42 | 01 | A3A401334330004A |
| scooter info | 60 | 42 | 01 | A3A40133436100F9 |
| realtime | 63 | 42 | 01 | A3A40133436200AC |
| set 61 | 61 | 42 | 01 01 01 00 | A3A40433436000000001C6 |
| изъятие батареи | 81 | 42 | 05 | A3A4013343800429 |
| battery cover | C0 | 42 | 01 | A3A4013343C10017 |
| read RFID | 85 | 42 | 01 | A3A4013343840073 |
| spam unlock, ts=1700000000 | 05 | 42 | 01 31 31 32 39 65 52 F0 00 00 | A3A40A33430400303033386452F0010160 |

Ответы:

| Имя | Cmd | Payload | Кадр | Разбор |
|---|---|---|---|---|
| auth ok, session 2A | 01 | 01 2A | A3A402334300002BBC | успех, ключ 0x2A |
| auth fail | 01 | 00 | A3A4013343000139 | отказ |
| unlock status 00 | 05 | 00 | A3A4013343040102 | LostBot OK |
| unlock status 02 | 05 | 02 | A3A40133430403BE | LostBot ACK |
| unlock status 01 | 05 | 01 | A3A401334304005C | NineHack OK |
| set status 00 | 61 | 00 | A3A4013343600163 | успех |
| device info 42.00V locked fw 1.4.2 | 31 | 10 68 01 00 01 04 02 | A3A4073343301169000100050377 | напряжение 0x1068 = 4200, flags 01, locked true, версия 1.4.2 |
| scooter info | 60 | 57 02 00 00 0B B8 0F A0 | A3A408334361560301010AB90EA1BC | 87%, режим 2, 0.0 км/ч, 30000 м, 40000 м |
| realtime | 63 | 57 00 00 07 D0 01 | A3A40633436256010106D100D9 | 87%, 0.0, 20.00, flags 01 |
| RFID | 85 | 12 34 56 78 | A3A404334384133557797B | 0x12345678 |
| ERROR отклонён 05, причина 03 | 10 | 05 03 | A3A4023343110402D9 | rejected 0x05, reason 0x03 |
| pack request 5 | FC | 00 05 | A3A4023343FD010487 | индекс 5 |
| FW complete | FB | 02 | A3A4013343FA03FB | завершено |

Проверка CRC: для всех строк выше crc совпадает у табличной и побитовой реализаций; CRC8("123456789") = 0xA1.

Вектор auth позволяет проверить и wire-поле: key=0 при originalRand=1 даёт байт 0x01 на позиции 4, cmd 0x01 даёт 0x00 на позиции 5.

## Unknowns

1. Реальный HCI-лог или UART-обмен Urent отсутствует. Совместимость формата с прошивкой Urent подтверждена только по двум сторонним клиентам (inferred).
2. payload 0x05: 10x01 или метка времени. Нужен RX-лог успешного unlock от Urent.
3. Коды успеха 0x05/0x15. Нужен RX-лог успешного unlock и lock.
4. Семантика поля key во входящих кадрах (эхо сессионного ключа или 0). В LostBot key не используется (analysis/java/fishbot/sources/A2/f.java:88-95), в NineHack тоже; нужен лог.
5. Поведение при повторном 0x01 и срок жизни сессии. Данных нет.
6. Payload команд без построителей: 0x34, 0x35, 0x37, 0x40, 0x50, 0x52-0x56, 0x62, 0x64, 0x72-0x79, 0x86, 0x90-0x97, 0xA0, 0xA1, 0xC3, 0xC5, 0xC7, 0xDC, 0xDE, 0xFB, 0xFC. В референсах только декодеры ответов (analysis/java/fishbot/sources/A2/c.java:258-398).
7. Единицы полей 0x31: LostBot делит u16 на 100 и подписывает как напряжение, но физическое подтверждение отсутствует (inferred).
8. Точный мэппинг headlight/mode/throttle в LostBot для 0x61 не найден: есть только payload 02 00 00 00 и 01 00 00 00 (analysis/java/fishbot/sources/D2/C0141b.java:39-41), что не совпадает с 4-байтовым форматом NineHack. Нужен захват 0x61 из UI LostBot.
9. Наличие у Urent BLE вообще: в APK нет BLE-кода, канал мог быть вырезан или обслуживаться другим приложением (missing: нужен захват трафика с реальным приложением Urent).

## Источники

- analysis/java/fishbot/sources/A2/f.java: рама, CRC, разбор.
- analysis/java/fishbot/sources/A2/c.java: каталог опкодов, декодеры, статусы.
- analysis/java/fishbot/sources/C2/F.java: auth, отправка, таймауты, настройки, demo.
- analysis/java/fishbot/sources/C2/o.java: готовые вызовы, battery cover, спам unlock.
- analysis/java/fishbot/sources/D2/C0141b.java: построители payload, генерация ключа.
- analysis/java/fishbot/sources/z2/l.java: массивы ключа.
- analysis/java/fishbot/sources/J1/e.java, analysis/java/fishbot/sources/K1/a.java: источник rand.
- analysis/java/fishbot/sources/cd/bn.java: константа 3000 мс.
- analysis/LostBot/smali/z2/i.smali: UUID, скан, подключение, чанк, тип записи.
- analysis/LostBot/smali/z2/g.smali: приём уведомлений, onMtuChanged.
- analysis/LostBot/smali/D2/s.1.smali: requestMtu 247, fallback 3000, CCCD.
- analysis/LostBot/smali/b/j.smali: включение уведомлений.
- analysis/NineHack/smali_classes3/com/timeylies/ninehack/CommandCreator.smali: независимая сборка рамы, статусы.
- analysis/NineHack/smali_classes3/com/timeylies/ninehack/Crc8.smali: таблица и compute.
- analysis/NineHack/smali_classes3/com/timeylies/ninehack/controlActivity.smali: UUID, запись, auth, ключ из prefs.
- analysis/NineHack/res/values/strings.xml: ключ по умолчанию.
- analysis/report_urent.md: Urent 1.90.2 и 2.7.0, отсутствие BLE, разрешения.
- analysis/network.md: useBluetooth=false, серверная модель.
- analysis/fishbot_handoff.md: принадлежность analysis/payload и analysis/payload_apktool плагину FishBot.
- app/app/src/main/java/ru/dany/scoot/ble/Frame.kt: рама DanyScoot.
- app/app/src/main/java/ru/dany/scoot/ble/ScooterCodec.kt: команды и парсеры DanyScoot.
- app/app/src/main/java/ru/dany/scoot/ble/OpcodeCatalog.kt: каталог DanyScoot.
- app/app/src/main/java/ru/dany/scoot/ble/BleTransport.kt: транспорт DanyScoot.
- app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt: auth, статусы, телеметрия, спам.
- app/app/src/main/java/ru/dany/scoot/core/Prefs.kt: дефолты DanyScoot.
