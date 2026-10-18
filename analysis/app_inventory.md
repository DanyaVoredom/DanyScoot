# Инвентарь приложений

## 1. Артефакты

| Приложение | Пакет | Версия | Размер, байт | SHA-256 |
| --- | --- | --- | --- | --- |
| LostBot | ru.lostbotapp.Free | 1.2.1 (4) | 2 536 378 | D1D8713820F3EBC480236E113020C0C547B94453D416EDE894D27A4205245882 |
| FishBot | FishBot.en.Milky | 1.0.0 (1) | 3 895 634 | 7E05FEEABDB3E2FB119FB60BFE437E8433EEE5BC33D473BFC4FB23BB09B1FEFA |
| NineHack | com.timeylies.ninehack.ru | 1.2 (1) | 11 497 879 | E21272AC4C1A74E1BFE5AF824EE2257BB15D0BB5A8C014F75014C6DE1931D497 |
| Urent 1.90.2 base | ru.urentbike.app | 1.90.2 (1902) | 68 207 063 | B42D28566AD4BCC600BBD7CFDF2D5197B2B0DA24B1291D217E80AFC7C0CF6B68 |
| Urent 2.7.0 base | ru.urentbike.app | 2.7.0 (2070) | 82 428 322 | 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 |
| DanyScoot debug | ru.dany.scoot | 1.1.0 (2) | зависит от сборки | вычисляется после сборки |

## 2. SDK и версии

| Приложение | minSdk | targetSdk | compileSdk | Источник |
| --- | --- | --- | --- | --- |
| LostBot | 4 | 42 | 35 | apktool.yml, AndroidManifest.xml |
| FishBot | 17 | 28 | 35 | apktool.yml, AndroidManifest.xml |
| NineHack | 3 | 49 | 34 | apktool.yml, AndroidManifest.xml |
| Urent 1.90.2 | 24 | 36 | 36 | AndroidManifest.xml |
| Urent 2.7.0 | 24 | 36 | 36 | AndroidManifest.xml |
| DanyScoot | 23 | 35 | 36 | app/app/build.gradle.kts |

Значения targetSdk 42 и 49 у LostBot и NineHack выглядят аномальными для публичных приложений и, вероятно, являются артефактом патча или apktool. Они не используются для выводов о совместимости.

## 3. Подписи

| Приложение | Результат apksigner | Сертификат |
| --- | --- | --- |
| LostBot | OK | CN=np, SHA-256 492682F877607EE99DF2DDD2BD5953FD727BDF6E19D397DE9DBBAFD582BCAD75 |
| NineHack | OK | тот же сертификат, что LostBot |
| FishBot | DOES NOT VERIFY | v1 отсутствует, META-INF/MANIFEST.MF нет |
| Urent 1.90.2 | OK | O=Urentbike, SHA-256 90E028E6D376C9E79F8D0332BFFA8667F8D41F6E5B7A114A4602EC68A0AAE2DD |
| Urent 2.7.0 | OK | тот же сертификат Urentbike |

Совпадение сертификатов LostBot и NineHack означает общую подпись сборки, а не общий исходный код. FishBot не проходит проверку, но пригоден для анализа кода.

## 4. Компоненты

| Приложение | Activity | Service | Receiver | Provider |
| --- | --- | --- | --- | --- |
| LostBot | 1 | 1 | 1 | 1 |
| FishBot | 1 | 1 | 1 | 1 |
| NineHack | 5 | 0 | 1 | 1 |
| Urent 1.90.2 | 21 | 24 | 19 | 14 |
| Urent 2.7.0 | 20 | 24 | 19 | 15 |
| DanyScoot | 1 | 1 | 0 | 0 |

Ключевые компоненты:

- LostBot: ru.lostbotapp.MainActivity, exported=true; ru.lostbotapp.service.SpamService, exported=false, foregroundServiceType=connectedDevice.
- FishBot: те же компоненты, но манифест пропатчен и содержит дополнительные разрешения.
- NineHack: mainActivity exported=true; controlActivity, scanActivity, bleSettingsActivity, permissionsActivity exported=false.
- Urent: SingleActivity exported=true; множество сервисов push, оплаты, аналитики и камеры.
- DanyScoot: MainActivity exported=true, SpamService exported=false, foregroundServiceType=connectedDevice.

## 5. Разрешения

### LostBot

- INTERNET, ACCESS_NETWORK_STATE;
- POST_NOTIFICATIONS, VIBRATE;
- FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE;
- BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION;
- BLUETOOTH_SCAN, BLUETOOTH_CONNECT;
- DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION.

### FishBot

Базовый набор LostBot плюс:

- REORDER_TASKS;
- WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE;
- READ_PHONE_STATE;
- BIND_VPN_SERVICE;
- REQUEST_INSTALL_PACKAGES;
- QUERY_ALL_PACKAGES;
- ACCESS_WIFI_STATE.

В манифесте есть дубли INTERNET, FOREGROUND_SERVICE и ACCESS_NETWORK_STATE. usesCleartextTraffic=true.

### NineHack

- BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION;
- ACCESS_BACKGROUND_LOCATION, ACCESS_COARSE_LOCATION;
- BLUETOOTH_SCAN, BLUETOOTH_CONNECT;
- DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION.

Нет INTERNET. Приложение работает только с BLE.

### Urent

- геолокация, камера, NFC, уведомления, foreground service data sync;
- INTERNET, network state, Wi-Fi state;
- чтение телефона, контактов, внешнего хранилища;
- push, аналитика, платежи, Android Auto, Appsflyer, Huawei, Yandex Metrica.

Официальное приложение существенно шире по разрешениям, но не содержит BLE-разрешений. Это согласуется с отсутствием BLE-стека.

### DanyScoot

- BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION до API 30;
- BLUETOOTH_SCAN с neverForLocation, BLUETOOTH_CONNECT;
- POST_NOTIFICATIONS;
- FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE.

Нет INTERNET, хранилища, камеры, контактов, VPN, установки APK и доступа ко всем пакетам.

## 6. Атрибуты application

| Приложение | allowBackup | cleartext | debuggable | networkSecurityConfig |
| --- | --- | --- | --- | --- |
| LostBot | false | не задан | нет | есть |
| FishBot | false | true | нет | есть |
| NineHack | true | не задан | true | нет |
| Urent 1.90.2 | false | false | нет | есть |
| Urent 2.7.0 | false | false | нет | есть |
| DanyScoot | false | не задан | debug только | нет |

NineHack собран с debuggable=true. Это упрощает анализ, но недопустимо для публичной сборки.

## 7. Выводы

- LostBot и NineHack используют одинаковую подпись, но разные архитектуры: LostBot имеет сеть и лицензирование, NineHack является минимальным BLE-клиентом.
- FishBot является пропатченной сборкой LostBot с нерабочим инжектором и опасным набором разрешений.
- Официальное Urent не содержит BLE-стека и не может быть источником ключа.
- DanyScoot имеет минимальный набор разрешений, необходимый для BLE и foreground-сервиса.
- DanyScoot не содержит сетевых разрешений, что отличает его от LostBot, FishBot и Urent.

## 8. Источники

- analysis/references.md;
- analysis/report_lostbot.md;
- analysis/report_fishbot.md;
- analysis/report_ninehack.md;
- analysis/report_urent.md;
- AndroidManifest.xml каждого приложения;
- references/README.md.
