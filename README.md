# DanyScoot

Android-приложение для работы с BLE-модулем электросамокатов Urent. Протокол восстановлен по референсным APK из каталога references: LostBot, FishBot, NineHack, а также по официальным Urent, Whoosh и Yandex Go. Приложение объединяет транспорт и телеметрию LostBot с командами и семантикой NineHack, добавляет демо-режим для проверки интерфейса без самоката.

Пакет: ru.dany.scoot. Версия: 1.1.1 (3). Минимальная Android: 6.0 (API 23). Целевая: Android 15 (API 35).

## Быстрый старт

Сборка отладочного APK:

```
cd app
.\gradlew.bat --no-daemon assembleDebug
```

Результат: app\app\build\outputs\apk\debug\app-debug.apk.

Установка на подключённое устройство:

```
adb install -r app\app\build\outputs\apk\debug\app-debug.apk
```

Запуск тестов:

```
cd app
.\gradlew.bat --no-daemon testDebugUnitTest
```

Полная проверка:

```
cd app
.\gradlew.bat --no-daemon testDebugUnitTest assembleDebug lintDebug assembleRelease
```

## Документация

Корневые документы: CHANGELOG.md, CONTRIBUTING.md, SECURITY.md, LICENSE.

| Документ | Содержимое |
| --- | --- |
| docs/README.md | индекс документации проекта и порядок чтения |
| docs/architecture.md | слои, состояние, потоки данных, жизненный цикл |
| docs/development.md | карта исходников, соглашения, добавление команд |
| docs/build.md | требования, сборка, варианты, типовые ошибки |
| docs/release.md | версии, подпись, артефакты, публикация |
| docs/ble-lifecycle.md | скан, GATT, MTU, очередь записи, переподключение |
| docs/protocol-implementation.md | кадр, CRC, рукопожатие, команды, расхождения |
| docs/data-model.md | настройки, StateFlow, журнал, хранение |
| docs/ui.md | экраны, элементы управления, состояния |
| docs/themes.md | пять тем и правила добавления темы |
| docs/testing.md | unit-тесты, векторы, ручная матрица, hardware |
| docs/compatibility.md | Android, BLE-адаптеры, семейства самокатов |
| docs/troubleshooting.md | симптомы, причины, действия |
| docs/faq.md | частые вопросы |
| docs/glossary.md | термины протокола и Android BLE |
| docs/roadmap.md | направления развития и не-цели |
| docs/security.md | модель угроз, хранение ключей, приватность |
| docs/legal.md | правовые ограничения и ответственность |
| analysis/README.md | индекс анализа референсов и протокола |
| references/README.md | артефакты, хеши, подписи, проверка данных |

## Возможности

- Сканирование BLE с фильтром по сервису Nordic UART, отображением RSSI и режимом показа всех устройств.
- Подключение по GATT с таймаутом 20 секунд, запросом MTU 247, включением уведомлений и записью чанками по MTU-3.
- Рукопожатие ключом через команду 0x01, приём сессионного ключа, таймаут авторизации 3 секунды.
- Разблокировка 0x05, блокировка 0x15, изъятие батареи 0x81, поиск самоката 0xA2, настройка фары, режима и газа 0x61.
- Спам разблокировки с временной меткой, интервалом 20..10000 мс, foreground-сервисом, счётчиком и автовозобновлением.
- Телеметрия: устройство и IoT 0x31, самокат 0x60, реальное время 0x63, RFID 0x85 и 0x86.
- Консоль ручных команд с каталогом из 54 опкодов и произвольным hex-payload.
- Сервисные команды: крышка батареи 0xC0, перезапуск IoT 0x92, очистка Bluetooth 0x54, RGB-подсветка 0x82, питание 0x91, чтение конфига 0xFA.
- Журнал TX, RX, INFO, OK, WARN, ERR с автопрокруткой.
- Пять тем: black, white, light, dark, amoled.
- Настройки ключа с пресетами NineHack 4BKNwi77 и LostBot w4S3Hqn5 и автоподбором второго ключа при отказе, интервал спама, переподключение, фильтры сканирования и каталога.
- Экспериментальные проверки Urent на телефоне: unlock без auth с key 0 и перебор session key 0..255.
- Демо-режим с виртуальным самокатом, растущей скоростью, ответами на команды и телеметрией без Bluetooth.

## Архитектура

```
app/app/src/main/java/ru/dany/scoot/
  App.kt                    инициализация клиента и журнала
  AppTheme.kt               пять тем и выбор текущей
  MainActivity.kt           нижняя навигация, разрешения, статус
  ble/Frame.kt              кадр A3 A4, CRC8, CRC16, hex, поиск кадров
  ble/ScooterCodec.kt       сборка команд и декодирование ответов
  ble/BleTransport.kt       скан, GATT, MTU, очередь записи, уведомления
  ble/OpcodeCatalog.kt      каталог опкодов и направлений
  core/ScooterClient.kt     состояние, авторизация, спам, телеметрия
  core/Prefs.kt             SharedPreferences danyscoot_prefs
  core/LogBus.kt            кольцевой журнал и StateFlow
  spam/SpamService.kt       foreground-сервис спама
  ui/                       устройства, управление, журнал, настройки
```

Подробнее: docs/architecture.md.

## Совместимость

Приложение рассчитано на самокаты с модулем Nordic UART и протоколом A3 A4. Ключи 4BKNwi77 и w4S3Hqn5 являются известными пресетами из референсов, но не гарантируют доступ к конкретному самокату. Официальное приложение Urent 1.90.2 и 2.7.0 не содержит BLE-стека, поэтому ключ из него не извлекается. Проверка на реальном оборудовании описана в analysis/hardware_test.md.

Подробнее: docs/compatibility.md.

## Референсы

| APK | Пакет | Роль |
| --- | --- | --- |
| references/LostBot.apk | ru.lostbotapp.Free | транспорт, кадр, спам, телеметрия, API |
| references/FishBot.apk | FishBot.en.Milky | упаковщик, инжектор, отличия сборки |
| references/NineHack.apk | com.timeylies.ninehack.ru | минимальный BLE-клиент, ключи, команды |
| references/apks/urent-2.7.0-base.apk | ru.urentbike.app | официальный API, отсутствие BLE |
| references/apks/whoosh-4.0.0-base.apk | com.punicapp.whoosh | IoT BLE, UUID, команды, серверное шифрование |
| references/apks/yandex-go-5.98.2-base.apk | ru.yandex.taxi | Ninebot BLE, команды, провайдер-заглушка |
| Urent 1.90.2 | ru.urentbike.app | официальный API, отсутствие BLE, APK не хранится |

Хеши и подписи: references/README.md. Полный разбор: analysis/README.md. Отчёты по Whoosh и Yandex Go: analysis/report_whoosh.md и analysis/report_yandex_go.md.

## Ограничения

- Нет физического самоката и телефона с BLE в среде разработки. Реальная проверка команд и ключей не выполнена.
- BlueStacks и другие эмуляторы не поддерживают Bluetooth LE, поэтому проверка ограничена демо-режимом и unit-тестами.
- Неизвестно, какие ключи и команды принимает конкретная прошивка. Возможны отличия по годам и моделям.
- Прошивка самоката в APK отсутствует. Формат OTA восстановлен только по клиентскому коду.
- Release-сборка использует debug-подпись и не предназначена для публикации в магазине.
- Приложение не содержит сетевого клиента и не обходит серверные блокировки. Все команды выполняются по BLE.

## Безопасность и правовое использование

Приложение предназначено только для собственного самоката или устройства, на работу с которым получено явное разрешение владельца. Использование для доступа к чужому транспорту, обхода аренды или причинения вреда запрещено. Ключи хранятся в SharedPreferences в открытом виде, журнал может содержать идентификаторы устройства и RFID. Подробнее: docs/legal.md и docs/security.md.

## Лицензия

MIT. См. LICENSE.
