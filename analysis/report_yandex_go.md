# Yandex Go 5.98.2: BLE

## 1. Артефакт

| Параметр | Значение |
| --- | --- |
| Файл | references/apks/yandex-go-5.98.2-base.apk |
| Пакет | ru.yandex.taxi |
| Версия | 5.98.2 (50128633) |
| minSdk / targetSdk | 32 / 36 |
| compileSdk | 37 |
| Размер | 192 834 008 байт |
| SHA-256 | 27A75FDF62C9AB601BDDD89C6559E75231F868AC3F08FA463AE7A85D8B5C1862 |
| Подпись | CN=OOO Yandex, SHA-256 ACA405DED8B25CB2E8C6DA69425D2B4307D087C1276FC06AD5942731CCC51DBA |

Источник: ADB, устройство SM-A175F. Сохранён только base APK. Native-библиотеки находятся в split arm64 и в этот каталог не входят. BLE-код находится в dex.

## 2. Состав BLE-модуля

Пакет com.yandex.go.scooters.ble содержит:

| Класс | Роль |
| --- | --- |
| ScootersBleInteractorImpl | подключение и выполнение команд |
| ScootersBleProtocol | enum NINEBOT, WIND |
| ScootersBleCommandType | enum AUTH, LOCK, UNLOCK, SOUND, CABLE_UNLOCK, UNLOCK_BATTERY_COVER |
| ScootersBleNinebotRepository | GATT, запись, ожидание ответа |
| ScootersBleNinebotProtocolInteractor | сборка и разбор Ninebot-команд |
| ScootersBleConnectExtensionsKt | подключение, поиск сервиса, включение notify |
| domain/model/ninebot | результаты и ошибки |

ScootersBleProtocol объявляет два протокола, NINEBOT и WIND. Реализация в этой сборке относится к Ninebot. Классов реализации WIND в найденных путях нет.

Модуль com.yandex.go.scooters.ignition относится к ignition/QR и не является BLE-протоколом управления самокатом.

## 3. Провайдер UUID и ключей

Соединение получает UUID через интерфейсы za и ab:

| Метод | Значение |
| --- | --- |
| za.a() | UUID сервиса |
| za.e() | UUID notify |
| za.b() | UUID write |
| ab.a() | UUID CCCD |
| za.d() | целое, первые два байта payload |
| za.c() | 4 байта ключа |
| za.f(byte[]) | преобразование исходящего payload |
| za.g(byte[]) | преобразование входящего notify |

Провайдеры реализованы классами ghq0 и i2r0. Оба являются singletons с абстрактным методом b(). Единственные реализации fhq0 и h2r0 возвращают null:

| Класс | Метод | Результат |
| --- | --- | --- |
| fhq0 | b()Lza; | null |
| h2r0 | b()Lab; | null |

В dex не найдено ни другой реализации, ни вызова установки ghq0.a(...). Поэтому в этой сборке ScootersBleNinebotRepository.connectScooter при обращении к za.a() получит null. Статических UUID, статического ключа и реализации шифрования в APK нет.

Это означает, что Ninebot BLE-путь в 5.98.2 присутствует как код, но не активируется без внешней подстановки провайдера. Считать его рабочим клиентом разблокировки нельзя.

## 4. Подключение

ScootersBleNinebotRepository.connectScooter:

1. Получает BluetoothDevice по MAC.
2. Берёт service, notify и write UUID из za.
3. Берёт CCCD UUID из ab.
4. Вызывает connectGatt с TRANSPORT_LE.
5. После discoverServices получает сервис и характеристики.
6. Включает setCharacteristicNotification.
7. Записывает ENABLE_NOTIFICATION_VALUE в CCCD.
8. Сохраняет соединение в поле d.

ScootersBleInteractorImpl.connect после подключения сразу выполняет AUTH.

## 5. Команды

Есть два сборщика payload.

nap0 собирает AUTH:

| Смещение | Значение |
| --- | --- |
| 0..1 | za.d(), старший и младший байт |
| 2 | 0x08 |
| 3 | 0x00 |
| 4 | 0x00 |
| 5 | 0x01 |
| 6..14 | ASCII-ключ, максимум 9 байт |

nep0 собирает остальные команды. В общем виде payload содержит два байта za.d(), затем 0x01, 0x00, аргумент, код команды, параметр и 0x00.

| Команда | Сборщик | Код | Параметр | Аргумент |
| --- | --- | --- | --- | --- |
| AUTH | nap0 | 0x08 | нет | ключ |
| LOCK | nep0 index 1 | 0x15 | 0x01 | байт из pcp0.a |
| UNLOCK | nep0 index 5, затем index 6 | 0x0A, затем 0x05 | 0x02 | байт из pcp0.a |
| SOUND | nep0 index 3 | 0xA2 | 0x08 | байт из pcp0.a |
| CABLE_UNLOCK | nep0 index 0 | 0x81 | 0x01 | boolean |
| UNLOCK_BATTERY_COVER | nep0 index 6 | 0x05 | 0x02 | байт из pcp0.a |

UNLOCK состоит из двух записей. Первая имеет длину 17 байт: два байта za.d(), 0x0A, 0x00, аргумент, 0x01, 0x01, четыре байта za.c(), четыре байта UNIX-секунд big-endian, 0x00, 0x00. Вторая запись имеет длину 8 байт: два байта za.d(), 0x01, 0x00, аргумент, 0x05, 0x02, 0x00.

Эти байты являются payload до za.f. Результат za.f и есть тело GATT write. Алгоритм za.f в APK отсутствует.

## 6. Ответ и таймауты

| Параметр | Значение |
| --- | --- |
| Ожидание GATT write | 1000 мс |
| Ожидание notify | 5000 мс |
| Транспорт | TRANSPORT_LE |
| CCCD | 0x2902, значение из ab |
| Разбор ответа | n9r0, oap0, oep0, xir0 и другие обёртки |

Ответ приходит в notify, проходит через za.g, затем разбирается в один из классов результата. При отсутствии ответа выбрасывается ScootersBleResponseTimeoutException. При ошибке записи или подключения используются ScootersBleConnectionFailureException и ScootersBleLockFailedException.

## 7. Что не подтверждено

Реальная разблокировка Yandex Go не проверена. Нет самоката, нет GATT-дампа, нет действующего провайдера UUID и ключа.

UUID сервиса, write, notify и CCCD не извлечены, потому что провайдер возвращает null. Любые значения из сторонних источников не подтверждены этим APK.

Ключ AUTH, четыре байта za.c(), алгоритм za.f и za.g отсутствуют в dex. Без них команды остаются описанием структуры, а не готовым клиентом.

Не подтверждено, что WIND-протокол реализован в этой сборке. Enum содержит WIND, но реализация не найдена.

## 8. Источники

yandex_dec/smali_classes17/com/yandex/go/scooters/ble/ScootersBleInteractorImpl$connect$1.smali

yandex_dec/smali_classes17/com/yandex/go/scooters/ble/a.smali

yandex_dec/smali_classes17/com/yandex/go/scooters/ble/api/ScootersBleProtocol.smali

yandex_dec/smali_classes17/com/yandex/go/scooters/ble/domain/model/ScootersBleCommandType.smali

yandex_dec/smali_classes17/com/yandex/go/scooters/ble/domain/ninebot/a.smali

yandex_dec/smali_classes17/com/yandex/go/scooters/ble/data/ninebot/a.smali

yandex_dec/smali_classes17/com/yandex/go/scooters/ble/data/ninebot/ScootersBleNinebotRepository$sendCommandWaitingResponse$response$1.smali

yandex_dec/smali_classes15/com/yandex/go/scooters/ble/utils/ScootersBleConnectExtensionsKt$awaitConnect$2$bluetoothGatt$1.smali

yandex_dec/smali_classes17/nap0.smali

yandex_dec/smali_classes17/nep0.smali

yandex_dec/smali_classes17/ghq0.smali

yandex_dec/smali_classes17/fhq0.smali

yandex_dec/smali_classes17/i2r0.smali

yandex_dec/smali_classes17/h2r0.smali

yandex_dec/smali_classes17/za.smali

yandex_dec/smali_classes17/ab.smali
