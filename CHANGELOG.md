# Changelog

Формат: major.minor.patch. Даты в ISO 8601.

## 1.1.0 - 2026-10-18

### Изменено

- Переименование в DanyScoot, пакет ru.dany.scoot.
- Файл настроек danyscoot_prefs с переносом из danyurent_prefs в пределах одного пакета.
- Версия 1.1.0 (2).

## 1.0.0 - 2026-10-18

### Добавлено

- Сканирование BLE, подключение по Nordic UART, MTU 247 и очередь записи.
- Рукопожатие 0x01 с ключом и сессионным ключом.
- Команды 0x05, 0x15, 0x61, 0x81, 0xA2, 0x54, 0x82, 0x91, 0x92, 0xC0, 0xFA.
- Телеметрия 0x31, 0x60, 0x63, 0x85 и 0x86.
- Спам разблокировки с временной меткой и foreground-сервисом.
- Консоль опкодов с каталогом из 54 команд.
- Демо-режим без Bluetooth.
- Пять тем: black, white, light, dark, amoled.
- Документация docs, analysis и references.
- Машиночитаемые opcodes.csv и frames.json.
- Проверка кадров verify_frames.py.
- Официальные base APK Urent 2.7.0, Whoosh 4.0.0 и Yandex Go 5.98.2 в references/apks через Git LFS.
- Манифесты, разрешения и компоненты восьми приложений в manifests.json, permissions.csv и components.csv.
- Карты UUID и BLE-команд в uuids.csv, ble_map.csv и ble_commands.csv.
- Проверка references/validate_all.py.
- Отчёты report_whoosh.md и report_yandex_go.md.

### Исправлено

- sessionKey 0 больше не считается ошибкой.
- 0x10 разбирается как отклонённая команда и причина, а не как CRC.
- Очередь записи проверяет все чанки нового кадра и не переполняется.
- parseHex отклоняет не-ASCII цифры.
- Кадр GSM_AUTO_DETECT использует 0x53 вместо 0x83.

### Ограничения

- Нет hardware-теста.
- Release использует debug-подпись.
- Ключи 4BKNwi77 и w4S3Hqn5 не гарантированы.
- OTA не реализован.

## 0.1.0

- Начальная переработка референсов LostBot, FishBot и NineHack.
- Переименование в DanyUrent, пакет ru.dany.urent.
- Базовый BLE-клиент и экран управления.
