# Карта источников

Индекс для проверки любого утверждения из analysis. Пути LostBot/FishBot указаны к jadx-декомпиляции analysis/java/fishbot/sources, если не указано иное. Пути NineHack указаны к analysis/NineHack.

## 1. LostBot и FishBot

| Утверждение | Файл | Строки |
| --- | --- | --- |
| Каталог 54 опкодов | A2/c.java | 19-36 |
| Имена и разбор входящих payload | A2/c.java | 50-256 |
| Разбор ответов и статусов | A2/c.java | 258-399 |
| Сборка кадра | A2/f.java | 20-50 |
| Проверка header, длины и CRC | A2/f.java | 70-90 |
| Таблица CRC8 | A2/f.java | 13 |
| UUID сервиса и характеристик | z2/i.java | 115-124 |
| connectGatt TRANSPORT_LE | z2/i.java | 335 |
| Watchdog 20000 мс | z2/i.java | 333 |
| Запись характеристики | z2/i.java | 430-437 |
| requestMtu 247 | LostBot smali/D2/s.1.smali | 128-131 |
| onMtuChanged | z2/g.java | 100 |
| Рукопожатие cmd 0x01 | C2/F.java | 366-379 |
| Проверка ответа и sessionKey | C2/F.java | 392-399 |
| Прием и диспетчеризация | C2/F.java | 1102-1163 |
| Прошивка START 0xFB | C2/F.java | 776-836 |
| Запрос пакета 0xFC | C2/F.java | 1323-1328 |
| Спам 0x05 | C2/F.java | 1296-1320 |
| Сервисные команды | C2/o.java | 36-141 |
| Быстрые команды | D2/C0141b.java | 29-83 |
| MQTT, SIM, reset, AVAS toggle | D2/C0148e0.java | 28-70 |
| Удаление RFID | D2/C0175s0.java | 36 |
| Цветной свет | D2/C0166n0.java | 274 и 401 |
| AVAS payload | I1/a.java | 737-745 |
| Массивы ключа w4S3Hqn5 | z2/l.java | 10-16 |
| Обфускация ключа | D2/C0141b.java | 81-85 |
| Demo controller | C2/F.java | 340-460 |
| Demo IoT network | C2/F.java | 420-482 |
| Demo firmware | C2/F.java | 830-836 |

## 2. NineHack

| Утверждение | Файл | Строки |
| --- | --- | --- |
| UUID сервиса | controlActivity.smali | 35 |
| UUID notify | controlActivity.smali | 44 |
| UUID write | controlActivity.smali | 53 |
| CCCD | controlActivity.smali | 264 |
| connectGatt TRANSPORT_LE | controlActivity.smali | 237 |
| Включение нотификаций через readDescriptor | controlActivity.smali | 254-292 |
| Запись ключа | controlActivity.smali | 689-773 |
| Ключ по умолчанию | res/values/strings.xml | 44 |
| SharedPreferences ninehack_settings | controlActivity.smali | 716-734 |
| Ответ рукопожатия | CommandCreator.smali | 100-107 |
| Сборка кадра | CommandCreator.smali | 855-941 |
| Ключевой кадр | CommandCreator.smali | 669-752 |
| Rand 1..254 | CommandCreator.smali | 766-778 |
| XOR payload | CommandCreator.smali | 828-845 |
| CRC8 | Crc8.smali | 45-317 |
| Разбор входящего кадра | CommandCreator.smali | 1362-1529 |
| Команды | CommandCreator.smali | 52-60 |
| Ошибка CRC от устройства | CommandCreator.smali | 201 |
| Экран настроек ключа | bleSettingsActivity.smali | 19-252 |
| Сканирование 10000 мс | scanActivity.smali | 9 и 317-319 |

## 3. Официальный Urent

| Утверждение | Источник |
| --- | --- |
| Пакет ru.urentbike.app | AndroidManifest.xml обеих версий |
| Нет android.bluetooth классов | поиск по всем smali обеих версий |
| Нет BLE-разрешений | AndroidManifest.xml |
| useBluetooth=false | TransportRepositoryImpl$getMapFreeTransport$2 |
| AvailableViaBluetooth только серверное состояние | VehicleResponse.State |
| openlock и closelock | OrderingRepositoryImpl |
| API /api/v1/scooters/openlock/{id} | OrderingRepositoryImpl |
| API /api/v1/scooters/closelock/{id} | OrderingRepositoryImpl |
| API /api/v1/connect/token | auth-слой |
| API /api/v3/transports/ | TransportRepositoryImpl |
| API /api/v6/transports | TransportRepositoryImpl |
| base host service.urentbike.ru | сетевой конфиг |
| Нет ключей 4BKNwi77 и w4S3Hqn5 | поиск по всем smali |
| Нет UUID 6e400001/2/3 | поиск по всем smali |
| Нет BluetoothAdapter, BluetoothGatt, BluetoothLeScanner | поиск по всем smali |

## 4. Публичный источник

| Утверждение | Источник |
| --- | --- |
| Публичное описание протокола | https://raw.githubusercontent.com/wiki/bitplugg/XDDbot/Protocol.md |
| Публичная реализация | https://github.com/bitplugg/XDDbot |
| Расхождение по handshake | wiki считает payload[0] deviceType; APK считают payload[0] статусом, payload[1] sessionKey |

## 5. Машиночитаемые данные

| Файл | Содержимое |
| --- | --- |
| references/opcodes.csv | 54 опкода, направление, payload, источник, декодер, поддержка DanyScoot |
| references/frames.json | 48 TX, 20 RX, 4 битых кадра, CRC-константы |
| references/apks.csv | инвентарь APK, размеры, SHA-256, подписи |
| references/manifests.json | манифесты шести приложений |
| references/permissions.csv | разрешения с категориями и риском |
| references/components.csv | компоненты приложений |
| references/uuids.csv | UUID Urent |
| references/generate_frames.py | генератор frames.json без внешних зависимостей |
| references/verify_frames.py | независимый разбор кадров и проверка CRC |
| references/validate_all.py | проверка CSV, JSON, APK-хешей и кадров |
| references/README.md | хеши APK, подписи, ссылки, команды проверки |

## 6. DanyScoot

| Утверждение | Файл | Строки |
| --- | --- | --- |
| Кадр и CRC | app/app/src/main/java/ru/dany/scoot/ble/Frame.kt | 55-161 |
| Команды и декодеры | app/app/src/main/java/ru/dany/scoot/ble/ScooterCodec.kt | 56-300 |
| Каталог опкодов | app/app/src/main/java/ru/dany/scoot/ble/OpcodeCatalog.kt | 1-200 |
| Скан, GATT, MTU, очередь | app/app/src/main/java/ru/dany/scoot/ble/BleTransport.kt | 37-529 |
| Состояние, auth, спам, демо | app/app/src/main/java/ru/dany/scoot/core/ScooterClient.kt | 24-780 |
| Настройки | app/app/src/main/java/ru/dany/scoot/core/Prefs.kt | 12-121 |
| Журнал | app/app/src/main/java/ru/dany/scoot/core/LogBus.kt | 16-47 |
| Спам-сервис | app/app/src/main/java/ru/dany/scoot/spam/SpamService.kt | 1-200 |
| Разрешения и вкладки | app/app/src/main/java/ru/dany/scoot/MainActivity.kt | 30-193 |
| Темы | app/app/src/main/java/ru/dany/scoot/AppTheme.kt, res/values/themes.xml | 8-24, 4-182 |
| Манифест | app/app/src/main/AndroidManifest.xml | 1-40 |
| Сборка | app/app/build.gradle.kts | 1-80 |

## 7. Документация

| Документ | Что проверять |
| --- | --- |
| analysis/app_inventory.md | пакеты, версии, SDK, компоненты, атрибуты |
| analysis/permissions.md | разрешения и опасные наборы |
| analysis/network.md | endpoint-ы и отсутствие сети |
| analysis/storage.md | SharedPreferences, ключи, токены |
| analysis/comparison.md | функции и команды референсов |
| analysis/opcode_reference.md | 54 опкода и уверенность |
| analysis/telemetry.md | поля и формулы телеметрии |
| analysis/firmware.md | OTA 0xFA, 0xFB, 0xFC |
| analysis/errors.md | 0x10 и статусы |
| analysis/test_vectors.md | схема frames.json |
| analysis/method.md | порядок анализа и уровни |
| analysis/limitations.md | неизвестные и blockers |
| analysis/obfuscation.md | R8, StringFog, упаковщики |
| analysis/ui_screens.md | экраны референсов |
| docs/README.md | индекс документации приложения |
