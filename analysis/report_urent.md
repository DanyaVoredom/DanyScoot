# Официальное приложение Urent (ru.urentbike.app): 1.90.2 и 2.7.0

Сводный индекс: analysis/references.md. Полный BLE-протокол: analysis/protocol_full.md.

## 1. Артефакты

| Артефакт | Версия | versionCode | Размер | SHA-256 | Подпись |
| --- | --- | --- | --- | --- | --- |
| urent-1902.xapk | 1.90.2 | 1902 | 95 646 408 | FA689FF0813A332F374D5A1FECDD4AF939F9780B1087FFDB3F75A7FEB50491E8 | O=Urentbike |
| urent-1902 base APK | 1.90.2 | 1902 | 68 207 063 | B42D28566AD4BCC600BBD7CFDF2D5197B2B0DA24B1291D217E80AFC7C0CF6B68 | O=Urentbike |
| urent.xapk | 2.7.0 | 2070 | 117 800 904 | 22CA0CF66B22EA13E67953ED9158B1DA5508B668A91A17BA373C69513063ADBF | O=Urentbike |
| urent 2.7.0 base APK | 2.7.0 | 2070 | 82 428 322 | 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 | O=Urentbike |

Сертификат обеих версий: O=Urentbike, SHA-256 90E028E6D376C9E79F8D0332BFFA8667F8D41F6E5B7A114A4602EC68A0AAE2DD. Обе версии: minSdk 24, targetSdk 36, package ru.urentbike.app.

## 2. Манифест

Обе версии:

- android.hardware.bluetooth_le required=true;
- android.hardware.nfc required=false;
- android.permission.NFC с атрибутом required=false (атрибут для permission недействителен, но факт есть);
- android:usesCleartextTraffic=false;
- android:allowBackup=false;
- android:networkSecurityConfig=@xml/network_security_config;
- android:name=ru.urentbike.app.App.

Разрешения, связанные с BLE:

| Разрешение | 1.90.2 | 2.7.0 |
| --- | --- | --- |
| android.permission.BLUETOOTH | нет | нет |
| android.permission.BLUETOOTH_ADMIN | нет | нет |
| android.permission.BLUETOOTH_SCAN | нет | нет |
| android.permission.BLUETOOTH_CONNECT | нет | нет |
| android.permission.ACCESS_FINE_LOCATION | есть | есть |
| android.permission.ACCESS_COARSE_LOCATION | есть | есть |

ACCESS_FINE_LOCATION и ACCESS_COARSE_LOCATION используются для карты и поездок, а не для BLE-сканирования: BLE-разрешений и BLE-кода нет. Наличие feature required=true при отсутствии BLE-разрешений - несогласованность манифеста: приложение может отсеиваться в магазине на устройствах без BLE, хотя BLE не использует.

Другие заметные разрешения: INTERNET, ACCESS_NETWORK_STATE, CAMERA, READ_PHONE_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, VIBRATE, READ_CONTACTS, NFC, ru.nspk.mirpay.DOCUMENT_VIEW, com.google.android.gms.permission.AD_ID.

## 3. Что приложение делает с самокатом

Urent не подключается к самокату напрямую. Аренда, замок и телеметрия идут через сервер.

Наблюдаемые строки путей в dex:

| Путь | Класс | Назначение |
| --- | --- | --- |
| /api/v1/connect/token | SmsAuthRepositoryImpl, MtsAuthRepositoryImpl | Получение токена |
| /api/v1/scooters/openlock/ | OrderingRepositoryImpl$openLock$2 | Серверная разблокировка |
| /api/v1/scooters/closelock/ | OrderingRepositoryImpl$closeLock$2 | Серверная блокировка |
| /api/v3/transports/ | TransportRepositoryImpl, getVehicle | Карточка самоката |
| /api/v6/transports | getMapFreeTransport | Список самокатов на карте |
| /api/v6/transports/polygon | getTransportsByPolygon | Список в полигоне |

Ресурсы хоста:

| Ресурс | Значение |
| --- | --- |
| app_host | service.urentbike.ru |
| app_host2 | ure.su |
| app_host_prefix | /a |
| urent_url | urentbike.ru |

Шлюз: core_flagr/api/entity/GatewayConfigInfo, feature gateway_configuration, default gatewayclient. Публичного официального описания API не найдено. Встречается неофициальный OpenAPI-документ с сервером https://app.urentbike.ru/gatewayclient/api, но он собран по наблюдениям и не является истиной для приложения. В приложении хост service.urentbike.ru, поэтому склеивать хост и пути из OpenAPI без проверки нельзя.

Авторизация: Authorization: Bearer JWT. Токен выдается через /api/v1/connect/token, есть SMS- и MTS-ветки. В неофициальном OpenAPI также описаны заголовки ur-* и подпись ur-request-data; алгоритм подписи в документе намеренно не раскрыт.

Серверные lock-типы:

| LockType | Смысл |
| --- | --- |
| NA | нет данных |
| Code | кодовый замок |
| Electro | электрозамок |
| Carbine | карабин |
| Electronic | электронный замок |
| TwoInOne | комбинированный |

В ActivityResponse есть поля lockCode и lockType. lockCode - серверный код замка, а не BLE-ключ. В ответах транспорта нет MAC, BLE-ключа, session key или сервисных UUID.

## 4. useBluetooth

В обеих версиях параметр useBluetooth встречается ровно один раз, в TransportRepositoryImpl$getMapFreeTransport$2, и всегда отправляется со значением false.

1.90.2, smali_classes5, строки 268-277:

    const-string/jumbo p1, "useBluetooth"
    const-string p2, "false"

2.7.0, smali_classes6, строки 352-360:

    const-string p1, "useBluetooth"
    const-string p2, "false"

Путь запроса: /api/v6/transports. Параметр не читается из настроек, не зависит от состояния самоката и не имеет ветки true. Это не флаг возможности, а константа.

Неофициальный OpenAPI описывает useBluetooth также для lowercase-пути /v3/transports/s.{scooter_id} как необязательный boolean. Это не меняет факт: в APK нет кода, который ставит true, и нет BLE-клиента.

## 5. AvailableViaBluetooth

Enum есть в двух местах:

- data_transport/api/entity/VehicleResponse$State: Available, AvailableViaBluetooth, Unavailable, AvailableWithoutGps;
- data_zones/api/entity/State: Available, AvailableViaBluetooth, Unavailable.

Это только модель ответа сервера и сериализация. Поиск sget-object по AvailableViaBluetooth находит только внутренние $values и deserializer. Нет ветки, которая по этому состоянию запускает сканирование, подключение, чтение характеристики или отправку кадра. Состояние означает, что сервер считает самокат доступным по BLE, но приложение эту возможность не реализует.

## 6. Отсутствие BLE-стека

Проверено по dex обеих версий, включая smali_classes5 для 1.90.2 и smali_classes6/7 для 2.7.0.

| Что искали | Результат |
| --- | --- |
| Landroid/bluetooth/BluetoothAdapter | не найдено |
| Landroid/bluetooth/BluetoothGatt | не найдено |
| Landroid/bluetooth/BluetoothLeScanner | не найдено |
| android.bluetooth.* | не найдено |
| 6e400001-b5a3-f393-e0a9-e50e24dcca9e | не найдено |
| 6e400002-b5a3-f393-e0a9-e50e24dcca9e | не найдено |
| 6e400003-b5a3-f393-e0a9-e50e24dcca9e | не найдено |
| 4BKNwi77 | не найдено |
| w4S3Hqn5 | не найдено |
| bleKey, sessionKey, CommunicateKey | не найдено в контексте BLE |
| createBond, connectGatt, startScan | не найдено |

Единственные BLE-следы: feature в манифесте, enum AvailableViaBluetooth и query useBluetooth=false. Ни один из них не является BLE-клиентом.

## 7. Различия 1.90.2 и 2.7.0

| Свойство | 1.90.2 | 2.7.0 |
| --- | --- | --- |
| versionCode | 1902 | 2070 |
| Размер base APK | 68 207 063 | 82 428 322 |
| Раскладка app-классов | smali_classes5 | smali_classes6 и smali_classes7 |
| app_host | service.urentbike.ru | service.urentbike.ru |
| useBluetooth | false | false |
| openlock/closelock | есть | есть |
| AvailableViaBluetooth | есть | есть |
| BLE-классы | нет | нет |
| BLE-разрешения | нет | нет |
| targetSdk | 36 | 36 |

По BLE и ключам версии эквивалентны. Отличия касаются размера, структуры dex и набора функций, а не транспорта замка.

## 8. Что это значит для DanyScoot

1. Официальное Urent 1.90.2/2.7.0 не является источником BLE-ключа. Ключа там нет, потому что нет BLE-кода.
2. Наличие AvailableViaBluetooth не доказывает, что приложение умеет BLE. Это серверное состояние.
3. useBluetooth=false означает, что даже если сервер отдает BLE-состояние, клиент выбирает серверную разблокировку.
4. Для Urent-самоката прямой BLE-канал может быть закрыт или не использоваться официальным приложением. Проверять известные ключи 4BKNwi77 и w4S3Hqn5 все равно стоит: прошивка может принимать их независимо от приложения.
5. Если известные ключи не подходят, нужен сниффинг BLE-обмена другого приложения, дамп прошивки или UART-лог. Официальное Urent для этого бесполезно.
6. Серверный openlock/closelock - отдельный канал. Он требует аккаунт, токен и подпись запросов; это не BLE-ключ и не обход BLE.
7. DanyScoot должен принимать sessionKey 0 как валидный и не путать его с отсутствием авторизации. Официальное Urent к этому отношения не имеет, но референсный протокол это допускает.

## 9. Аномалии и ограничения

- android.hardware.bluetooth_le required=true при полном отсутствии BLE-кода и BLE-разрешений.
- android.permission.NFC с required=false: атрибут required относится к uses-feature, не к uses-permission.
- OpenAPI и APK расходятся по хосту: app_host service.urentbike.ru против сервера app.urentbike.ru в неофициальном документе. Приоритет у APK.
- OpenAPI описывает useBluetooth для lowercase s.{id}, но в APK параметр есть только в getMapFreeTransport и всегда false.
- LockType Code/Carbine означает механический или кодовый замок, а не BLE. lockCode нельзя использовать как BLE-ключ.
- Проверены только 1.90.2 и 2.7.0. Другие версии, региональные сборки и внутренние тестовые сборки могут отличаться.
- В приложении есть NFC и Mir Pay, но это не BLE и не ключ самоката.

## 10. Итог

Официальное Urent управляет замком через сервер. BLE-стека, BLE-разрешений, UUID Nordic UART и ключей 4BKNwi77/w4S3Hqn5 в проверенных версиях нет. useBluetooth всегда false. AvailableViaBluetooth - только enum. Из этого APK ключ BLE извлечь нельзя, и это не ошибка анализа, а свойство приложения.

Практические источники ключа остаются прежними: известные константы, BLE-сниффинг рукопожатия другого клиента, дамп прошивки, UART-лог. Для парка Urent альтернативный канал - серверный openlock/closelock, но он не относится к BLE-протоколу DanyScoot.
