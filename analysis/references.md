# Референсы DanyScoot: артефакты, протокол, ключи, официальное приложение Urent

Сводный индекс. Детальный протокол: analysis/protocol_full.md. Официальное приложение: analysis/report_urent.md. Отчёты по отдельным APK: report_lostbot.md, report_ninehack.md, report_fishbot.md, report_whoosh.md, report_yandex_go.md.

## 1. Артефакты

| Файл | Пакет | Версия | Размер, байт | SHA-256 | Подпись | Роль |
| --- | --- | --- | --- | --- | --- | --- |
| references/FishBot.apk | FishBot.en.Milky | 1.0.0 (1) | 3 895 634 | 7E05FEEABDB3E2FB119FB60BFE437E8433EEE5BC33D473BFC4FB23BB09B1FEFA | v1 отсутствует, apksigner не проверяет | Клон LostBot с инжектором com.px/com.cloudinject, payload отсутствует |
| references/LostBot.apk | ru.lostbotapp.Free | 1.2.1 (4) | 2 536 378 | D1D8713820F3EBC480236E113020C0C547B94453D416EDE894D27A4205245882 | CN=np, SHA-256 492682F877607EE99DF2DDD2BD5953FD727BDF6E19D397DE9DBBAFD582BCAD75 | Клиент LostBot без инжектора |
| references/NineHack.apk | com.timeylies.ninehack.ru | 1.2 (1) | 11 497 879 | E21272AC4C1A74E1BFE5AF824EE2257BB15D0BB5A8C014F75014C6DE1931D497 | CN=np, тот же сертификат, что у LostBot | Минимальный BLE-клиент, 4 команды, без телеметрии |
| urent-1902.xapk | ru.urentbike.app | 1.90.2 (1902) | 95 646 408 | FA689FF0813A332F374D5A1FECDD4AF939F9780B1087FFDB3F75A7FEB50491E8 | O=Urentbike, SHA-256 90E028E6D376C9E79F8D0332BFFA8667F8D41F6E5B7A114A4602EC68A0AAE2DD | Официальное приложение, XAPK |
| urent.xapk | ru.urentbike.app | 2.7.0 (2070) | 117 800 904 | 22CA0CF66B22EA13E67953ED9158B1DA5508B668A91A17BA373C69513063ADBF | O=Urentbike, тот же сертификат | Официальное приложение, XAPK |
| urent-1902 base APK | ru.urentbike.app | 1.90.2 | 68 207 063 | B42D28566AD4BCC600BBD7CFDF2D5197B2B0DA24B1291D217E80AFC7C0CF6B68 | O=Urentbike | База XAPK |
| references/apks/urent-2.7.0-base.apk | ru.urentbike.app | 2.7.0 | 82 428 322 | 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 | O=Urentbike | База XAPK, ADB SM-A175F |
| references/apks/whoosh-4.0.0-base.apk | com.punicapp.whoosh | 4.0.0 (418060) | 54 707 264 | 912079486F06E84581C92055C3E87DAF8C3CB11F55B8F81F41BA907ADDB53C29 | CN=Whoosh.Bike, SHA-256 12607A1082202FEAFAA03AC22A3814F9EC770D731D87C667CE9FCA7B164F6BAE | Официальное приложение, BLE-стек |
| references/apks/yandex-go-5.98.2-base.apk | ru.yandex.taxi | 5.98.2 (50128633) | 192 834 008 | 27A75FDF62C9AB601BDDD89C6559E75231F868AC3F08FA463AE7A85D8B5C1862 | CN=OOO Yandex, SHA-256 ACA405DED8B25CB2E8C6DA69425D2B4307D087C1276FC06AD5942731CCC51DBA | Официальное приложение, Ninebot BLE-код |

Источники официальных APK: APKPure для Urent 1.90.2; ADB SM-A175F для Urent 2.7.0, Whoosh 4.0.0 и Yandex Go 5.98.2. Дампы: analysis/LostBot, analysis/FishBot, analysis/NineHack; временные каталоги urent1902_dec, urent_dec, whoosh_dec, whoosh_jadx и yandex_dec.

## 2. Что подтверждено

| Факт | Источник | Статус |
| --- | --- | --- |
| Nordic UART 6e400001-b5a3-f393-e0a9-e50e24dcca9e | LostBot z2/i.java, NineHack controlActivity.smali | Подтверждено |
| Write 6e400002, notify 6e400003, CCCD 00002902 | LostBot z2/i.java, NineHack controlActivity.smali | Подтверждено |
| Кадр A3 A4 len randWire key cmd data crc8 | LostBot A2/f.java, NineHack CommandCreator.smali | Подтверждено |
| randWire = originalRand + 50, XOR исходным rand | оба | Подтверждено |
| CRC-8/MAXIM, check 0xA1 | таблицы A2/f.java и Crc8.smali | Подтверждено вычислением |
| Рукопожатие cmd 0x01, ключ ASCII, ответ payload[0]=1, sessionKey=payload[1] | LostBot C2/F.java, NineHack CommandCreator.smali | Подтверждено |
| Ключ LostBot w4S3Hqn5 | z2/l.java, D2/C0141b.java | Подтверждено |
| Ключ NineHack 4BKNwi77 | strings.xml, controlActivity.smali | Подтверждено |
| Спам cmd 0x05, payload 01 31 31 32 39 + 4 байта времени + 00 | LostBot C2/F.java | Подтверждено |
| Прошивка cmd 0xFB/0xFC, CRC16 MODBUS 0x4B37 | LostBot C2/F.java | Подтверждено вычислением |
| Официальное Urent не содержит BLE-стека | Urent 1.90.2 и 2.7.0 smali | Подтверждено поиском |
| Urent отправляет useBluetooth=false | TransportRepositoryImpl | Подтверждено |
| Urent open/close lock через сервер | OrderingRepositoryImpl | Подтверждено |

## 3. Протокол кратко

Транспорт: BLE GATT, сервис Nordic UART. Запись в 6e400002, уведомления из 6e400003, CCCD 00002902-0000-1000-8000-00805f9b34fb. LostBot просит MTU 247, режет запись на max(20, MTU-3), тип WRITE_TYPE_DEFAULT при PROPERTY_WRITE, иначе WRITE_TYPE_NO_RESPONSE, все операции через очередь. NineHack MTU не просит, пишет одним пакетом, тип WRITE_TYPE_DEFAULT, CRC входящих кадров не проверяет.

Кадр:

| Смещение | Поле | Формула |
| --- | --- | --- |
| 0 | header | A3 |
| 1 | header | A4 |
| 2 | len | длина payload, 0..255 |
| 3 | randWire | (rand + 50) & 255 |
| 4 | key | key ^ rand |
| 5 | cmd | cmd ^ rand |
| 6..6+len-1 | data | payload[i] ^ rand |
| 6+len | crc8 | CRC-8/MAXIM по всем предыдущим байтам |

rand у LostBot 0..254, у NineHack 1..254. CRC-8/MAXIM: poly 0x31 отраженный, init 0x00, refin/refout true, xorout 0x00, check 123456789 = A1.

Рукопожатие: cmd 0x01, payload = байты ASCII-ключа, key=0. Успех: payload[0] == 1, sessionKey = payload[1]. Session key может быть 0, это валидное значение. Таймаут LostBot 3000 мс. NineHack таймаута не имеет.

Команды: 0x05 unlock, 0x15 lock, 0x61 set, 0x81 eject, 0xA2 find car, 0x31 device info, 0x60 scooter info, 0x63 realtime, 0x85 RFID, 0x82 color, 0xC0 battery cover, 0x92 IoT restart, 0xFA/0xFB/0xFC firmware. Полный каталог из 54 опкодов: analysis/protocol_full.md.

Ключи:

| Ключ | Где | Смысл |
| --- | --- | --- |
| 4BKNwi77 | NineHack strings.xml control_defaultBleKey | Ключ по умолчанию для рукопожатия |
| w4S3Hqn5 | LostBot z2/l.java, D2/C0141b.java case 27 | Ключ по умолчанию для рукопожатия |
| DEMOKEY, 66 | LostBot demo | Демо-ключ, в эфир не идет |
| sessionKey 0..255 | ответ на cmd 0x01 | Сессионный байт, используется в key ^ rand |
| 4BKNwi77, w4S3Hqn5 | публичные примеры | Не гарантируют работу на конкретной прошивке |

Ключ не является AES. Описание COMMUNICATION_KEY в A2/c.java содержит слова session AES key exchange, но в кадре используется только однобайтовый XOR с sessionKey. Никакого AES, DH или подписи в референсах нет.

Ключ LostBot не генерируется на устройство. Массивы z2/l.java f10879a = 44 CC 70 F4 76 B1 33 F1 и f10880b = 5B E7 1D 9A 42 дают ровно 8 байт w4S3Hqn5 формулой (b[i%5] ^ a[i]) ^ ((i*31)&255). Это обфускация константы, а не вывод из ID самоката.

Магазин ble_saved_keys/ble_selected_key в LostBot 1.2.1 создается, но рукопожатие берет ключ из DI-провайдера C0141b(27), то есть константу w4S3Hqn5. Пользовательский выбор ключа в этой сборке не влияет на auth.

## 4. Официальное приложение Urent

Пакет ru.urentbike.app, версии 1.90.2 и 2.7.0, minSdk 24, targetSdk 36, подпись O=Urentbike. В манифесте есть android.hardware.bluetooth_le required=true и android.permission.NFC, но нет BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH, BLUETOOTH_ADMIN.

В dex обеих версий нет классов android.bluetooth.BluetoothAdapter, BluetoothGatt, BluetoothLeScanner, нет UUID 6e400001/6e400002/6e400003, нет ключей 4BKNwi77 и w4S3Hqn5. Поиск по smali_classes5 для 1.90.2 и smali_classes6/7 для 2.7.0 это подтверждает. Есть только:

- enum AvailableViaBluetooth в VehicleResponse.State и State;
- query-параметр useBluetooth, который всегда отправляется как false;
- серверные lock-типы NA, Code, Electro, Carbine, Electronic, TwoInOne;
- серверные методы openlock и closelock.

Пути в приложении: /api/v1/scooters/openlock/{id} и /api/v1/scooters/closelock/{id}, база service.urentbike.ru, имя gateway из Flagr, авторизация Bearer через /api/v1/connect/token. OpenAPI дополнительно описывает /v3/transports/s.{id} с query useBluetooth и /v6/transports. В приложении параметр useBluetooth=false, поэтому BLE-ветка не используется.

Следствие: из официального APK ключ BLE не извлекается, потому что BLE-кода и ключа там нет. Urent управляет замком через сервер, а не через прямой BLE. Для DanyScoot это означает, что w4S3Hqn5 и 4BKNwi77 остаются единственными известными кандидатами, а не гарантией для парка Urent.

## 5. Whoosh и Yandex Go

Whoosh 4.0.0 содержит пакеты bike.whoosh.shared.ble.library_client, library_client_offline, library_server и library_scanner. Клиент ищет сервис 00001c18 и характеристики 0000572a, 00003d2a, 00009f2a. Серверная спецификация использует стандартные 0000181c, 00002a57, 00002a3d, 00002a9f. Клиентские значения являются байтово переставленными версиями стандартных.

Команда Whoosh является UTF-8 строкой. Ключ читается из 00003d2a с интервалом 500 мс. Шифрование выполняет backend через ClientBleCommandEncryptor и BleCommandRequestBody. В APK нет статического ключа и алгоритма шифрования. Успех определяется строкой CMD_EXE_SUCCESS, ошибка строкой CMD_EXE_ERROR. Повторов 3, задержка 500 мс. Подробнее: report_whoosh.md.

Yandex Go 5.98.2 содержит пакет com.yandex.go.scooters.ble с Ninebot-протоколом. Команды: AUTH, LOCK, UNLOCK, SOUND, CABLE_UNLOCK, UNLOCK_BATTERY_COVER. Payload собирается классами nap0 и nep0, преобразуется через za.f, ответ проходит через za.g. UUID сервиса, write, notify и CCCD возвращает провайдер ghq0 и i2r0. В этой сборке fhq0.b() и h2r0.b() возвращают null, других реализаций и установки провайдера нет. Статические UUID и ключи отсутствуют. Подробнее: report_yandex_go.md.

## 6. Расхождения и неизвестное

- XDDbot wiki иначе трактует поля ответа рукопожатия. LostBot и NineHack трактуют payload[0] как статус 1, payload[1] как sessionKey. Код APK важнее wiki; текущая реализация DanyScoot следует APK.
- Неизвестно, какой ключ прошит в конкретном самокате пользователя. Проверка возможна только на hardware.
- Неизвестно, принимает ли конкретная прошивка sessionKey 0. NineHack явно показывает ответ с ключом 0, LostBot код допускает 0. DanyScoot должен допускать 0.
- Неизвестно, какой MTU согласует конкретный самокат. LostBot просит 247, но фактический размер зависит от прошивки.
- Неизвестно, требует ли прошивка CRC входящих кадров. LostBot проверяет, NineHack нет.
- Официальное Urent 1.90.2 и 2.7.0 не использует BLE, но более старые версии или отдельные сборки могут отличаться. Проверены только эти две.
- В Urent есть NFC-разрешение и Mir Pay, но это не BLE-ключ.

## 6. Как добыть ключ

1. Проверить известные константы: 4BKNwi77, w4S3Hqn5. Это бесплатно и быстро.
2. Если самокат доступен и официальное приложение подключается по BLE, снять обмен BLE-сниффером. Рукопожатие передает ключ ASCII в кадре, замаскированный только XOR с rand, а rand выводится из байта 3. Ключ читается из эфира без криптоанализа. Ограничение: официальное Urent 1.90.2/2.7.0 BLE не использует.
3. Если есть сервисное приложение или диагностический софт, снять рукопожатие там.
4. Дамп прошивки или UART лог может содержать ключ в открытом виде.
5. Брутфорс 8 ASCII-символов практически бессмыслен: 95^8 = 6.6e15, 62^8 = 2.18e14. Перебор имеет смысл только если ключ короткий или известен алфавит.
6. Для парка Urent практичнее серверный openlock/closelock, а не BLE-ключ. Это другой канал и требует авторизации в аккаунте.

## 7. Файлы анализа

| Файл | Содержимое |
| --- | --- |
| analysis/protocol_full.md | Полный протокол, 54 опкода, TX/RX, телеметрия, прошивка, тест-векторы |
| analysis/opcode_builders.md | Подтвержденные сборщики, каталог, декодеры, расхождения |
| analysis/rx_samples.md | Байтовые примеры ответов, телеметрия, чанкинг, битые кадры |
| analysis/hardware_test.md | Проверка на самокате, HCI snoop, nRF Connect, UART, дамп |
| analysis/source_map.md | Карта файлов и строк для каждого утверждения |
| analysis/README.md | Индекс всех документов |
| analysis/report_lostbot.md | LostBot/FishBot: транспорт, auth, спам, прошивка, лицензии, UI |
| analysis/report_ninehack.md | NineHack: минимальный клиент, кадр, команды, ошибки |
| analysis/report_fishbot.md | Инжектор FishBot, payload, отличия от LostBot |
| analysis/report_urent.md | Официальное Urent 1.90.2 и 2.7.0: манифест, API, отсутствие BLE |
| references/README.md | Хэши, подписи, ссылки на скачивание |
| references/opcodes.csv | 54 опкода в машиночитаемом виде |
| references/frames.json | 48 TX, 20 RX и 4 битых кадра |
| references/generate_frames.py | Генератор frames.json |
| references/verify_frames.py | Независимая проверка кадров и CRC |
| references/xddbot.md | Публичный XDDbot и расхождения с APK |
| references/artifacts.md | Полные таблицы артефактов, подписей и происхождения |
| references/schema.md | Схемы opcodes.csv и frames.json |
| analysis/app_inventory.md | Пакеты, версии, SDK, компоненты и атрибуты приложений |
| analysis/permissions.md | Матрица разрешений и опасные наборы |
| analysis/network.md | Endpoint-ы LostBot, Urent, FishBot, отсутствие сети у NineHack |
| analysis/storage.md | SharedPreferences, ключи, файлы, токены, приватность |
| analysis/comparison.md | Сравнение функций, команд и архитектуры |
| analysis/opcode_reference.md | Полная таблица 54 опкодов |
| analysis/telemetry.md | Поля телеметрии и ограничения |
| analysis/firmware.md | OTA 0xFA, 0xFB, 0xFC и риски |
| analysis/errors.md | 0x10, статусы авторизации, команд и GATT |
| analysis/test_vectors.md | Схема и проверка frames.json |
| analysis/method.md | Инструменты, порядок анализа, уровни уверенности |
| analysis/limitations.md | Неизвестные и blockers |
| analysis/obfuscation.md | R8, StringFog и упаковщики |
| analysis/ui_screens.md | Экраны референсов и DanyScoot |
| docs/README.md | Индекс документации приложения |

## 8. Уточнения после проверки кода

- 0x10 ERROR: payload[0] - отклонённая команда, payload[1] - причина. Трактовка payload[0] как CRC в DanyScoot удалена.
- 0x64 SET_WHEEL_DIAMETER, 0xC3 ACCESSORY_CONTROL и 0x78 XM_FIND_CAR не имеют подтверждённого сборщика. Для 0x64 и 0xC3 есть только декодер, для 0x78 только имя каталога.
- 0x05 UNLOCK имеет две подтверждённые формы: 10 байт 01 у NineHack и 01 31 31 32 39 + время + 00 у LostBot. DanyScoot использует первую для кнопки, вторую для спама.
- 0xA2 FIND_CAR подтверждён с payload 01, 02, 03 и 06. DanyScoot использует 01.
- 0xDC AVAS payload завершается NUL: ASCII HBCS,NB,IMEI,V2,1 или 2,,,,,# и 00.
- 0x81 в каталоге имеет два имени, SCOOTER_BATTERY и OUT_LOCK. DanyScoot использует код как изъятие батареи с payload 05.
- 0x85 READ_RFID_CARD возвращает 4 байта big-endian. 0x86 DELETE_RFID принимает два идентификатора по 4 байта.
- 0xFB и 0xFC относятся к прошивке. DanyScoot не выполняет OTA.
