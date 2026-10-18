# Матрица разрешений

## 1. Сравнение

| Разрешение | LostBot | FishBot | NineHack | Urent | DanyScoot |
| --- | --- | --- | --- | --- | --- |
| INTERNET | да | да | нет | да | нет |
| ACCESS_NETWORK_STATE | да | да | нет | да | нет |
| BLUETOOTH | да | да | да | нет | да, до API 30 |
| BLUETOOTH_ADMIN | да | да | да | нет | да, до API 30 |
| BLUETOOTH_SCAN | да | да | да | нет | да |
| BLUETOOTH_CONNECT | да | да | да | нет | да |
| ACCESS_FINE_LOCATION | да | да | да | да | да, до API 30 |
| ACCESS_COARSE_LOCATION | нет | нет | да | да | да, до API 30 |
| ACCESS_BACKGROUND_LOCATION | нет | нет | да | нет | нет |
| POST_NOTIFICATIONS | да | да | нет | да | да |
| FOREGROUND_SERVICE | да | да | нет | да | да |
| FOREGROUND_SERVICE_CONNECTED_DEVICE | да | да | нет | нет | да |
| FOREGROUND_SERVICE_DATA_SYNC | нет | нет | нет | да | нет |
| VIBRATE | да | да | нет | да | нет |
| READ_PHONE_STATE | нет | да | нет | да | нет |
| READ_EXTERNAL_STORAGE | нет | да | нет | да | нет |
| WRITE_EXTERNAL_STORAGE | нет | да | нет | да | нет |
| MANAGE_EXTERNAL_STORAGE | нет | да | нет | нет | нет |
| REQUEST_INSTALL_PACKAGES | нет | да | нет | нет | нет |
| QUERY_ALL_PACKAGES | нет | да | нет | нет | нет |
| BIND_VPN_SERVICE | нет | да | нет | нет | нет |
| ACCESS_WIFI_STATE | нет | да | нет | да | нет |
| CHANGE_WIFI_STATE | нет | нет | нет | да | нет |
| CAMERA | нет | нет | нет | да | нет |
| NFC | нет | нет | нет | да | нет |
| READ_CONTACTS | нет | нет | нет | да | нет |
| RECEIVE_BOOT_COMPLETED | нет | нет | нет | да | нет |
| WAKE_LOCK | нет | нет | нет | да | нет |

## 2. Опасные и специальные

### FishBot

- REQUEST_INSTALL_PACKAGES: установка APK из приложения.
- BIND_VPN_SERVICE: VPN-сервис, в коде найден туннель Cloudflare/WARP.
- MANAGE_EXTERNAL_STORAGE: широкий доступ к файлам.
- QUERY_ALL_PACKAGES: видимость установленных приложений.
- READ_PHONE_STATE: доступ к состоянию телефона.
- usesCleartextTraffic=true: HTTP без TLS.

Эти разрешения не нужны для BLE-клиента и указывают на упаковщик или вредоносную нагрузку. Инжектор в shipped-сборке нерабочий, но набор разрешений остаётся опасным.

### Urent

Официальное приложение запрашивает камеру, NFC, контакты, чтение телефона, хранилище, push и аналитику. Это объясняется картами, оплатой, сканированием и поддержкой. BLE-разрешений нет, хотя в манифесте есть android.hardware.bluetooth_le required=true.

### NineHack

Запрашивает ACCESS_BACKGROUND_LOCATION, хотя сканирование выполняется на экране. Это избыточно. Нет INTERNET, поэтому утечка возможна только через BLE или файлы.

### LostBot

Запрашивает INTERNET и сеть для лицензирования и телеметрии. Ключ BLE хранится локально, но приложение имеет сетевой API.

### DanyScoot

Минимальный набор:

- BLE-разрешения по версиям Android;
- foreground service;
- уведомления.

Нет сети, файлов, камеры, контактов и установки APK.

## 3. Разрешения по API

| API | DanyScoot | Примечание |
| --- | --- | --- |
| 23..30 | BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION | геолокация обязательна для скана |
| 31..32 | BLUETOOTH_SCAN neverForLocation, BLUETOOTH_CONNECT | геолокация не нужна |
| 33..35 | плюс POST_NOTIFICATIONS | уведомление foreground-сервиса |

## 4. Экспортированные компоненты

| Приложение | Компонент | exported | Защита |
| --- | --- | --- | --- |
| LostBot | MainActivity | true | нет |
| LostBot | SpamService | false | нет |
| FishBot | MainActivity | true | нет |
| FishBot | SpamService | false | нет |
| NineHack | mainActivity | true | нет |
| NineHack | остальные activity | false | нет |
| Urent | SingleActivity | true | нет |
| Urent | платёжные activity | true | внешние SDK |
| Urent | push receiver | true | permission |
| DanyScoot | MainActivity | true | launcher |
| DanyScoot | SpamService | false | нет |

DanyScoot не экспортирует сервис спама и не принимает внешние intent-ы, кроме запуска MainActivity.

## 5. Практические выводы

- FishBot не следует устанавливать на основное устройство.
- Urent имеет широкий набор разрешений, но не BLE.
- LostBot и NineHack ближе к задаче, но LostBot имеет сеть.
- DanyScoot сокращает поверхность атаки до BLE и уведомлений.
- Отсутствие INTERNET не защищает от перехвата BLE, но исключает удалённую утечку.
