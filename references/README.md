# Референсы и данные

Каталог содержит шесть APK и машиночитаемые данные по BLE-протоколам Urent, Whoosh и Yandex Go. Официальные APK лежат в references/apks и хранятся через Git LFS. Старые исследовательские APK лежат в корне references.

## 1. APK

| Файл | Пакет | Версия | Размер, байт | SHA-256 |
| --- | --- | --- | --- | --- |
| references/LostBot.apk | ru.lostbotapp.Free | 1.2.1 (4) | 2 536 378 | D1D8713820F3EBC480236E113020C0C547B94453D416EDE894D27A4205245882 |
| references/FishBot.apk | FishBot.en.Milky | 1.0.0 (1) | 3 895 634 | 7E05FEEABDB3E2FB119FB60BFE437E8433EEE5BC33D473BFC4FB23BB09B1FEFA |
| references/NineHack.apk | com.timeylies.ninehack.ru | 1.2 (1) | 11 497 879 | E21272AC4C1A74E1BFE5AF824EE2257BB15D0BB5A8C014F75014C6DE1931D497 |
| references/apks/urent-2.7.0-base.apk | ru.urentbike.app | 2.7.0 (2070) | 82 428 322 | 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 |
| references/apks/whoosh-4.0.0-base.apk | com.punicapp.whoosh | 4.0.0 (418060) | 54 707 264 | 912079486F06E84581C92055C3E87DAF8C3CB11F55B8F81F41BA907ADDB53C29 |
| references/apks/yandex-go-5.98.2-base.apk | ru.yandex.taxi | 5.98.2 (50128633) | 192 834 008 | 27A75FDF62C9AB601BDDD89C6559E75231F868AC3F08FA463AE7A85D8B5C1862 |

Полный инвентарь, подписи и происхождение: references/apks.csv и references/apks/README.md.

Официальные Urent 1.90.2 base APK и сборка DanyScoot в репозитории не хранятся. Их хеши есть в apks.csv.

## 2. Официальные приложения

| Приложение | BLE | Факт |
| --- | --- | --- |
| Urent 2.7.0 | нет | Нет BLUETOOTH-разрешений и классов android.bluetooth. Есть только uses-feature bluetooth_le. BLE-протокол Urent восстановлен по LostBot и NineHack. |
| Whoosh 4.0.0 | да | IoT-профиль, строковые команды, ключ команды и шифрование через backend. UUID клиента: 1c18, 572a, 3d2a, 9f2a. |
| Yandex Go 5.98.2 | да | Ninebot-протокол, команды AUTH, LOCK, UNLOCK, SOUND, CABLE_UNLOCK, UNLOCK_BATTERY_COVER. Провайдер UUID возвращает null, статических ключей нет. |

Отчёты: analysis/report_whoosh.md и analysis/report_yandex_go.md.

Для Urent официальное приложение не содержит BLE-стека, поэтому использовать его как источник UUID и ключей нельзя.

## 3. Машиночитаемые файлы

| Файл | Содержимое |
| --- | --- |
| apks.csv | инвентарь APK: пакет, версия, SDK, размер, SHA-256, подпись, источник |
| manifests.json | манифесты восьми приложений: application, permissions, components, features, queries |
| permissions.csv | 226 строк разрешений с категорией и риском |
| components.csv | 497 компонентов: activity, service, receiver, provider |
| opcodes.csv | 54 опкода Urent: код, имя, направление, payload, источник, декодер |
| opcodes.json | те же опкоды в JSON |
| builders.csv | 55 сборщиков payload Urent |
| rx_fields.csv | 72 поля разбора ответов Urent |
| frames.json | 48 TX, 20 RX, 4 битых кадра Urent |
| uuids.csv | 16 UUID и ролей для Urent, Whoosh, Yandex Go |
| ble_map.csv | карта BLE-профилей: сервис, write, notify, read, auth, encryption |
| ble_commands.csv | 7 команд Whoosh и Yandex Go: сборщик, структура payload, источник |
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
| analysis/report_whoosh.md | Whoosh 4.0.0: BLE, UUID, команды, шифрование |
| analysis/report_yandex_go.md | Yandex Go 5.98.2: Ninebot, провайдер, команды |
| analysis/protocol_full.md | полный протокол Urent |
| analysis/opcode_builders.md | матрица сборщиков Urent |

## 5. Происхождение

Официальные APK получены через ADB с устройства SM-A175F, Android 16, SDK 36.

| Пакет | Версия |
| --- | --- |
| ru.urentbike.app | 2.7.0 |
| com.punicapp.whoosh | 4.0.0 |
| ru.yandex.taxi | 5.98.2 |

Декодирование:

| Инструмент | Версия | Результат |
| --- | --- | --- |
| apktool | 3.0.3 | AndroidManifest.xml, resources, smali |
| jadx | 1.5.6 | Java-исходники Whoosh |
| apksigner | 36.1.0 | сертификаты и проверка подписи |

Дампы:

| Каталог | Приложение |
| --- | --- |
| urent_dec | Urent 2.7.0 |
| whoosh_dec | Whoosh 4.0.0 |
| whoosh_jadx | Whoosh 4.0.0, Java |
| yandex_dec | Yandex Go 5.98.2 |

Дампы находятся в C:/Users/Dmode/AppData/Local/Temp/opencode и не входят в репозиторий. В репозитории фиксируются факты, источники и хеши.

## 6. Проверка

python references\verify_frames.py

python references\validate_all.py

Ожидаемый результат validate_all.py:

| Счётчик | Значение |
| --- | --- |
| apps | 8 |
| apk | 8 |
| permissions | 226 |
| components | 497 |
| opcodes | 54 |
| uuids | 16 |
| ble_map | 3 |
| ble_commands | 7 |
| Итог | ok |

Проверка APK:

Get-FileHash references\apks\urent-2.7.0-base.apk -Algorithm SHA256

Get-FileHash references\apks\whoosh-4.0.0-base.apk -Algorithm SHA256

Get-FileHash references\apks\yandex-go-5.98.2-base.apk -Algorithm SHA256

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\urent-2.7.0-base.apk

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\whoosh-4.0.0-base.apk

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\yandex-go-5.98.2-base.apk

## 7. Ограничения

- Urent BLE не проверен на реальном самокате.
- Whoosh команды и ключ формируются на сервере, алгоритм в APK отсутствует.
- Yandex Go провайдер UUID возвращает null, рабочий BLE-путь в сборке не активируется.
- Прошивка и OTA не проверены.
- APK являются собственностью правообладателей и хранятся только для анализа совместимости.
- Split APK и native-библиотеки не входят в references/apks.
