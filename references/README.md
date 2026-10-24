# Референсы и данные

Каталог содержит четыре APK и машиночитаемые данные по BLE-протоколам Urent. Официальный APK лежит в references/apks и хранится через Git LFS. Старые исследовательские APK лежат в корне references.

## 1. APK

| Файл | Пакет | Версия | Размер, байт | SHA-256 |
| --- | --- | --- | --- | --- |
| references/LostBot.apk | ru.lostbotapp.Free | 1.2.1 (4) | 2 536 378 | D1D8713820F3EBC480236E113020C0C547B94453D416EDE894D27A4205245882 |
| references/FishBot.apk | FishBot.en.Milky | 1.0.0 (1) | 3 895 634 | 7E05FEEABDB3E2FB119FB60BFE437E8433EEE5BC33D473BFC4FB23BB09B1FEFA |
| references/NineHack.apk | com.timeylies.ninehack.ru | 1.2 (1) | 11 497 879 | E21272AC4C1A74E1BFE5AF824EE2257BB15D0BB5A8C014F75014C6DE1931D497 |
| references/apks/urent-2.7.0-base.apk | ru.urentbike.app | 2.7.0 (2070) | 82 428 322 | 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 |

Полный инвентарь, подписи и происхождение: references/apks.csv и references/apks/README.md.

Официальные Urent 1.90.2 base APK и сборка DanyScoot в репозитории не хранятся. Их хеши есть в apks.csv.

## 2. Официальные приложения

| Приложение | BLE | Факт |
| --- | --- | --- |
| Urent 2.7.0 | нет | Нет BLUETOOTH-разрешений и классов android.bluetooth. Есть только uses-feature bluetooth_le. BLE-протокол Urent восстановлен по LostBot и NineHack. |

Для Urent официальное приложение не содержит BLE-стека, поэтому использовать его как источник UUID и ключей нельзя.

## 3. Машиночитаемые файлы

| Файл | Содержимое |
| --- | --- |
| apks.csv | инвентарь APK: пакет, версия, SDK, размер, SHA-256, подпись, источник |
| manifests.json | манифесты шести приложений: application, permissions, components, features, queries |
| permissions.csv | 118 строк разрешений с категорией и риском |
| components.csv | 185 компонентов: activity, service, receiver, provider |
| opcodes.csv | 54 опкода Urent: код, имя, направление, payload, источник, декодер |
| opcodes.json | те же опкоды в JSON |
| builders.csv | 55 сборщиков payload Urent |
| rx_fields.csv | 72 поля разбора ответов Urent |
| frames.json | 48 TX, 20 RX, 4 битых кадра Urent |
| uuids.csv | 4 UUID и роли для Urent |
| verify_frames.py | проверка кадров Urent |
| validate_all.py | проверка CSV, JSON, APK-хешей и кадров |

Схемы: references/schema.md.

## 4. Документы

| Файл | Содержимое |
| --- | --- |
| artifacts.md | артефакты, подписи, происхождение, проверка |
| schema.md | схемы всех CSV и JSON |
| analysis/report_urent.md | официальный Urent 1.90.2 и 2.7.0 |
| analysis/report_lostbot.md | LostBot: кадры, команды, API, C2 |
| analysis/report_fishbot.md | FishBot: упаковщик, инжектор, отличия |
| analysis/report_ninehack.md | NineHack: минимальный BLE-клиент, ключи, команды |
| analysis/protocol_full.md | полный протокол Urent |
| analysis/opcode_builders.md | матрица сборщиков Urent |

## 5. Происхождение

Официальные APK получены через ADB с устройства SM-A175F, Android 16, SDK 36.

| Пакет | Версия |
| --- | --- |
| ru.urentbike.app | 2.7.0 |

Декодирование:

| Инструмент | Версия | Результат |
| --- | --- | --- |
| apktool | 3.0.3 | AndroidManifest.xml, resources, smali |
| apksigner | 36.1.0 | сертификаты и проверка подписи |

Дампы:

| Каталог | Приложение |
| --- | --- |
| urent_dec | Urent 2.7.0 |

Дампы находятся в C:/Users/Dmode/AppData/Local/Temp/opencode и не входят в репозиторий. В репозитории фиксируются факты, источники и хеши.

## 6. Проверка

python references\verify_frames.py

python references\validate_all.py

Ожидаемый результат validate_all.py:

| Счётчик | Значение |
| --- | --- |
| apps | 6 |
| apk | 6 |
| permissions | 118 |
| components | 185 |
| opcodes | 54 |
| uuids | 4 |
| Итог | ok |

Проверка APK:

Get-FileHash references\apks\urent-2.7.0-base.apk -Algorithm SHA256

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\urent-2.7.0-base.apk

## 7. Ограничения

- Urent BLE не проверен на реальном самокате.
- Прошивка и OTA не проверены.
- APK являются собственностью правообладателей и хранятся только для анализа совместимости.
- Split APK и native-библиотеки не входят в references/apks.
