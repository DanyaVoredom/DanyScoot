# Разработка

## 1. Требования

- JDK 17.
- Android SDK с platform 36 и build-tools, которые использует AGP 8.11.1.
- Gradle 8.14.3 через wrapper в каталоге app.
- Windows PowerShell или cmd. Команды ниже приведены для PowerShell.

Проверка окружения:

```
java -version
cd app
.\gradlew.bat --version
```

## 2. Карта исходников

| Путь | Содержимое |
| --- | --- |
| app/app/src/main/java/ru/dany/scoot/App.kt | инициализация ScooterClient |
| AppTheme.kt | перечисление тем и выбор по prefs |
| MainActivity.kt | вкладки, разрешения, статус, badge |
| ble/Frame.kt | CRC8, CRC16, FrameCodec, hex, parseHex |
| ble/ScooterCodec.kt | команды и декодеры |
| ble/BleTransport.kt | сканер, GATT, MTU, очередь записи |
| ble/OpcodeCatalog.kt | каталог 54 опкодов |
| core/ScooterClient.kt | состояния, auth, спам, ответы, демо |
| core/Prefs.kt | SharedPreferences и значения по умолчанию |
| core/LogBus.kt | журнал и уровни |
| spam/SpamService.kt | foreground-сервис |
| ui/DevicesFragment.kt | скан и выбор устройства |
| ui/ControlFragment.kt | основные команды и телеметрия |
| ui/LogFragment.kt | журнал |
| ui/SettingsFragment.kt | настройки и темы |
| ui/ProvidersFragment.kt | операторы Whoosh и Yandex Go |
| res/layout | разметка экранов |
| res/values/strings.xml | все пользовательские строки |
| res/values/themes.xml | пять тем |

## 3. Соглашения

- Язык интерфейса и документации: русский.
- Идентификаторы кода и имена опкодов: английский.
- Пакет: ru.dany.scoot. Не менять без полного переименования.
- Комментарии в коде не добавляются. Исключение: подавление предупреждений и объяснение неочевидных API.
- Форматирование: 4 пробела, максимальная длина строки не задана, но предпочтительно до 120 символов.
- Состояние UI читается через StateFlow. Не хранить копию состояния в Activity или Fragment.
- Новые команды добавлять в ScooterCodec и OpcodeCatalog, затем подключать в ScooterClient.
- Все пользовательские строки добавлять в strings.xml, не хардкодить в layout.
- Для новых настроек использовать Prefs с явным значением по умолчанию и ограничением диапазона.
- Для новых BLE-операций учитывать таймауты и очистку очереди.

## 4. Добавление опкода

Порядок:

1. Найти подтверждение в analysis/protocol_full.md, analysis/opcode_builders.md или references/opcodes.csv.
2. Добавить константу в ScooterCodec, если опкод используется в коде.
3. Добавить OpcodeInfo в OpcodeCatalog с направлением и описанием.
4. Если нужен сборщик, добавить метод, который принимает sessionKey и payload.
5. Если нужен декодер, добавить функцию разбора с проверкой длины.
6. Подключить обработку в ScooterClient.handleFrame.
7. Добавить unit-тест на сборку и, если возможно, на разбор.
8. Обновить references/opcodes.csv и analysis/opcode_reference.md.
9. Добавить тест-вектор в references/frames.json, если кадр детерминирован.
10. Запустить testDebugUnitTest и lintDebug.

Не добавлять команду, если неизвестен payload и направление. Для неизвестных данных использовать консоль ручных команд, а не кнопку с догадкой.

## 5. Добавление команды в UI

1. Добавить кнопку в fragment_control.xml с осмысленным id.
2. Добавить строку в strings.xml.
3. Подключить обработчик в ControlFragment.onViewCreated.
4. Вызывать метод ScooterClient, а не BleTransport напрямую.
5. Включить кнопку в updateStatus по isReady.
6. Проверить поведение в демо-режиме.
7. Обновить docs/ui.md и docs/protocol-implementation.md.

## 6. Добавление настройки

1. Добавить поле в Prefs с ключом и значением по умолчанию.
2. Добавить ограничение диапазона в setter.
3. Добавить элемент в fragment_settings.xml.
4. Загрузить значение в SettingsFragment.loadPrefs.
5. Сохранить значение в SettingsFragment.save.
6. Если настройка влияет на активное соединение, обработать изменение сразу или задокументировать, что нужен перезапуск.
7. Обновить docs/data-model.md и docs/ui.md.

## 7. Логирование

LogBus хранит кольцевой буфер в памяти. Уровни:

- TX: отправленные кадры.
- RX: принятые кадры и служебные ответы.
- INFO: обычные события.
- OK: успешные операции.
- WARN: отклонённые команды и восстановимые ошибки.
- ERR: ошибки, требующие внимания.

Не логировать полный ключ в релизной сборке. Сейчас ключ печатается при авторизации и в настройках. Это осознанное решение для отладки, но оно повышает риск утечки через скриншот или logcat. Перед публичной сборкой следует убрать или маскировать ключ.

## 8. Тесты

Unit-тесты находятся в app/app/src/test/java/ru/dany/scoot/ble. Они не требуют Android и проверяют:

- CRC8 и CRC16 на контрольных значениях;
- сборку и разбор кадра;
- XOR с неразделённым rand;
- поиск кадров в потоке и восстановление после мусора;
- hex и parseHex;
- команды key, unlock, lock, eject, find, set;
- спам-кадр с временной меткой;
- декодеры vehicle, IoT, realtime, RFID.

Тесты не покрывают Android BLE, разрешения, foreground-сервис и UI. Для них используется ручная матрица из docs/testing.md.

## 9. Отладка

Полезные команды:

```
adb logcat -s BLE AUTH SPAM SCOOTER FW INFO
adb shell dumpsys bluetooth_manager
adb shell settings get global bluetooth_on
```

Для HCI snoop:

1. Включить Bluetooth HCI snoop log в параметрах разработчика.
2. Выполнить сценарий.
3. Снять bugreport или файл btsnoop_hci.log.
4. Открыть в Wireshark с профилем Bluetooth HCI.

Подробнее: analysis/hardware_test.md.

## 10. Стиль изменений

- Одна задача - один логический блок изменений.
- Не смешивать переименование, рефакторинг и новую функцию в одном коммите.
- Сообщения коммитов: fix:, feat:, doc:, chore:, нижний регистр, кратко.
- Перед коммитом проверить git status и diff.
- Не коммитить локальные файлы, ключи, дампы APK и результаты сборки.
