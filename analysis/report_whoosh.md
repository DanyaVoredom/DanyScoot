# Whoosh 4.0.0: BLE

## 1. Артефакт

| Параметр | Значение |
| --- | --- |
| Файл | references/apks/whoosh-4.0.0-base.apk |
| Пакет | com.punicapp.whoosh |
| Версия | 4.0.0 (418060) |
| minSdk / targetSdk | 32 / 36 |
| Размер | 54 707 264 байт |
| SHA-256 | 912079486F06E84581C92055C3E87DAF8C3CB11F55B8F81F41BA907ADDB53C29 |
| Подпись | CN=Whoosh.Bike, SHA-256 12607A1082202FEAFAA03AC22A3814F9EC770D731D87C667CE9FCA7B164F6BAE |

Источник: ADB, устройство SM-A175F. Сохранён только base APK. Split APK с native-библиотеками не входит в references/apks.

## 2. BLE-стек

В dex присутствуют четыре библиотеки:

| Пакет | Назначение |
| --- | --- |
| bike.whoosh.shared.ble.library_client | клиентская сессия, GATT, ключ команды, control point |
| bike.whoosh.shared.ble.library_client_offline | офлайн-сессия |
| bike.whoosh.shared.ble.library_server | эмуляция самоката, SIG-профиль |
| bike.whoosh.shared.ble.library_scanner | сканирование |

Клиентский путь:

| Класс | Роль |
| --- | --- |
| ClientBleSession | сессия IoTScooterBleSession |
| BleGattManager | подключение, очередь write, таймауты |
| BleServiceResolver | поиск сервиса и характеристик |
| BleNotificationManager | включение notify через CCCD |
| BleCommandKeyReader | чтение ключа команды |
| BleDigitalOutputReader | чтение digital output |
| BleControlPointChannel | запись зашифрованной команды и ожидание результата |
| ClientBleCommandEncryptor | получение шифртекста через API |

## 3. UUID

Клиентский BleServiceResolver сравнивает старшие 32 бита UUID сервиса с 0x1c18, затем ищет характеристики 0x572a, 0x3d2a, 0x9f2a.

| Роль | UUID | Короткий ID |
| --- | --- | --- |
| Сервис | 00001c18-0000-1000-8000-00805f9b34fb | 1c18 |
| Digital output | 0000572a-0000-1000-8000-00805f9b34fb | 572a |
| String | 00003d2a-0000-1000-8000-00805f9b34fb | 3d2a |
| User control point | 00009f2a-0000-1000-8000-00805f9b34fb | 9f2a |
| CCCD | 00002902-0000-1000-8000-00805f9b34fb | 2902 |

Серверная библиотека IotV1Specifications использует стандартные SIG UUID:

| Роль | UUID |
| --- | --- |
| Сервис | 0000181c-0000-1000-8000-00805f9b34fb |
| Digital output | 00002a57-0000-1000-8000-00805f9b34fb |
| String | 00002a3d-0000-1000-8000-00805f9b34fb |
| User control point | 00002a9f-0000-1000-8000-00805f9b34fb |

Расхождение существенное. Клиентские константы 1c18, 572a, 3d2a, 9f2a являются байтово переставленными версиями 181c, 2a57, 2a3d, 2a9f. В коде клиента сравнение идёт с переставленными значениями, поэтому рабочий клиент должен искать именно 00001c18, 0000572a, 00003d2a, 00009f2a. Серверная библиотека описывает стандартный профиль и для реального самоката не является источником UUID клиента.

## 4. Роли характеристик

| Характеристика | Роль |
| --- | --- |
| 0x572a | digital output, чтение и notify |
| 0x3d2a | string, чтение ключа команды |
| 0x9f2a | user control point, запись команды и notify результата |

BleServiceResolver при отсутствии характеристики выбрасывает разные исключения: digital_output not found для 572a, string not found для 3d2a, user_control_point not found для 9f2a. Это подтверждает назначение каждой характеристики.

## 5. Команда и ключ

ClientBleSession.executeEncryptedCommand принимает строку команды. Далее BleControlPointChannel.sendEncryptedCommandWithRetries:

1. Подписывается на notify.
2. Записывает UTF-8 строку в 0x9f2a.
3. Ждёт notify.
4. Считает успехом строку, начинающуюся с CMD_EXE_SUCCESS.
5. Считает ошибкой строку, начинающуюся с CMD_EXE_ERROR.
6. Повторяет операцию до 3 раз с задержкой 500 мс.

BleCommandKeyReader читает ключ из 0x3d2a. Опрос идёт с интервалом 500 мс. Пустой или нулевой payload игнорируется. Ключ нужен для запроса шифрования команды.

ClientBleCommandEncryptor не содержит алгоритма шифрования. Он вызывает ClientApiServiceManager и передаёт BleCommandRequestBody с полями command и commandKey. Сервер возвращает зашифрованную строку. В APK нет статического ключа, статического вектора и реализации шифра.

Имена команд в BLE-библиотеке отсутствуют. Встречаются только служебные строки CMD_EXE_SUCCESS и CMD_EXE_ERROR. Сами команды приходят из backend и не являются литералами APK.

## 6. Таймауты и GATT

| Параметр | Значение |
| --- | --- |
| Таймаут подключения | 10 с |
| Таймаут операции write | 3 с |
| Интервал опроса ключа | 500 мс |
| Количество повторов команды | 3 |
| Задержка между повторами | 500 мс |
| CCCD | 2902 |
| Тип записи | определяется свойствами характеристики |

BleGattManager хранит две Duration-константы: 10 секунд и 3 секунды. Write выполняется через очередь и CompletableDeferred с таймаутом.

## 7. Что не подтверждено

Работоспособность на реальном самокате Whoosh не проверена. Нет hardware-теста, нет дампа GATT, нет подтверждённого ответа самоката на команду.

Не подтверждено, что реальный самокат использует именно 00001c18 и переставленные характеристики. Это следует из кода клиента, но требует проверки сканером.

Не подтверждены формат command, формат commandKey и алгоритм шифрования. Они находятся на стороне API и в APK отсутствуют.

## 8. Источники

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleServiceResolver.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/ClientBleSession.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleControlPointChannel.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleCommandKeyReader.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleGattManager.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleNotificationManager.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_client/BleDigitalOutputReader.smali

whoosh_dec/smali_classes2/bike/whoosh/shared/ble/library_server/IotV1Specifications.smali

whoosh_dec/smali/bike/whoosh/client/ble_iot/ClientBleCommandEncryptor.smali

whoosh_dec/smali/com/punicapp/whoosh/model/request/BleCommandRequestBody.smali
