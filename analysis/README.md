# Индекс анализа

Все документы собраны по references/FishBot.apk, references/LostBot.apk, references/NineHack.apk и официальным ru.urentbike.app 1.90.2 и 2.7.0. Факты без ссылки на источник являются вычисленными тест-векторами.

## 1. Основные документы

| Файл | Содержимое |
| --- | --- |
| references.md | главный индекс артефактов, хеши, подписи, матрица подтверждённых фактов, ключи, маршруты добычи |
| protocol_full.md | полный протокол: GATT, кадр, CRC, 54 опкода, TX/RX, телеметрия, прошивка, спам, демо, аномалии |
| opcode_builders.md | какие опкоды имеют подтверждённый сборщик, какие только каталог, какие восстановлены из декодера |
| opcode_reference.md | полная таблица 54 опкодов с направлением, payload, поддержкой и уверенностью |
| rx_samples.md | байтовые примеры ответов, разбор телеметрии, чанкинг, битые кадры |
| telemetry.md | поля 0x31, 0x60, 0x63, 0x85, 0x86, единицы и ограничения |
| firmware.md | чтение конфига, OTA 0xFA/0xFB/0xFC, CRC16, риски |
| errors.md | 0x10, статусы авторизации, команд, GATT и приложения |
| hardware_test.md | проверка на реальном самокате, HCI snoop, nRF Connect, сниффер, UART, дамп, брутфорс |
| method.md | инструменты, порядок анализа, уровни уверенности, этика |
| test_vectors.md | схема frames.json, примеры, проверка, ограничения |
| limitations.md | неизвестные, blockers, что нельзя утверждать |
| source_map.md | карта файлов и строк для проверки каждого утверждения |
| app_inventory.md | пакеты, версии, SDK, компоненты и атрибуты приложений |
| permissions.md | матрица разрешений и опасные наборы |
| network.md | endpoint-ы LostBot, Urent, FishBot, отсутствие сети у NineHack |
| storage.md | SharedPreferences, ключи, файлы, токены, приватность |
| comparison.md | сравнение функций, команд и архитектуры |
| obfuscation.md | R8, StringFog, упаковщики, отладочные артефакты |
| ui_screens.md | экраны LostBot, FishBot, NineHack, Urent, DanyUrent |
| report_lostbot.md | LostBot/FishBot: транспорт, auth, спам, прошивка, лицензирование, UI |
| report_ninehack.md | NineHack: минимальный клиент, ключи, команды, ошибки, UI |
| report_fishbot.md | FishBot: инжектор, payload, отличия от LostBot |
| report_urent.md | официальный Urent 1.90.2 и 2.7.0: манифест, API, отсутствие BLE |

## 2. Данные

| Файл | Содержимое |
| --- | --- |
| references/opcodes.csv | 54 опкода в машиночитаемом виде |
| references/frames.json | 48 TX, 20 RX и 4 битых кадра |
| references/generate_frames.py | генератор frames.json |
| references/verify_frames.py | независимая проверка всех кадров и CRC |
| references/artifacts.md | хеши, подписи, происхождение, проверка |
| references/schema.md | схемы CSV и JSON |
| references/xddbot.md | публичное описание XDDbot и расхождения с APK |
| references/README.md | индекс данных и ограничения |

## 3. Быстрый старт

1. Открыть analysis/references.md.
2. Открыть analysis/protocol_full.md.
3. Проверить конкретный опкод в analysis/opcode_reference.md или references/opcodes.csv.
4. Взять кадр из references/frames.json.
5. Проверить на самокате по analysis/hardware_test.md.

## 4. Проверка данных

```
python references/generate_frames.py
python references/verify_frames.py
python -c "import csv; r=list(csv.DictReader(open('references/opcodes.csv',encoding='utf-8'))); print(len(r))"
python -c "import json; d=json.load(open('references/frames.json',encoding='utf-8')); print(len(d['tx']),len(d['rx']),len(d['malformed']))"
```

Ожидаемый вывод: ok 72, затем 54 и 48 20 4.

## 5. Уровни уверенности

- confirmed: сборщик, декодер или манифест в APK.
- inferred: payload восстановлен из декодера, но сборщик не найден.
- catalog: известно только имя опкода.

Подробнее: docs/README.md и analysis/method.md.

## 6. Главные выводы

- Официальное Urent 1.90.2 и 2.7.0 не содержит BLE-стека.
- LostBot и NineHack используют Nordic UART, A3 A4, CRC8 и XOR.
- Ключи 4BKNwi77 и w4S3Hqn5 присутствуют в референсах.
- 0x05 имеет две формы payload.
- 0x10 содержит отклонённую команду и причину.
- 0xFA, 0xFB и 0xFC относятся к прошивке.
- FishBot содержит упаковщик и опасные разрешения, но инжектор нерабочий.
- DanyUrent не имеет сети и использует минимальный набор разрешений.

## 7. Ограничения

- Нет hardware-теста.
- Ключи не гарантированы.
- Прошивка не проверена.
- OTA не реализован.
- FishBot C2 не подтверждён.
- Официальный API не документирован.

Подробнее: analysis/limitations.md.
