# LostBot/FishBot (ru.lostbotapp): BLE-протокол, спам разблокировки, прошивка, лицензирование, UI

Сводный индекс: analysis/references.md. Полный протокол: analysis/protocol_full.md.

## 0. Артефакты и источники

- APK: FishBot.apk (apktool 3.0.3), пакет FishBot.en.Milky, versionCode 1, versionName 1.0.0, minSdk 17, targetSdk 28, compileSdk 35 (analysis/FishBot/apktool.yml:1-11, AndroidManifest.xml:2).
- Второй артефакт: LostBot.apk (ru.lostbotapp.Free 1.2.1, 2 536 378 байт, SHA-256 D1D8713820F3EBC480236E113020C0C547B94453D416EDE894D27A4205245882). Классы A2/c, A2/f, z2/i, D2/b идентичны FishBot; C2/F отличается логированием и полями, протокол тот же.
- Java-декомпиляция jadx 1.5.6: analysis/java/fishbot/sources (ниже пути без префикса).
- smali: analysis/FishBot/smali, smali_classes2 (используется там, где jadx не декомпилировал класс или коллизия регистра).
- Манифест: activity ru.lostbotapp.MainActivity (exported, singleTop), service ru.lostbotapp.service.SpamService (exported=false, foregroundServiceType=connectedDevice), разрешения BLE/INTERNET/POST_NOTIFICATIONS + добавленный патчем набор (BIND_VPN_SERVICE, MANAGE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES, REQUEST_INSTALL_PACKAGES и др., AndroidManifest.xml:3-28).
- Приложение - Compose-UI без строк в ресурсах (все надписи в коде), поэтому тексты ниже приведены как есть.

Термины: контроллер - C2.F (два экземпляра: live и demo), ключ сессии - CommunicateKey (поле f443j), кадр - формат A2/f.

## 1. BLE-транспорт

Класс менеджера - z2.i (единственный владелец BluetoothGatt).

UUID (z2/i.java:114-127):
| Роль | UUID |
| Сервис (Nordic UART) | 6e400001-b5a3-f393-e0a9-e50e24dcca9e |
| Write characteristic | 6e400002-b5a3-f393-e0a9-e50e24dcca9e |
| Notify characteristic | 6e400003-b5a3-f393-e0a9-e50e24dcca9e |
| CCCD дескриптор | 00002902-0000-1000-8000-00805f9b34fb |

Поля (z2/i.java:131-194):
- f10850g = 23 (стартовое значение MTU), f10853j = true (фильтровать сканирование по сервисному UUID).
- f10845b - Handler(main), f10856m - очередь операций (p055t1.j), f10847d - BluetoothGatt, f10848e/f10849f - write/notify characteristics.
- Регистрация ресивера STATE_CHANGED: API 33+ registerReceiver(..., RECEIVER_NOT_EXPORTED), ниже - с пермишеном <package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION.

Сканирование z2/i.b() (z2/i.java:234-262):
- stopScan, затем если f10853j - ScanFilter.Builder().setServiceUuid(6e400001...), ScanSettings setScanMode(2)=SCAN_MODE_LOW_LATENCY, setCallbackType(1).
- Лог "Starting BLE scan filterByService=" + флаг; SecurityException - "Bluetooth permission denied".
- Результаты: z2/h.java (ScanCallback) -> main handler -> z2/i.a (z2/i.java:196-232): проверка сервисного UUID в ScanRecord, имя устройства (ScanRecord -> device.getName() -> "Unknown"), публикация z2.k(address, name, rssi, hasService, device) в C2.l-приемник.

Подключение z2/i.e(k) (z2/i.java:313-342):
- Остановка сканирования, закрытие старого GATT, сброс MTU на 23 и очереди.
- connectGatt(app, false, callback, 2) - TRANSPORT_LE; watchdog 20 000 мс (z2/i.java:333) с сообщением о таймауте подключения.

GATT-цепочка (z2/g.java):
- onConnectionStateChange (z2/g.java:44-89): при STATE_CONNECTED - "Connected, negotiating MTU / discovering services", очередь C0174s (запрос MTU) и B2.a (включение нотификаций); при STATE_DISCONNECTED - "Disconnected: GATT status N"/"clean", при статусе 133 (GATT_ERROR) - отложенный disconnect/close через 150 мс (z2/g.java:81).
- onCharacteristicChanged (z2/g.java:23-33): копия value -> main handler -> C2.F.u (парсинг кадра).
- onCharacteristicWrite/onDescriptorWrite/onMtuChanged - снятие блокировки очереди.

MTU и нотификации (smali D2/s.1.smali, класс D2/s):
- requestMtu(0xF7) = 247, таймаут 0xbb8 = 3000 мс (D2/s.1.smali:128-131, :182).
- CCCD: ENABLE_NOTIFICATION_VALUE (D2/s.1.smali:59); API < 33 - setValue + writeDescriptor.

Запись z2/i.i(byte[]) (z2/i.java:390-451):
- Размер чанка = max(20, MTU-3) = 20 байт при MTU=23.
- Тип записи: PROPERTY_WRITE(4) есть -> 1 (WRITE_TYPE_DEFAULT), иначе 2 (WRITE_TYPE_NO_RESPONSE) (z2/i.java:401).
- API 33+: B0.AbstractC0116d.a(gatt, char, value, writeType); иначе setWriteType + setValue + writeCharacteristic.
- Все операции (write, discover, MTU, CCCD) выполняются строго последовательно через очередь f()/g() (z2/i.java:344-373).
- Фреймы длиннее 20 байт режутся на чанки и склеиваются на стороне устройства (для этого же рассчитан payload <= 255).

Состояния линка C2.k (C2/k.java): DISCONNECTED(f522d), SCANNING(f523e), CONNECTING(f524f), CONNECTED(f525g), AUTHENTICATED(f526h), ERROR(f527i); v() = AUTHENTICATED, w() = CONNECTED|AUTHENTICATED.

## 2. Рукопожатие и аутентификация

Формат кадра A2/f (A2/f.java:15-96), CRC8 - таблица f308a (A2/f.java:13), вычисление b() (A2/f.java:34-40):

| Смещение | Поле | Формула |
| 0 | заголовок | 0xA3 |
| 1 | заголовок | 0xA4 |
| 2 | длина payload | len (<=255, иначе "Payload too long: N") |
| 3 | rand | (rnd + 50) & 255, rnd = J1.e.f2741e.b() (A2/f.java:19) |
| 4 | ключ | (key ^ rnd) & 255 |
| 5 | команда | (cmd ^ rnd) & 255 |
| 6..6+len-1 | payload | payload[i] ^ rnd |
| 6+len | CRC8 | CRC8 по всем байтам 0..6+len-1 |

Декодер A2/f.d (A2/f.java:71-96): длина b.length == 7+len ("Invalid frame length"), rand = (b[3]-50)&255, cmd = b[5]^rand, key = b[4]^rand, payload[i] = b[6+i]^rand, CRC8 сверяется с последним байтом. Возвращает A2/a(cmd f292a, payload f293b, key f294c); 4-й аргумент конструктора игнорируется (smali A2/a.smali - только 3 поля).

Рукопожатие C2/F.a (C2/F.java:334-418):
- Демо: f443j = 66, статус AUTHENTICATED, ошибка сброшена (C2/F.java:357-364).
- Live: ключ из C0141b.b() (DI-провайдер, case 27 -> z2.l.f10881c); пустая строка - "BLE key is empty".
- Отправка: cmd 0x01, payload = байты ключа (ASCII), ожидание ответа через F.S(1, bn.f4730m=3000 мс) (C2/F.java:376-379).
- Успех: payload.length >= 2 и payload[0] == 1 -> f443j = payload[1] & 255 (ключ сессии), статус AUTHENTICATED, сброс ошибки, и если установлен флаг отложенного старта f424P - запуск Q() (C2/F.java:392-404).
- Ошибки: null - "Handshake timed out" (C2/F.java:408), иначе "Handshake failed payload=<hex>" (C2/F.java:412) или "Handshake failed: <msg>".
- Повторный запуск: подпись Handshake/Re-handshake в D2/C0170p0.java:94.

Проводной ключ для update (передаётся в каждый кадр как key):
- z2/l.java:10-16: массивы f10879a = {44,204,112,244,118,177,51,241}, f10880b = {91,231,29,154,66}, ленивый f10881c = C0141b(29).
- D2/C0141b.java:80-85: bArr[i] = (f10880b[i%5] ^ f10879a[i]) ^ ((i*31)&255), i=0..7 -> ASCII "w4S3Hqn5".
- Демо-ветка: 66 или "DEMOKEY" как ключ прошивки (C2/F.java:241,246).

Гарды: D() (C2/F.java:564-577), B() (560-562), C() (272-331), b() (485-550), P() (776-836), Q() (838-869) требуют v() (AUTHENTICATED), иначе статус ERROR и сообщение "Authenticate first".

## 3. Спам разблокировки и SpamService

Запуск C2/F.Q() (C2/F.java:838-869):
- Проверки: v() иначе "Authenticate first"; entitlement f438e.b() == false -> "Subscription required" (в этой сборке недостижимо, см. раздел 8); уже запущен (r()) -> выход.
- Сброс счётчика f446m = 0, флаг f447n = true, f420L = 0.
- Лог "Unlock spam started (N ms)", событие unlock_spam_start {interval_ms}.
- Live: startForegroundService(SpamService); старт цикла C2/x и первого тика y().

Тик C2/F.y() (C2/F.java:1296-1321):
- Защита f419K от реэнтрантности; gate по entitlement (нет - R()).
- Кадр: cmd 0x05, payload ниже; t = epoch-секунды (System.currentTimeMillis()/1000).

| Смещение | Байт | Значение |
| 0 | 1 | константа |
| 1..4 | 49,49,50,57 | ASCII "1129" |
| 5..8 | t>>24, t>>16, t>>8, t&255 | UNIX-время, секунды, big-endian |
| 9 | 0 | терминатор |

- Счётчик t()++ (f446m), f420L = текущее время; демо - H(false) (снятие лока).

Цикл C2/x (C2/x.java:46-65): пока корутина активна и r() -> delay(s()) -> N().

Планировщик C2/F.N() (C2/F.java:719-733):
- Условия r() && v() && !f419K; если (now - f420L) + 5 < s() - выход (дедупликация двойного тика).
- Иначе y(); если entitlement выключен - лог "Unlock spam stopped: subscription expired", событие unlock_spam_stopped_no_entitlement, R().

Останов C2/F.R() (C2/F.java:871-890): отмена корутины, f447n = false, событие unlock_spam_stop {sent}, stopService(SpamService).

Интервал s() = f445l (C2/F.java:198, дефолт 500 мс; prefs unlock_spam_interval_ms, clamp 2..3000 (cd/ue.java:116 f5847d=3000, C2/F.java:255).

SpamService (ru/lostbotapp/service/SpamService.java):
- Канал "lostbot_spam", id 42, важность 2, старт foreground 42 с типом connectedDevice (SpamService.java:444-455).
- Заголовок уведомления "Unlock spam active" (SpamService.java:99), текст "<Scooter> · N sent · every M ms" (SpamService.java:82), action "Stop spam" -> ru.lostbotapp.action.STOP_SPAM (SpamService.java:74).
- onStartCommand (SpamService.java:437-462): действие STOP_SPAM -> b().R() + stopForeground + stopSelf; иначе createNotificationChannel + startForeground + корутина.
- Контроллер берётся из LostBotApplication.f9244d (live) (SpamService.java:411-420).

UI-точки входа: "Spam unlock" (p027j0/c.java:795), "Stop spam" (p027j0/c.java:1000), "Start spam unlock" (D2/C0170p0.java:225); быстрая команда C0141b case 13 (C2/F.java:48) отправляет тот же кадр 0x05.
Автопродолжение: prefs auto_resume_spam (C2/F.java:254, D2/C0140a0.java:206) - при реконнекте f424P = true -> Q() после успешного хэндшейка (C2/F.java:398-401).

## 4. Кодирование команд (TX)

Каталог опкодов A2/c.f299a (A2/c.java:19-36), A2/e(code, name, direction, description), направление A2/d (BIDIRECTIONAL f300d, SET_ONLY f301e, NOTIFICATION f302f).
Человекочитаемое описание TX-кадра - A2/c.b(int, byte[]) (A2/c.java:50-256), RX-ответа - A2/c.c(int, byte[]) (258-399); формат кода - A2/c.f (417-425).

Пути отправки:
- C2/F.D(A2/h, Integer) (C2/F.java:564-577): кадр + лог "TX CMD=%02X RAW ..." + z2/i.i; в демо запись не выполняется.
- C2/F.B(A2/h, Integer) (C2/F.java:560-562 -> корутина c, C2/F.java:485-550): отправка + ожидание ответа, событие ble_command {cmd, error}.
- C2/F.C (C2/F.java:272-331): отправка вручную введённой команды с записью в журнал (C0125f), таймаут одиночного ответа bn.f4730m = 3000 мс (C2/F.java:298), лог "TX ... payload=... raw=... <- <описание>"; ошибка характеристики - "BLE write failed".

Команды, которые строит само приложение (D2/C0141b.java, вид A2.h(cmd, payload)):
| case | cmd | payload | Смысл |
| 4 | 21 (0x15) | 01 | Lock |
| 5/16 | 49 (0x31) | 01 | Device info / IoT config |
| 6 | 96 (0x60) | 01 | Scooter info (телеметрия) |
| 7 | 99 (0x63) | 01 | Realtime telemetry |
| 8 | 192 (0xC0) | 01 | Battery cover unlock |
| 9 | 97 (0x61) | 02 00 00 00 | Set: свет off, режим keep |
| 10 | 97 (0x61) | 01 00 00 00 | Set: свет on, режим keep |
| 11 | 162 (0xA2) | 01 | Find car (звук) |
| 12 | 162 (0xA2) | 06 | Find car (вариант) |
| 13 | 5 (0x05) | 01 31 31 32 39 t..t 00 | Unlock spam (тот же кадр, что в тике) |
| 14 | 250 (0xFA) | пусто | Firmware device info / IoT read |
| 15 | 146 (0x92) | 01 | IoT module restart |
| 17 | 133 (0x85) | 01 | Read RFID card |
| 18 | 130 (0x82) | 01 00 7C 6A E8 00 00 00 | Color light: mode=1 (Solid), RGB #7C6AE8 |
| 19 | 198 (0xC6) | 01 | Switch next SIM |
| 20 | 196 (0xC4) | 01 | Update certificate |

Дополнительные места сборки команд:
- D2/C0166n0.java:274 и :401 - cmd 130: [mode, 0, R, G, B, 0, 0, 0] из RGB-слайдеров ("Apply colour").
- D2/C0175s0.java:36 - cmd 134: 8 байт (два RFID-идентификатора по 4 байта big-endian).
- D2/C0175s0.java:44 - P(A2.j.OTA_UPDATE, "Firmware", data).
- smali D2/s.1.smali:382-390 - "Set diameter" (D2/C0181v0.java:404): cmd 100, payload [v>>8, v&255], значение 100..400 мм (D2/C0181v0.java:308-339).
- D2/C0148e0.java (сервисные кнопки): case 0 - 145 [1] (Power OFF), case 1 - 115 [1,1] (Meter reset), case 2 - 84 [1] (Clear BT users), case 3/4 - 220 (AVAS on/off, payload из I1.a.i), case 5 - 146 [1] (IoT restart), case 6 - 52 + ASCII account, case 7 - 53 + ASCII password, case 8 - 197 [slot], default - сохранение prefs transmission_update_key (C2/F.java:74-76).
- Настройки IoT-конфига: cmd 0xFA (250) на чтение (C2/F.java:456), ответ 251 со статусом 1|2 -> totalPack = b1<<8|b2, devType = b5, далее запрос чанков cmd 252 [idxHi, idxLo, devType] (C2/F.java:1323-1329). Таймаут чтения 5000 мс (C2/A.java:38), ошибка "Read config failed: timeout" (C2/F.java:1040).

## 5. Декодирование ответов (RX)

Приём: z2.i -> C2.F.u (C2/F.java:1102-1281):
- Лог "RX <opcode> CK=<key> PAYLOAD=<hex> -> <декод>" (C2/F.java:1116).
- C2.F.h (C2/F.java:1006-1031) раскладывает состояние: cmd 49 -> IoT-сеть (если payload состоит из печатных символов и содержит ':') -> C0123d через q2.d.P(строка), иначе C0122c(voltage u16, flags, "maj.min.patch", locked) (C2/F.java:1009-1022); cmd 96 -> I(battery, mode, speed/10, trip*10, range*10) (1023-1026); cmd 133 -> RFID id 4 байта big-endian + hex (1027-1031).
- Сопоставление ответа с журналом: по cmd, либо по (cmd & 127) (учитывается флаг 0x80), статусы PENDING/ANSWERED; ответ 0x10 (ERROR) помечает исходную команду как ERROR с текстом "Scooter rejected (ERROR 0x10 status=0xNN)" (C2/F.java:1165-1262).
- Ожидание конкретного ответа: C2.F.S(cmd, timeout, cont) (C2/F.java:893-952) ставит p(cmd, C0329m) в f425Q; при приходе кадра deferred завершается (C2/F.java:1263-1277).
- Прогресс прошивки nо ответам 251/252 (C2/F.java:1137-1164, см. раздел 6).
- После обработки, если идёт спам - N() (C2/F.java:1278-1280).

Декодер A2/c.c (A2/c.java:258-399), ключевые форматы:
| cmd | ответ | Разбор |
| 1 (0x01) | handshake ACK | "handshake ACK, communicateKey=0xNN" (b1) |
| 5/21 | unlock/lock | b0==2 -> "ACK", иначе status |
| 16 (0x10) | ERROR | "ERROR: rejected cmd=0xNN reason=0xNN" |
| 49 (0x31) | device info | V=u16/100 В, flags (bit0==0 -> LOCKED), fw=b4.b5.b6; текст с ':' -> IoT-сеть |
| 52/53 | mqtt account/password | строковые ACK |
| 80 (0x50) | gsm toggle | b0: 0 DISABLE, иначе ENABLE |
| 83 (0x53) | gsm auto-detect | ACK/status |
| 96 (0x60) | scooter info | battery %, mode, speed u16/10 км/ч, trip u16*10/1000 км, range (b6<<8|b7)*10/1000 км |
| 99 (0x63) | realtime | battery %, speed u16/10, current u16/100 A, flags (требуется >= 6 байт) |
| 100 (0x64) | wheel diameter | u16 |
| 130 (0x82) | color light | "mode=N rgb=#RRGGBB" (b0, b2..b4) |
| 133 (0x85) | RFID id | b0..b3 big-endian + hex |
| 134 (0x86) | delete RFID | два id (b0..b3)/(b4..b7) |
| 117 (0x75) | audio | b0==255 -> "audio: OFF (b1)", иначе "audio: ON (b0)" |
| 145 (0x91) | power | b0==1 -> "power: OFF (shutdown)" |
| 195 (0xC3) | accessory | b0: 1 saddle, 3 tail-box, 8 helmet; b1: 1 open, 2 close |
| 251 (0xFB) | transmission | 0 password error, 1 READY, 2 COMPLETE, 3 timeout, 4 total CRC error |
| 252 (0xFC) | pack request | idx = b0<<8|b1 |
| 250 (0xFA) | firmware info | ASCII-строка |

Журнал обмена (C2):
- C0125f (ManualCommandEntry): id, timestamp, cmd, payload, rawFrame, usedCommunicateKey, expectsResponse, status, errorMessage, responses (C2/C0125f.java:44-57); кольцевой буфер 200 записей (C2/F.java:294-296).
- EnumC0128i: PENDING f513d, ANSWERED f514e, TIMEOUT f515f, ERROR f516g.
- EnumC0124e (потоки): TX f492d, RX f493e, DECODED f494f; логи строкой "[<Instant.now()>] msg", буфер 500 (C2/F.java:954-972).
- Subsystem R()/y() добавляют записи в журнал ручных команд (C2/F.java:325).

## 6. Прошивка/transmission и CRC16

CRC16 (C2/F.java:796-807 и 587-597): init 0xFFFF, отражённый полином 0xA001 (XOR 40961), без финального XOR: для каждого байта crc ^= b; 8 раз: if (crc&1) crc = (crc>>1)^0xA001 else crc >>= 1.

Запуск C2/F.P(A2.j, label, data) (C2/F.java:776-836):
- Проверки: v() -> "Authenticate first"; x() -> "A transmission is already in progress"; пустые данные -> "Nothing to send".
- Паддинг нулями до кратности 16 (C2/F.java:793-794), CRC16 по всему буферу, нарезка на блоки по 16 байт.
- START-кадр: cmd 0xFB, payload (C2/F.java:825-829):

| Смещение | Байт | Значение |
| 0 | kind | 0 = OTA_UPDATE, 1 = SYSTEM_CONFIG (A2/j.java:5-6) |
| 1..2 | totalPack | big-endian, число 16-байтных пакетов |
| 3..4 | crc16 | big-endian, CRC16 всего образа |
| 5 | deviceType | n(), prefs transmission_device_type (clamp 0..255) |
| 6.. | ключ | байты prefs transmission_update_key (может быть пустым) |

- Событие и лог "TX transmission START <label> transType=.. deviceType=.. totalPack=.. crc16=0xNNNN".
- Прогресс C2/G: label, kind, totalPack, sentPack, status (H: RUNNING f466d, SUCCESS f467e, FAILED f468f), message (C2/G.java:26-51).

Отправка пакета C2/F.E(int idx) (C2/F.java:579-617):
- buf = [idx>>8, idx&255] + chunk(16 байт) = 18 байт;
- crc16 по buf, отправка buf + [crc>>8, crc&255] = 20 байт ("TX pack[i] RAW");
- обновление прогресса "Sent pack i+1/N"; ошибка - "Pack write failed".
- Вызывается по запросу устройства: ответ 0xFC [idxHi, idxLo] -> E(idx) (C2/F.java:1158-1163).

Статусы в C2/F.u (C2/F.java:1140-1157): 251 b0: 0 - "Password / key error"; 1 - "Device ready"; 2 - g() "Transmission complete"; 3 - "Device receive timeout"; 4 - "Total CRC error".
Завершение C2/F.g (C2/F.java:996-1004) - статус SUCCESS "Complete"; отмена C2/F.f (984-994) - "Cancelled".
Демо-передача C2/y (C2/y.java:57-123): задержка 300 мс, затем по одному пакету каждые 250 мс (E(i)), после последнего - g().
Модель A2.i (A2/i.java, smali A2/i.smali): фактически хранит только totalPack (f312b) и список 16-байтных чанков (f311a); kind/deviceType/key/crc в объекте не сохраняются (пересчитываются в P()).

Чтение конфига IoT модуля (0xFA/0xFB/0xFC) в приложении (C2/F.java:437-482, C2/A.java, C2/F.java:1118-1136): cmd 250 -> устройство 251 (b0: 1 или 2) с totalPack и devType -> поштучные запросы 252. Наблюдение: накопленный список f429U после инициализации нигде не читается (C2/F.java:237,450-451,1125), карточка IoT в UI питается текстовым ответом cmd 49 (C2/F.java:1009-1015).

## 7. Демо-режим

Запуск: LostBotApplication.onCreate (LostBotApplication.java:73-98):
- y2.s f9246f (entitlement store), y2.y f9247g (телеметрия), C0141b(27) как провайдер ключа.
- f9244d = new F(this, false, ...) - live; f9245e = new F(this, true, ...) - demo (LostBotApplication.java:81-82).
- f9248h = prefs use_demo (LostBotApplication.java:89); контроллер выбирается в UI (p027j0/c.java:1194-1206).
- Телеметрия: app_launch (LostBotApplication.java:91), app_foreground через registerActivityLifecycleCallbacks (LostBotApplication.java:90,97).

Инициализация demo-контроллера (C2/F.java:240-247): f443j = 66, статус AUTHENTICATED, IoT C0122c(5230, 1, "1.2.3", false), телеметрия I(69, 2, 18.7, 1240, 15300), deviceType A = 7, ключ прошивки B = "DEMOKEY".

Автоответы C2/F.M(A2/h, Integer) (C2/F.java:655-717):
- cmd 5/21: [1]; cmd 49: [v>>8, v&255, locked?0:1, 0, maj, min, patch] из C0122c;
- cmd 96: [battery, mode, speed*10 hi/lo, trip/10 hi/lo, range/10 hi/lo] из I;
- cmd 133: [18, 52, 86, 120]; cmd 250: ASCII "FW 1.2.3 demo"; прочее: [1].
- Ручные команды в демо проходят через C() (C2/F.java:304-307) и M; P() в демо запускает C2/y (C2/F.java:830-832).
- Демо IoT-сеть (C2/F.java:421-482): задержка 400 мс, затем C0123d("860123456789012","8901234567890123456","internet","52.28.100.10","1883","1","1.2.3", "IMEI:...").
- Спам в демо: y() -> H(false) (снятие лока), сервис не стартует (C2/F.java:858-862).

## 8. Лицензирование (entitlement), API, телеметрия

Хранилище y2.s (y2/s.java):
- Поля: f10626a EntitlementDTO, f10627b/f10628c - булевы состояния, f10629d, f10630e = A2.b(15) (HTTP-клиент), f10631f = y2.t (обёртка prefs), f10632g = SharedPreferences "lostbot_prefs".
- Инициализация (y2/s.java:38-67): вход считан, если есть lb_access_token и lb_refresh_token; EntitlementDTO десериализуется из lb_entitlement_json.
- Методы в этой сборке заглушены: e() - пусто; f() - true; g() - true; h()/i() - Unit; j() - true; k(boolean) - запись состояния; l(ArrayList) - false; d() - Base64 "8J+QnyBGaXNoQm90IMK3IExpZmVUaW1lIOKcqA==" = "EMOJI FishBot · LifeTime OK" (y2/s.java:104-138).
- Карта кодов redeem c(String) (y2/s.java:71-89): license_already_redeemed -> "This key was already used", invalid_code -> "Invalid code", device_limit_reached -> "Device limit reached", invalid_license -> "Invalid license key", expired_code -> "Code expired - request a new one".
- Следствие: g() всегда true, поэтому проверки подписки (C2/F.java:722,845,1299; D2/T0.java:75) всегда проходят, а ветки "Subscription required"/"Unlock spam stopped: subscription expired" недостижимы.

API-клиент A2/b (case 15, A2/b.java:78-89): база https://lostbotapp.ru, OkHttp, Content-Type application/json; заголовок Authorization: Bearer <token> (A2/b.java:119-121); извлечение ошибки из JSON-поля detail (A2/b.java:96-117).
| Метод | HTTP | Тело/ответ |
| F(List, token, cont) | POST /me/telemetry/events | TelemetryBatch в JSON |
| k(String, cont) | GET /me/entitlement | EntitlementDTO |
| w(String, token, cont) | POST /me/redeem | {code: ...} |
| x(String, cont) | POST /auth/refresh | RefreshResponse |

Модели ru/lostbotapp/auth: EntitlementDTO(entitled, plan, status="none", source, expiresAt, seats, features) (EntitlementDTO.java:16-27,152), TokenResponse(accessToken, refreshToken, expiresIn), RefreshResponse, TelemetryEvent(type, severity, payload, capturedAt, clientEventId), TelemetryBatch, TelemetryUploadResponse.

Телеметрия y2.y (y2/y.java):
- b() (y2/y.java:174-199): обрезка type до 64 символов, время Instant.now() с точностью до секунды, очередь до 2000 событий.
- Отправка a() (y2/y.java:63-172): требует s.f() (true), берёт до 200 событий и вызывает s.l(list) - заглушен (сразу возвращает false, без приостановки), поэтому события вычитываются из очереди и теряются, в сеть не уходят.
- События контроллера: app_launch/app_foreground/login_success (LostBotApplication.java:91-97), unlock_spam_start (C2/F.java:857), unlock_spam_stop (C2/F.java:882), unlock_spam_stopped_no_entitlement (C2/F.java:729), ble_command (C2/F.java:513,529-534).

Ключи lostbot_prefs: show_all_scan_devices, show_scan_debug_log, auto_reconnect_enabled, auto_reconnect_max_attempts (20, clamp 1..200), auto_reconnect_delay_ms (250, clamp 50..10000), auto_resume_spam, unlock_spam_interval_ms (500, clamp 2..3000), transmission_device_type, transmission_update_key, use_demo (C2/F.java:249-259, 619-641; LostBotApplication.java:89), плюс auth-ключи lb_access_token, lb_refresh_token, lb_entitlement_json, lb_entitlement_verified_at (y2/s.java:55-66,97). Магазин ключей BLE - C2/C0121b (ble_saved_keys/ble_selected_key).

## 9. Экраны UI

Общие точки:
- MainActivity: setContent с x2.f(app, 1) (MainActivity.java:161-167), запрос BLUETOOTH_SCAN/CONNECT (или ACCESS_FINE_LOCATION до API 31) и POST_NOTIFICATIONS (MainActivity.java:136-158).
- Корневой composable p027j0/c.m (p027j0/c.java:1177-1229): локали AbstractC0143c.f858a ScooterController, f859b EntitlementStore, f860c AppRouter (D2/C0145d), f861d Application; выбор live/demo по use_demo.
- Четыре вкладки: Control, Telemetry, IoT, Console (p027j0/c.java:1023-1025,1066, класс D2/W0).
- Шелл D2/C.java case 3 (D2/C.java:72-153): верхняя панель (AppRouter.f864a -> p027j0/c.i с вкладками, иначе q2/l.b), диалоги: f866c -> настройки q2/l.c ("Settings", содержимое D2/C0166n0), f865b -> "Connect" q2/d.k (D2/C0140a0 case 2 + экран поиска D2/E case 3), f867d -> лист "License" a/a.s (a/a.java:3705-3749).

Control:
- Карточки и кнопки p027j0/c.java: Lock/Unlock (опкоды 21/5), "Spam unlock" (:795), "Stop spam" (:1000), Accessories, Sounds, Colour light, AVAS, Power, RFID, Service; список быстрых команд D2/AbstractC0142b0.java:85.
- Подэкраны D2/C0166n0.java: case 0 - RGB-слайдеры, режимы Solid/Breathe/Flash (:224), "Apply colour" (:304); case 1 - APN/User/Password/APN mode, "Write APN" (:323,431).
- Настройки (нижний лист "Settings", D2/C0166n0.java:440-487): Subscription (карточка D2/T0.java с иконкой CheckCircle и текстом sVar.d()), далее "Scan" (:476), "Auto-reconnect" (:478), "Key for Firmware" (:480), футер decode("RmlzaEJvdCDCtyB2MS4wLjA=") = "FishBot · v1.0.0" (:484).
- Заголовки D2/K.java: "Settings" (:30), "Refresh" (:38).

Telemetry:
- D2/E.java: карточки "Lock"/"Trip"/"Firmware" (:259-265), состояния поиска "Searching for scooters...", "No scooters found", "Tap a scooter to connect" (:220).
- D2/C0168o0.java: "Scan for scooter" (:99); D2/X0.java: "Scan for scooter" (:100).
- D2/C0170p0.java: "Handshake"/"Re-handshake" (:94), "Start spam unlock" (:225), сетевые поля IoT (IMEI/ICCID/Server/IP mode/Version, APN :281).
- D2/C0187z.java "Battery" (:62), D2/C0181v0.java "Wheel diameter" (:291) и "Set diameter" (:404).
- D2/C0177t0.java: RFID - "Read card" (:139,257), ID part A/B, Account, "Set account" (:407).

IoT:
- D2/C0160k0.java: IMEI, GSM enable/disable ("Enable" :120), "Restart IoT module" (:377), Slot, "Switch SIM" (:489,616), "Next SIM" (:519,651), "Update cert" (:530,665), примечание AVAS.

Console:
- D2/z0.java "Log" (:113); D2/Y.java "Custom command" (:58), "Pick opcode" (:137), "Send command" (:171); D2/y0.java "Firmware file" (:69), "Choose file" (:222), "Flash firmware" (:234).
- D2/C0140a0.java: case 0 - журнал отправленных команд (+"Clear" в D2/C.java:69), case 1 - "Device" (:111), case 3 - показ всех BLE-устройств и debug-лог, case 4 - авто-реконнект ("Enable" :192, "Resume spam" :206, "Max attempts" :227, "Delay" :248), default - "Device type" (:278).

## 10. Краткая сводка

- Пакет FishBot.en.Milky (label "FishBot"), 1.0.0; BLE UART: сервис 6e400001-..., write 6e400002-..., notify 6e400003-..., CCCD 00002902-....
- Скан: фильтр по сервисному UUID (когда show_all_scan_devices=false), scan mode 2; MTU по подключению 247 (0xF7) с таймаутом 3000 мс; запись чанками по 20 байт, тип 1/2 по PROPERTY_WRITE, всё через последовательную очередь.
- Кадр: A3 A4 len rand key^rand cmd^rand payload^rand CRC8; rand=(rnd+50)&255, CRC8 по всем байтам кроме последнего, len+7 = длина.
- Ключ кадра по умолчанию "w4S3Hqn5" (собирается в D2/C0141b.java:80-85 из массивов z2/l.java:10-13), в демо - 66.
- Хэндшейк: cmd 0x01 + ASCII-ключ, успех при payload[0]==1, ключ сессии = payload[1]; таймаут 3000 мс; иначе "Handshake failed/timed out", "BLE key is empty".
- Спам: cmd 0x05, payload 01 31 31 32 39 + 4 байта UNIX-секунд big-endian + 00; интервал по умолчанию 500 мс (2..3000), цикл C2/x -> N() -> y(), сервис foreground id 42, канал lostbot_spam, action Stop "ru.lostbotapp.action.STOP_SPAM".
- Гарды: без AUTHENTICATED любая отправка -> "Authenticate first"; entitlement-проверки в этой сборке всегда true (y2.s.g() = true), поэтому "Subscription required" недостижимо.
- Прошивка: START cmd 0xFB payload [kind, totalPack hi/lo, crc16 hi/lo, deviceType] + ключ; пакеты 16 байт с префиксом idx и CRC16 (init 0xFFFF, полином 0xA001 отражённый) = 20 байт; запрос пакета cmd 0xFC [idxHi, idxLo]; статусы 0/1/2/3/4 = ошибка ключа/ready/complete/timeout/общий CRC.
- Демо-режим (use_demo): ключ 66, deviceType 7, "DEMOKEY"; ответы F.M (5/21 -> [1], 49 -> версия/лок, 96 -> батарея/скорость/пробег, 133 -> RFID 12 34 56 78, 250 -> "FW 1.2.3 demo"), передача 300 мс старт + 250 мс на пакет.
- Лицензии: y2.s - заглушка (f/g/j -> true, e - no-op, l -> false), токены/entitlement читаются из prefs lb_access_token/lb_refresh_token/lb_entitlement_json; API https://lostbotapp.ru (/me/entitlement, /me/redeem, /auth/refresh, /me/telemetry/events) с Bearer-токеном; телеметрия вычитывается из очереди (до 200 за раз) и отбрасывается, так как s.l заглушен.
- UI: 4 вкладки Control / Telemetry / IoT / Console; настройки "Settings" с Subscription/Scan/Auto-reconnect/Key for Firmware и футером "FishBot · v1.0.0"; лист "License"; диалог "Connect".
- Тексты подписки: "EMOJI FishBot · LifeTime OK" (Base64 в y2/s.java:105).
