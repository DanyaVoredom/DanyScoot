# Индекс анализа

Все документы собраны по references/FishBot.apk, references/LostBot.apk, references/NineHack.apk и официальным ru.urentbike.app 1.90.2 и 2.7.0. Факты без ссылки на источник являются вычисленными тест-векторами.

| Файл | Содержимое |
| --- | --- |
| analysis/references.md | Главный индекс артефактов, хеши, подписи, матрица подтвержденных фактов, ключи, маршруты добычи |
| analysis/protocol_full.md | Полный протокол: GATT, кадр, CRC, 54 опкода, TX/RX, телеметрия, прошивка, спам, демо, аномалии |
| analysis/opcode_builders.md | Какие опкоды имеют подтвержденный сборщик, какие только каталог, какие восстановлены из декодера |
| analysis/rx_samples.md | Байтовые примеры ответов, разбор телеметрии, чанкинг, битые кадры |
| analysis/hardware_test.md | Проверка на реальном самокате, HCI snoop, nRF Connect, сниффер, UART, дамп, брутфорс |
| analysis/source_map.md | Карта файлов и строк для проверки каждого утверждения |
| analysis/report_lostbot.md | LostBot/FishBot: транспорт, auth, спам, прошивка, лицензирование, UI |
| analysis/report_ninehack.md | NineHack: минимальный клиент, ключи, команды, ошибки, UI |
| analysis/report_fishbot.md | FishBot: инжектор, payload, отличия от LostBot |
| analysis/report_urent.md | Официальный Urent 1.90.2 и 2.7.0: манифест, API, отсутствие BLE |
| references/README.md | Хеши APK, подписи, ссылки, команды проверки |
| references/opcodes.csv | 54 опкода в машиночитаемом виде |
| references/frames.json | 48 TX, 20 RX и 4 битых кадра |
| references/generate_frames.py | Генератор frames.json |
| references/verify_frames.py | Независимая проверка всех кадров и CRC |
| references/xddbot.md | Публичное описание XDDbot и расхождения с APK |

## Быстрый старт

1. Открыть analysis/references.md.
2. Открыть analysis/protocol_full.md.
3. Проверить конкретный опкод в references/opcodes.csv.
4. Взять кадр из references/frames.json.
5. Проверить на самокате по analysis/hardware_test.md.

## Проверка данных

    python references/generate_frames.py
    python references/verify_frames.py
    python -c "import csv; r=list(csv.DictReader(open('references/opcodes.csv',encoding='utf-8'))); print(len(r))"
    python -c "import json; d=json.load(open('references/frames.json',encoding='utf-8')); print(len(d['tx']),len(d['rx']),len(d['malformed']))"

Ожидаемый вывод: ok 72, затем 54 и 48 20 4.
