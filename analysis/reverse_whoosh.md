# Whoosh 4.0.0: BLE reverse

Отчёт собран по base APK и двум распаковкам. Все утверждения помечены уровнем достоверности.

Обозначения путей:

| Метка | Корень |
| --- | --- |
| S2 | whoosh_dec/smali_classes2/bike/whoosh |
| S4 | whoosh_dec/smali_classes4/bike/whoosh |
| D1 | whoosh_dec/smali/bike/whoosh |
| OK4 | whoosh_dec/smali_classes4/okio |
| J | whoosh_jadx/sources |

Полные корни распаковок: C:\Users\Dmode\AppData\Local\Temp\opencode\whoosh_dec и C:\Users\Dmode\AppData\Local\Temp\opencode\whoosh_jadx.

## 1. Артефакты

| Параметр | Значение | Источник |
| --- | --- | --- |
| APK | references/apks/whoosh-4.0.0-base.apk | файл |
| Размер | 54707264 байт | файл |
| SHA-256 | 912079486F06E84581C92055C3E87DAF8C3CB11F55B8F81F41BA907ADDB53C29 | файл |
| Пакет | com.punicapp.whoosh | whoosh_dec/AndroidManifest.xml:2 |
| Версия | 4.0.0, versionCode 418060 | whoosh_dec/apktool.yml:9-11 |
| minSdk / targetSdk | 32 / 36 | whoosh_dec/apktool.yml:6-8 |
| Разрешения BLE | BLUETOOTH, BLUETOOTH_ADMIN (maxSdk 30), BLUETOOTH_SCAN, BLUETOOTH_CONNECT | whoosh_dec/AndroidManifest.xml:10-13 |
| BLE feature | android.hardware.bluetooth_le required true | whoosh_dec/AndroidManifest.xml:21 |
| Dex | 4 файла, classes.dex содержит client и offline_start | структура whoosh_dec |

Метод: smali используется для точных констант, порядка байт, сообщений и номеров строк; jadx используется для структуры и мест, где smali читается хуже. Номера строк jadx указаны только для Java-файлов.

## 2. Уровни достоверности

| Метка | Значение |
| --- | --- |
| confirmed | прямо следует из байткода или строки в APK, указан файл и строка |
| inferred | следует из кода с допущением о поведении платформы или устройства, допущение указано |
| missing | в APK данных нет, указано что нужно |

## 3. Архитектура BLE в приложении

| Роль | Класс | Источник |
| --- | --- | --- |
| Клиент самоката (online) | bike.whoosh.shared.ble.library_client.ClientBleSession | S2/shared/ble/library_client/ClientBleSession.smali |
| Клиент самоката (offline) | bike.whoosh.shared.ble.library_client_offline.ClientBleSessionOffline | S2/shared/ble/library_client_offline/ClientBleSessionOffline.smali |
| Сервер (телефон как периферия) | bike.whoosh.shared.ble.library_server.ServerBleSession | S2/shared/ble/library_server |
| Service master | bike.whoosh.shared.ble.library_service_master.ServiceMasterBleSession | S2/shared/ble/library_service_master/ServiceMasterBleSession.smali |
| Сканер | bike.whoosh.shared.ble.scanner.BleScanner, BleScannerOfflineStart | S2/shared/ble/scanner |

Выбор типа соединения. Имя устройства сравнивается с токенами из remote config BLE2Pattern (по умолчанию m4r0c0,m4r0c1,m3r0c2,m3r1c1, разделитель запятая). Если имя содержит любой токен как подстроку с учётом регистра, выбирается APP_CLIENT_TO_IOT_SERVER, иначе APP_SERVER_TO_IOT_CLIENT (S2/ble/domain/ClientBleInteractor.smali:158, 172, 183, 215, 221, 227).

Диспетчеризация по типу: S2/shared/ble/connection/BleConnection.smali:328; ветки 336-340; APP_CLIENT_TO_SERVICE_MASTER создаёт ServiceMasterBleSessionImpl (344-380), APP_SERVER_TO_IOT_CLIENT использует ServerBleSession (482), APP_CLIENT_TO_IOT_SERVER использует ClientBleSession (525).

## 4. Online протокол

### 4.1 UUID и роли характеристик

Клиентский резолвер сравнивает не полный UUID, а старшие 32 бита: UUID переводится в long, затем сдвиг на 32 (S2/shared/ble/library_client/BleServiceResolver.smali:172, 262-271). Сравниваемые значения: сервис 0x1c18 (196), digital output 0x572a (278), string 0x3d2a (292), user control point 0x9f2a (306).

| Роль | Сравниваемые старшие 32 бита | Поле IotV2Characteristics | Подтверждение |
| --- | --- | --- | --- |
| Сервис | 0x00001c18 | - | BleServiceResolver.smali:196 |
| digital_output, чтение и notify | 0x0000572a | a | BleServiceResolver.smali:278-320; IotV2Characteristics.smali:40 |
| string, чтение ключа | 0x00003d2a | b | BleServiceResolver.smali:292-320; IotV2Characteristics.smali:42 |
| user_control_point, запись и notify | 0x00009f2a | c | BleServiceResolver.smali:306-320; IotV2Characteristics.smali:44 |
| CCCD | 00002902-0000-1000-8000-00805f9b34fb | - | S2/shared/ble/library_client/BleNotificationManager.smali:51-53 |

Полные 128 бит для клиента в коде не задаются. Значения 0000xxxx-0000-1000-8000-00805f9b34fb являются inferred по Bluetooth SIG base UUID, потому что код сравнивает только верхние 32 бита.

Привязка ролей при подключении: digital output передаётся BleDigitalOutputReader (ClientBleSession.smali:677-687), string передаётся BleCommandKeyReader (691-701), user control point становится характеристикой записи BleControlPointChannel (726-731).

Серверный профиль IotV1 использует стандартные SIG UUID: 0000181c, 00002a57, 00002a3d, 00002a9f (S2/shared/ble/library_server/IotV1Specifications.smali:50, 60, 70, 80). Расхождение с клиентскими 00001c18 и 0000572a подтверждено кодом, это разные профили.

### 4.2 Подключение и включение notify

Notify включается в процессе подключения клиента, а не при первой команде: для каждой характеристики проверяется свойство PROPERTY_NOTIFY, вызывается setCharacteristicNotification и запись CCCD через BleNotificationManager (ClientBleSession.smali:614-656). Для CCCD используется ENABLE_NOTIFICATION_VALUE (BleNotificationManager.smali:133).

Таймаут подключения 10 секунд (S2/shared/ble/library_client/BleGattManager.smali:70-78), таймер в BleGattManager$connect$2.smali:194, при срабатывании TimeoutGattConnectionException с текстом Connection timed out after ... (226-234).

Таймаут разрешения сервиса и характеристик 10 секунд (BleServiceResolver.smali:55-63), исключение TimeoutServiceResolverException с текстом Service resolution timed out after ... (462).

Ошибки отсутствия: Service UUID not found (354), digital_output not found (345), string not found (336), user_control_point not found (327).

### 4.3 Ключ команды

Ключ читается из характеристики 0x3d2a. Интервал опроса 500 мс (S2/shared/ble/library_client/BleCommandKeyReader.smali:64), цикл опроса и повторное чтение при отсутствии значения: 141-164.

Пустой массив игнорируется (BleCommandKeyReader.smali:118-122). Массив из одних нулей игнорируется через BleExtensionsKt.a (125-131; S2/shared/ble/specification/BleExtensionsKt.smali:25-53). Результат копируется и отдаётся в CompletableDeferred (156-162).

В hex-строку ключа берутся первые 16 байт: ByteString.Companion.d(16, bytes), затем hex (ClientBleSession.smali:878-886). Hex в okio формируется в нижнем регистре (используется ByteString.e()). Если массив короче 16 байт, копирование первых 16 байт выйдет за границы и бросит исключение (OK4/ByteString$Companion.smali:441-473, копирование ArraysKt.m; inferred по семантике copyOfRange).

Ключ уходит на backend в поле commandKey вместе с именем команды. Локального шифрования в APK нет.

### 4.4 Получение команды с backend

Запрос: POST client/devices/{code}/bleCommand (J/bike/whoosh/client/api/ClientApiService.java:37-38). Тело: BleCommandRequestBody с полями commandKey и name (J/com/punicapp/whoosh/model/request/BleCommandRequestBody.java:7-11, конструктор 71-74). Ответ: поле bleCommand, alternate bleCommands (J/bike/whoosh/client/api/ble/BleResponseApiModel.java:8).

Вызов: new BleCommandRequestBody(key, command) и bleCommand(deviceCode, body) (J/bike/whoosh/client/ble_iot/ClientBleCommandEncryptor$encryptCommand$2.java:38-41), возврат getBleCommand() (52). Метод encryptCommand работает на Dispatchers.IO (J/bike/whoosh/client/ble_iot/ClientBleCommandEncryptor.java:14-16).

Имя класса ClientBleCommandEncryptor не означает шифрование на телефоне: ответ backend возвращается как строка и передаётся дальше без преобразований, кроме hex-декодирования при записи. Алгоритм и ключ шифрования находятся на backend (missing).

### 4.5 Запись команды

executeEncryptedCommand принимает hex-строку, проверяет CONNECTED, декодирует hex в байты через okio и передаёт в BleControlPointChannel (ClientBleSession.smali:246-276). Нечётная длина или недопустимый символ дают IllegalArgumentException от okio (OK4/ByteString$Companion.smali:340-417, текст Unexpected hex string на 406).

Запись идёт в характеристику user_control_point 0x9f2a, которая лежит в поле d канала (ClientBleSession.smali:729-731; BleControlPointChannel.smali:100-111).

Обёртка записи: на SDK 33 и выше вызывается writeCharacteristic(characteristic, bytes, characteristic.getWriteType()) (S2/shared/ble/library_client/BleGattWrapper.smali:134-150); на SDK ниже 33 setValue и writeCharacteristic(characteristic) (186-192). setWriteType в APK не вызывается ни в одном месте (проверено поиском по whoosh_dec, совпадений нет). Тип записи остаётся тем, что пришёл из GATT (обычно default или тот, что выставила система).

Ожидание подтверждения записи: CompletableDeferred кладётся в map по UUID, запись, затем ожидание с таймаутом 3 секунды (BleGattManager.smali:372, 409-436). По таймауту TimeoutGattWriteOperationException с текстом Connection timed out after ... (462-483); текст не отличается от ошибки подключения.

Если адаптер сообщает not connected, executeEncryptedCommand бросает NotConnectedDeviceException с текстом GattManager reports not connected (ClientBleSession.smali:321-328).

### 4.6 Ответ самоката

Notify приходит в user_control_point, проверяется UUID характеристики и непустой массив, массив из нулей игнорируется, затем байты декодируются как UTF-8 строка (S2/shared/ble/library_client/BleControlPointChannel.smali:120-146). Строка пишется в лог и в BleLogger.

Успех: строка начинается с CMD_EXE_SUCCESS, сравнение case-sensitive (168-190). Ошибка: строка начинается с CMD_EXE_ERROR, создаётся CommandExecuteException с текстом Command execution failed: плюс вся строка (192-217). Любая другая строка не завершает ожидание.

### 4.7 Повторы online

Перед циклом один раз создаётся CompletableDeferred и подставляется в BleDigitalOutputReader (S2/shared/ble/library_client/BleControlPointChannel$sendEncryptedCommandWithRetries$2.smali:215-223). Все попытки ждут один и тот же объект.

Цикл от 1 до 10 включительно (228-235: граница 0xb при старте v8=1, 147, 225). На каждой попытке:

| Шаг | Что происходит | Строки |
| --- | --- | --- |
| 1 | лог Attempt N/10 | 256-276 |
| 2 | запись команды | 278-297 |
| 3 | ожидание notify или poll с таймаутом 1500 мс | 318-320 |
| 4 | успех: возврат Unit | 328-335 |
| 5 | таймаут на попытке 10: лог Final attempt timed out, затем повторное ожидание того же deferred без таймаута | 337-360 |
| 6 | возврат без ошибки только если deferred завершится успешно позже; при ошибке CommandExecuteException; без ответа вызов может ждать бесконечно | 348-360 |
| 7 | иначе лог No response in 1500 ms on attempt N и следующая попытка без паузы | 380-394 |
| 8 | CommandExecuteException: проброс сразу | 396-409 |
| 9 | GattWriteOperationException: проброс сразу | 411-433 |

Метка (final - no rewrite) в логе на 10-й попытке (248) вводит в заблуждение: запись выполняется и на 10-й попытке (278-293). Строка 435-438 завершает метод после выхода из цикла, но на 10-й попытке выход идёт через возврат на 335 после завершения deferred.

Внутри каждой попытки параллельно запускаются ожидание notify и pollUntilSuccess, ожидается notify, затем poll (BleControlPointChannel$sendEncryptedCommandWithRetries$2$1.smali:161-207). pollUntilSuccess делает readCharacteristic для digital_output и до 3 итераций по 500 мс (BleControlPointChannel.smali:306-359, интервал f=500 мс на 64). Это отдельный механизм от повторов команды.

Итог: 10 записей, каждая с ожиданием до 1500 мс, без паузы между попытками; это даёт не менее 15 секунд до последнего таймаута. Жёсткого верхнего предела нет: после таймаута на 10-й попытке код ждёт тот же deferred без таймаута (348-360), поэтому при отсутствии ответа вызов может не завершиться.

### 4.8 Сканирование online

ScanSettings: scanMode 2 (SCAN_MODE_LOW_LATENCY), фильтры не передаются, startScan(null, settings, callback) (S4/shared/ble/library_scanner/BleLibraryScanner$scan$1.smali:129, 181-199).

Фильтр в BleScanner.filterScan: code должен быть непустым, имя устройства не null, имя содержит code с учётом регистра; иначе результат не эмитится (S2/shared/ble/scanner/BleScanner$filterScan$$inlined$filter$1$2.smali:126-149).

Ожидание устройства ограничено 10000 мс (S2/shared/ble/scanner/BleScanner.smali:142). Если устройство не найдено, TimeoutScanException с текстом Scan retrieval timed out (161-165).

## 5. Offline протокол

### 5.1 UUID

| Роль | Сравниваемые старшие 32 бита | Подтверждение |
| --- | --- | --- |
| Сервис | 0x0000e018 | S2/shared/ble/library_client_offline/BleServiceResolverOffline.smali:172-174 |
| plaintext_in, запись | 0x0000dd1a | BleServiceResolverOffline.smali:281 |
| plaintext_out, notify | 0x0000dd2a | BleServiceResolverOffline.smali:295 |

Резолвер работает так же, как online: сдвиг UUID на 32 и сравнение long (172-174). Полные 128 бит в коде не заданы; значения 0000e018-0000-1000-8000-00805f9b34fb, 0000dd1a-..., 0000dd2a-... являются inferred.

Привязка: in становится полем a IotV2CharacteristicsOffline, out становится полем b (BleServiceResolverOffline.smali:307-309). Канал записи получает in в c и out в d (ClientBleSessionOffline.smali:462-464). Запись идёт в c, notify слушается от d (BleControlPointChannelOffline.smali:204-215).

Ошибки: Service UUID not found. expectedServiceBits= (313-358), plaintext_in not found (322-329), plaintext_out not found (313-320).

Таймаут разрешения сервиса 10 секунд (BleServiceResolverOffline.smali:55-63, 470).

### 5.2 Сканирование offline

Сканирование ограничено 20 секундами через withTimeoutOrNull (S2/shared/ble/scanner/BleScannerOfflineStart.smali:62, 164-166). По истечении возвращается BleScanResultData с null device (182-199), вызывающий код в этом случае бросает TimeoutScanException с текстом Scan retrieval timed out (S2/client/offline_start/common/ble/BleInteractorOffline.smali:630-632).

Фильтр устройства (S2/shared/ble/scanner/BleScannerOfflineStart$scanForDeviceToConnect$found$1$2.smali:142-209):

| Условие | Поведение |
| --- | --- |
| feature toggle offlineStartMatchAnyWshDeviceFeatureToggle включён | принимается любое устройство (144-154) |
| имя null или пустое | отказ (159-167) |
| имя не начинается с WSH с учётом регистра | отказ (170-176) |
| code null или пустой | отказ (194-204) |
| имя не содержит code с учётом регистра | отказ (207-209) |

Toggle по умолчанию выключен: ResolutionStrategy LOCAL, значение false (S2/shared/ble/scanner/OfflineStartMatchAnyWshDeviceFeatureToggle.smali:48-56).

### 5.3 Подключение и MTU offline

Попыток подключения 2 (ClientBleSessionOffline.smali:602, 719-727), пауза между попытками 350 мс (596). Таймаут подключения 20 секунд (S2/shared/ble/library_client_offline/BleGattManagerOffline.smali:72-84; BleGattManagerOffline$connect$2.smali:194, 226-234).

После подключения запрашивается MTU 185 (BleGattManagerOffline$requestMtu$2.smali:162). Таймаут запроса MTU 20 секунд (BleGattManagerOffline.smali:72-84; requestMtu$2.smali:272-280, текст MTU request timed out after ...).

Для offline команды требуется согласованный MTU не меньше 114 (0x72), иначе GattConnectionException с текстом Offline command requires MTU >= 114, negotiated= плюс значение (ClientBleSessionOffline.smali:279-281, 511-521).

Notify для plaintext_out обязателен. Если CCCD не включён, DescriptorNotFoundException с текстом plaintext_out notifications were not enabled (466-508).

### 5.4 Токен offline

Токен берётся с backend: POST trips/offline/token, ответ OfflineStartTokenResponseApiModel с полем binaryToken (J/bike/whoosh/client/api/ClientApiService.java:186-187; J/bike/whoosh/client/api/trip/offline/OfflineStartTokenResponseApiModel.java:8).

Декодирование: trim строки, Base64.getDecoder().decode, hex байтов, toUpperCase(ROOT) (S2/client/offline_start/common/ble/BleOfflineStartTokenDecoder.smali:42-76). Локальной проверки подписи или срока токена в BLE-слое нет.

### 5.5 Идентификаторы команд

| Действие | hex | Подтверждение |
| --- | --- | --- |
| START | 01 | S2/client/offline_start/common/ble/BleOfflineStartCommandId.smali:85-89 |
| STOP | 02 | BleOfflineStartCommandId.smali:97-101 |

### 5.6 Payload offline

Payload содержит tokenHex и commandIdHex: поля a и b (S2/shared/ble/library_client_offline/BleOfflineStartCommandPayload.smali:27-29, конструктор 42-44). Создание: BleOfflineStartCommandPayload(tokenHex, commandIdHex) (S2/client/offline_start/common/ble/ClientBleInteractorOffline.smali:357-381: token получается на 361, command id на 371-377, конструктор на 379-381).

На проводе: attemptHex строится как Integer.toString(attempt, 16), toUpperCase(ROOT), padStart(8, 0) (BleControlPointChannelOffline$sendCommand$2$1.smali:241-265). Затем StringBuilder инициализируется commandIdHex.uppercase, добавляется attemptHex, добавляется tokenHex.uppercase, результат hex-декодируется и пишется (267-341: commandId на 301-303, attempt на 307-309, token на 313-315, сборка 319-329, decodeHex 331-341).

Порядок байт: commandId (1 байт), attempt (4 байта, старший байт первым), token (N байт). При токене 16 байт длина 21 байт. Для numeric attempt big-endian является inferred из текстового hex-представления.

Проверки перед записью (sendCommand$2$1.smali):

| Проверка | Сообщение | Строки |
| --- | --- | --- |
| attempt > 0 | Attempt must be positive | 239, 505-512 |
| длина commandId ровно 2 символа | Command id must be exactly 1 byte in hex | 281-285, 505 |
| длина attemptHex ровно 8 символов | Unique id must be exactly 4 bytes in hex | 287-291, 498 |
| длина tokenHex чётная | Token hex must have even length | 293-299, 491 |

### 5.7 Запись offline

Отправка выполняется внутри общего таймаута 30 секунд (S2/shared/ble/library_client_offline/BleControlPointChannelOffline.smali:79-87, sendCommand$2.smali:119, 191, 205). По истечении TimeoutGattWriteOperationException с текстом Offline start command timed out after ... (240-265).

Внутри цикл без ограничения числа попыток: запись через BleGattManagerOffline.f, ожидание завершения с таймаутом 500 мс (f=0x1f4, BleControlPointChannelOffline.smali:69-77; sendCommand$2$1.smali:446-458). Если за 500 мс завершения нет, лог Offline start write timed out on attempt N, will retry (392-405); при GattWriteOperationException лог Offline start write failed on attempt N, will retry (432-443). После этого ожидание того же deferred ещё 500 мс и при отсутствии результата attempt увеличивается на 1 и цикл повторяется (445-488). Успехом считается завершение deferred с true (467-481).

attempt начинается с 1 (239, 392). Счётчик живёт в рамках одной команды.

Запись синхронная: BleGattManagerOffline.f проверяет CONNECTED, вызывает BleGattWrapper.b; при false GattWriteOperationException с текстом Immediate GATT writeCharacteristic() failed; при не CONNECTED GattConnectionException с текстом Cannot write characteristic while disconnected, state= (BleGattManagerOffline.smali:336-401).

Повторная отправка всей команды: executeCommand делает 2 попытки с паузой 350 мс (ClientBleInteractorOffline.smali:871, 934), повторяет при GattConnectionException и NotConnectedDeviceException (983-984, 1028-1029, 1072-1073), на последней попытке пробрасывает исключение (1288-1289). Пауза 350 мс также на 1264.

### 5.8 Ответ offline

Notify принимается только от plaintext_out (BleControlPointChannelOffline.smali:204-215), байты превращаются в hex-строку в нижнем регистре (227-245).

Проверка первых трёх байт через helper b(int, byte[]): индексы 0..2, сравнение с маской 0xff, при выходе за границы возвращается false (104-193).

Ответ 0xBB 0xBB 0xBB: статус берётся из byte[8] только если длина больше 8 (247-283). Иначе BleOfflineStartMalformedResponseException с текстом IoT returned BBBBBB response without status byte valueHex= (467-481).

Статус ищется по BleOfflineStartStatus.getCode (285-335):

| Статус | Исключение или результат | Строки |
| --- | --- | --- |
| STATUS_OK | успех | 416-428 |
| STATUS_SCOOTER_ONLINE | BleOfflineStartFallbackToSmsException, текст IoT returned scooter online status statusByte= | 384-414 |
| другие известные | BleOfflineStartStatusException, текст IoT returned command status= плюс статус, statusByte=, valueHex= | 337-382 |
| неизвестный | BleOfflineStartUnknownStatusException, текст IoT returned BBBBBB response with unknown status statusByte= | 430-464 |

Ответ 0xEE 0xEE 0xEE: BleOfflineStartErrorResultException с текстом IoT returned EEEEEE response valueHex= (489-516). Иной формат игнорируется с логом ignored unsupported response format valueHex= (518-531); ожидание продолжается.

### 5.9 Статусы

| Код | Имя |
| --- | --- |
| 0 | STATUS_OK |
| 1 | STATUS_UNKNOWN_CMD |
| 2 | STATUS_INVALID_SIG |
| 3 | STATUS_EXPIRED |
| 4 | STATUS_REPLAY |
| 5 | STATUS_RTC_NOT_SYNCED |
| 6 | STATUS_TRIP_ACTIVE |
| 7 | STATUS_NO_ACTIVE_TRIP |
| 8 | STATUS_INVALID_USER |
| 9 | STATUS_LOG_FULL |
| 10 | STATUS_SCOOTER_ONLINE |
| 11 | STATUS_BATT_LOW_CAP |

Источник: J/bike/whoosh/shared/ble/library_client_offline/BleOfflineStartStatus.java:6-17.

### 5.10 Маппинг ошибок в BleOfflineStartFailure

Источник: J/bike/whoosh/client/offline_start/common/ble/ClientBleInteractorOffline.java:284-357.

| Исключение или статус | BleOfflineStartFailure | Строки |
| --- | --- | --- |
| STATUS_UNKNOWN_CMD | UnknownCommand | 296-298 |
| STATUS_INVALID_SIG | InvalidSignature | 299-301 |
| STATUS_EXPIRED | TokenExpired | 302-304 |
| STATUS_REPLAY | Replay | 305-307 |
| STATUS_RTC_NOT_SYNCED | RtcNotSynchronized | 308-310 |
| STATUS_TRIP_ACTIVE | TripAlreadyStarted | 311-313 |
| STATUS_NO_ACTIVE_TRIP | TripAlreadyFinished | 314-316 |
| STATUS_INVALID_USER | InvalidScooter | 317-319 |
| STATUS_LOG_FULL | TripLimitReached | 320-322 |
| STATUS_BATT_LOW_CAP | BatteryLowCapacity | 323-325 |
| STATUS_SCOOTER_ONLINE | ScooterOnline | 293-295 |
| STATUS_OK в маппинге | null и лог STATUS_OK status must not be mapped to failure | 290-292 |
| UnknownStatus | UnknownStatus с кодом | 330-331, 351-352 |
| MalformedResponse, ErrorResult | ProtocolError, код null для Malformed и 238 для ErrorResult | 332-333, 354 |
| TimeoutScan | DeviceNotFound | 338-339 |
| TimeoutGattConnection, GattConnection | ConnectionFailed | 340-341 |
| MissingBle, NotSupported, BleNotEnabled | BleUnavailable | 334-335 |
| Security, MissingPermissions | PermissionsDenied | 336-337 |
| FailedDiscoverServices, ServiceNotFound, TimeoutServiceResolver | ServiceNotFound | 342-343 |
| CharacteristicsNotFound | CharacteristicNotFound | 344-345 |
| TimeoutGattWrite, GattWrite, Descriptor, NotSupportRead, CommandExecute | ProtocolError | 346-348 |
| прочее | Unknown с message | 346-348 |

Код 238 для EEEEEE является подтверждённым, но его смысл в протоколе неизвестен.

### 5.11 SMS fallback

SMS автоматически не отправляется. При STATUS_SCOOTER_ONLINE или FallbackToSms результат OpenSms несёт номер и текст (S2/client/offline_start/offline_mobile/domain/OfflineMobileOfflineStartInteractor.smali:102-170, вызовы на 270 и 334), затем публикуется ExternalNavigationAction$OpenSms (S2/client/root/feature/DefaultAppRootFeatureComponent$executeOfflineStart$$inlined$launchSafe$default$1.smali:515-543). Открывается приложение SMS, отправку подтверждает пользователь (inferred по имени действия).

Тексты содержат кириллические гомоглифы: St\u0430rt и пробел для старта (OfflineMobileOfflineStartInteractor.smali:157), St\u043ep и пробел для стопа (141). К ним добавляется device code (код на 164-170).

Номер по умолчанию +79994445089 (J/bike/whoosh/shared/feature_config/domain/model/offline_start/OfflineStartSmsConfig.java:7).

### 5.12 Конфиги offline

| Параметр | Значение по умолчанию | Использование | Источник |
| --- | --- | --- | --- |
| connectionWaitingTimeoutDuration | 3000 мс | таймаут pingHealth в splash, не BLE | J/.../OfflineStartConfig.java:14-21 |
| ble.maxTripDuration | 1200 с | экран поездки, в BLE не передаётся | J/.../OfflineStartBleConfig.java:10-13 |

OfflineStartPayloadBuilder внедряется в BleControlPointChannelOffline, но конструктор его игнорирует, класс не используется (BleControlPointChannelOffline.smali:92-101).

## 6. Service master

Service master использует сервис 0xabf0, характеристику 0xabf1 и CCCD 0x2902 (S2/shared/ble/library_service_master/ServiceMasterBleSession$gattClientCallback$1.smali:357, 415, 466; ServiceMasterBleSession.smali:624). Это отдельный профиль, к командам самоката отношения не имеет (inferred по отсутствию вызовов из online и offline сессий).

Серверный профиль IotV1 и SIG UUID приведены в 4.1. Телеметрия 0xACF0 и 0xACF1 в APK отсутствует: совпадения acf0 и acf1 найдены только в цветах Compose и float-константе.

## 7. Сводка таймингов

| Режим | Операция | Значение | Повторы | Источник |
| --- | --- | --- | --- | --- |
| online | подключение | 10000 мс | 1 | BleGattManager.smali:72-78 |
| online | разрешение сервиса | 10000 мс | 1 | BleServiceResolver.smali:55-63 |
| online | подтверждение записи | 3000 мс | 1 на запись | BleGattManager.smali:80-86, 372 |
| online | ожидание ответа на попытку | 1500 мс | 10 записей | BleControlPointChannel$sendEncryptedCommandWithRetries$2.smali:228-235, 318-320 |
| online | пауза между попытками | нет | - | 380-394 |
| online | poll digital_output | 500 мс | 3 итерации внутри попытки | BleControlPointChannel.smali:64, 306-359 |
| online | сканирование | 10000 мс | 1 | BleScanner.smali:142 |
| online | опрос ключа | 500 мс | до получения | BleCommandKeyReader.smali:64, 141-164 |
| offline | сканирование | 20000 мс | 1 | BleScannerOfflineStart.smali:62, 164-166 |
| offline | подключение | 20000 мс | 2 попытки, пауза 350 мс | BleGattManagerOffline.smali:72-84; ClientBleSessionOffline.smali:596, 602, 719-727 |
| offline | запрос MTU 185 | 20000 мс | 1 | requestMtu$2.smali:162, 272-280 |
| offline | разрешение сервиса | 10000 мс | 1 | BleServiceResolverOffline.smali:55-63 |
| offline | ожидание записи на попытку | 500 мс | без лимита до 30 с | BleControlPointChannelOffline.smali:69-77; sendCommand$2$1.smali:445-488 |
| offline | общий таймаут команды | 30000 мс | 1 | BleControlPointChannelOffline.smali:79-87; sendCommand$2.smali:191, 205 |
| offline | повтор всей команды | пауза 350 мс | 2 попытки | ClientBleInteractorOffline.smali:871, 934, 1264 |

## 8. Тестовые векторы

Все hex-строки регистронезависимы при декодировании; приложение приводит части offline payload к верхнему регистру.

### 8.1 Offline

Вход: token = Base64 AAECAwQFBgcICQoLDA0ODw==, что даёт 16 байт 00 01 02 ... 0F и tokenHex 000102030405060708090A0B0C0D0E0F.

| Команда | attempt | Ожидаемый payload hex | Длина |
| --- | --- | --- | --- |
| START | 1 | 0100000001000102030405060708090A0B0C0D0E0F | 21 байт |
| STOP | 2 | 0200000002000102030405060708090A0B0C0D0E0F | 21 байт |
| START | 10 | 010000000A000102030405060708090A0B0C0D0E0F | 21 байт |
| START | 256 | 0100000100000102030405060708090A0B0C0D0E0F | 21 байт |

Побайтово для START attempt 1:

| Смещение | Байт | Смысл |
| --- | --- | --- |
| 0 | 01 | commandId START |
| 1 | 00 | attempt, старший байт |
| 2 | 00 | attempt |
| 3 | 00 | attempt |
| 4 | 01 | attempt, младший байт |
| 5 | 00 | token, байт 0 |
| 6 | 01 | token, байт 1 |
| 7-20 | 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F | token, байты 2-15 |

Ожидаемые ошибки payload:

| Вход | Исключение | Сообщение |
| --- | --- | --- |
| attempt 0 | IllegalArgumentException | Attempt must be positive |
| commandId 1 | IllegalArgumentException | Command id must be exactly 1 byte in hex |
| attemptHex длины не 8 | IllegalArgumentException | Unique id must be exactly 4 bytes in hex |
| tokenHex нечётной длины | IllegalArgumentException | Token hex must have even length |

Ответы:

| Байты от plaintext_out | Результат |
| --- | --- |
| BB BB BB 00 00 00 00 00 00 | STATUS_OK, успех |
| BB BB BB 00 00 00 00 00 0A | FallbackToSmsException, статус 10 |
| BB BB BB 00 00 00 00 00 0B | BleOfflineStartStatusException, статус 11 |
| BB BB BB 00 00 00 00 00 0C | UnknownStatusException, код 12 |
| BB BB BB | MalformedResponseException |
| EE EE EE | ErrorResultException, далее ProtocolError с кодом 238 |
| 00 00 00 | ответ игнорируется |

### 8.2 Online

| Вход | Ожидаемое поведение |
| --- | --- |
| bleCommand 0102AB | запись байт 01 02 AB в 0x9f2a |
| bleCommand 0102A | IllegalArgumentException от okio, Unexpected hex string |
| notify 43 4D 44 5F 45 58 45 5F 53 55 43 43 45 53 53 (CMD_EXE_SUCCESS) | успех |
| notify CMD_EXE_SUCCESS_EXTRA | успех, проверка по префиксу |
| notify CMD_EXE_ERROR:reason | CommandExecuteException с текстом Command execution failed: CMD_EXE_ERROR:reason |
| notify иная строка | ожидание продолжается |
| ключ 00 01 ... 13 (20 байт) | hex ключа 000102030405060708090a0b0c0d0e0f, первые 16 байт, нижний регистр |
| ключ короче 16 байт | исключение при копировании первых 16 байт (inferred) |

## 9. Исправления к report_whoosh.md

| Утверждение старого отчёта | Факт | Источник |
| --- | --- | --- |
| Команда кодируется в UTF-8 и пишется в 0x9f2a | Команда приходит с backend hex-строкой, декодируется decodeHex и пишется байтами; UTF-8 применяется только к notify-ответу | ClientBleSession.smali:252-262; BleControlPointChannel.smali:136-146 |
| Ожидание ответа до 3 раз по 500 мс | 10 записей, каждая с ожиданием до 1500 мс; 3 по 500 мс это poll digital_output внутри попытки | sendEncryptedCommandWithRetries$2.smali:228-235, 318-320; BleControlPointChannel.smali:306-359 |
| Пауза между повторами 500 мс | Паузы между online попытками нет; 500 мс это интервал poll и опроса ключа | sendEncryptedCommandWithRetries$2.smali:380-394; BleControlPointChannel.smali:64; BleCommandKeyReader.smali:64 |
| Таймаут на последней попытке является ошибкой | Таймаут на 10-й попытке логируется, затем идёт ожидание deferred без таймаута; ошибки нет только при позднем успехе, иначе CommandExecuteException или зависание | sendEncryptedCommandWithRetries$2.smali:337-360 |
| Write 3 с это ожидание ответа | 3 с это ожидание подтверждения записи конкретной характеристики; ответ команды ждётся отдельно | BleGattManager.smali:372, 436-483 |
| Полные UUID клиента фиксированы | Клиент сравнивает только старшие 32 бита; полные 128 бит не заданы | BleServiceResolver.smali:172, 196-306 |
| Offline протокол отсутствует | Offline описан в разделе 5: 0xE018, 0xDD1A, 0xDD2A, payload commandId + attempt + token, статусы BBBBBB | раздел 5 |

## 10. Что отсутствует в APK

| missing | Что нужно для закрытия |
| --- | --- |
| Байты и имена конкретных команд (lock, unlock, ring и другие) | перехват bleCommand и bleCommand-ответа backend на реальном аккаунте |
| Алгоритм шифрования и ключ, которыми backend формирует bleCommand | серверная часть или дамп трафика с известным ключом |
| Реальные notify-ответы самоката online | GATT-лог или HCI snoop с успешной и ошибочной командой |
| Формат и длина binaryToken, схема подписи | ответ trips/offline/token и документ протокола прошивки |
| Смысл байт 3-7 в ответе BBBBBB и кода 238 | прошивка или производитель |
| Реальные значения feature toggles offlineStartMatchAnyWshDeviceFeatureToggle и BLE2Pattern в проде | remote config продакшена |
| Фактический MTU и поведение при отказе requestMtu на разных телефонах | hardware-тест |
| Нужен ли самокату LSB-first attempt | hardware-тест с attempt больше 255 |
| Есть ли шифрование offline payload на устройстве | hardware-тест и дамп HCI |

## 11. Источники

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/ClientBleSession.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleControlPointChannel.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleControlPointChannel$sendEncryptedCommandWithRetries$2.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleControlPointChannel$sendEncryptedCommandWithRetries$2$1.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleGattManager.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleGattManager$connect$2.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleGattWrapper.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleServiceResolver.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleNotificationManager.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleCommandKeyReader.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/IotV2Characteristics.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client_offline/BleControlPointChannelOffline.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client_offline/BleControlPointChannelOffline$sendCommand$2.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client_offline/BleControlPointChannelOffline$sendCommand$2$1.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client_offline/ClientBleSessionOffline.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client_offline/BleGattManagerOffline.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client_offline/BleGattManagerOffline$connect$2.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client_offline/BleGattManagerOffline$requestMtu$2.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client_offline/BleServiceResolverOffline.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client_offline/BleOfflineStartCommandPayload.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client_offline/BleOfflineStartStatus.smali

whoosh_dec/smali_classes2/bike/whoosh/client/offline_start/common/ble/ClientBleInteractorOffline.smali

whoosh_dec/smali_classes2/bike/whoosh/client/offline_start/common/ble/BleOfflineStartTokenDecoder.smali

whoosh_dec/smali_classes2/bike/whoosh/client/offline_start/common/ble/BleOfflineStartCommandId.smali

whoosh_dec/smali_classes2/bike/whoosh/client/offline_start/offline_mobile/domain/OfflineMobileOfflineStartInteractor.smali

whoosh_dec/smali_classes2/bike/whoosh/client/root/feature/DefaultAppRootFeatureComponent$executeOfflineStart$$inlined$launchSafe$default$1.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/scanner/BleScanner.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/scanner/BleScanner$filterScan$$inlined$filter$1$2.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/scanner/BleScannerOfflineStart.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/scanner/BleScannerOfflineStart$scanForDeviceToConnect$found$1$2.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/scanner/OfflineStartMatchAnyWshDeviceFeatureToggle.smali

whoosh_dec/smali_classes4/bike/whoosh/shared/ble/library_scanner/BleLibraryScanner$scan$1.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/connection/BleConnection.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_server/IotV1Specifications.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_service_master/ServiceMasterBleSession$gattClientCallback$1.smali

whoosh_dec/smali_classes2/bike/whoosh/ble/domain/ClientBleInteractor.smali

whoosh_dec/smali/bike/whoosh/client/ble_iot/ClientBleCommandEncryptor.smali

whoosh_dec/smali/bike/whoosh/client/ble_iot/ClientBleCommandEncryptor$encryptCommand$2.smali

whoosh_dec/smali_classes4/okio/ByteString$Companion.smali

whoosh_jadx/sources/bike/whoosh/client/api/ClientApiService.java

whoosh_jadx/sources/bike/whoosh/client/api/ble/BleResponseApiModel.java

whoosh_jadx/sources/bike/whoosh/client/api/trip/offline/OfflineStartTokenResponseApiModel.java

whoosh_jadx/sources/com/punicapp/whoosh/model/request/BleCommandRequestBody.java

whoosh_jadx/sources/bike/whoosh/client/offline_start/common/ble/ClientBleInteractorOffline.java

whoosh_jadx/sources/bike/whoosh/shared/ble/library_client_offline/BleOfflineStartStatus.java

whoosh_jadx/sources/bike/whoosh/shared/feature_config/domain/model/offline_start/OfflineStartConfig.java

whoosh_jadx/sources/bike/whoosh/shared/feature_config/domain/model/offline_start/OfflineStartBleConfig.java

whoosh_jadx/sources/bike/whoosh/shared/feature_config/domain/model/offline_start/OfflineStartSmsConfig.java
