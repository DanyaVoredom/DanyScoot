# Сетевые артефакты

## 1. Сводка

| Приложение | Сеть | Основной хост | Назначение |
| --- | --- | --- | --- |
| LostBot | да | https://lostbotapp.ru | лицензия, телеметрия, активация |
| FishBot | да, но C2 не подтверждён | нет рабочего endpoint | упаковщик, туннель, нерабочий инжектор |
| NineHack | нет | нет | только BLE |
| Urent 1.90.2 | да | service.urentbike.ru | аренда, карта, оплата, push |
| Urent 2.7.0 | да | service.urentbike.ru | то же |
| DanyUrent | нет | нет | только BLE |

## 2. LostBot

База: https://lostbotapp.ru. Клиент: OkHttp, JSON, Authorization: Bearer.

| Метод | Путь | Назначение |
| --- | --- | --- |
| POST | /me/telemetry/events | пакет телеметрии |
| GET | /me/entitlement | состояние подписки |
| POST | /me/redeem | активация кода |
| POST | /auth/refresh | обновление токена |

Заголовки и поля:

- Authorization: Bearer <token>;
- Content-Type: application/json;
- refresh_token;
- device_id;
- detail в ответе об ошибке.

Токены и состояние хранятся в lostbot_prefs:

- lb_access_token;
- lb_refresh_token;
- lb_entitlement_json;
- lb_entitlement_verified_at.

Телеметрия вычитывается из очереди порциями до 200 событий. В проанализированной сборке обработчик лицензии является заглушкой, поэтому телеметрия не влияет на работу. Это не отменяет наличия сетевого клиента и разрешений.

## 3. FishBot

FishBot является пропатченной сборкой LostBot с упаковщиком. В манифесте есть INTERNET, ACCESS_NETWORK_STATE, VPN, установка APK и доступ ко всем пакетам.

В коде найдены:

- строки Cloudflare/WARP: fastTestCloudflare, socks5 auth failed, 200 Connection established, CONNECT OK, doDirect, doProxy;
- адреса 104.18.115.242, 162.159.38.227 и IPv6 2606:4700:3110::;
- com.cloudinject.feature.model.RemoteShareInfo с полями title, message, neutral, themeColor, showType, shareContent.

Это признаки туннеля и удалённого управления, но рабочий C2-endpoint не подтверждён. Инжектор com.px/com.cloudinject в shipped-сборке нерабочий: компоненты не объявлены, полезная нагрузка отсутствует, android.support.v7 нет.

## 4. NineHack

Сетевых разрешений нет. В манифесте отсутствует INTERNET. Приложение работает только с BLE.

Это делает NineHack полезным источником протокола: меньше посторонней логики, но код собран с debuggable=true и содержит отладочные библиотеки Crashlytics.

## 5. Urent 1.90.2 и 2.7.0

Хост приложения: service.urentbike.ru. Авторизация: Authorization: Bearer JWT.

| Метод | Путь | Назначение |
| --- | --- | --- |
| POST | /api/v1/connect/token | токен, SMS или MTS |
| POST | /api/v1/scooters/openlock/{id} | серверная разблокировка |
| POST | /api/v1/scooters/closelock/{id} | серверная блокировка |
| GET | /api/v3/transports/ | карточка самоката |
| GET | /api/v6/transports | список самокатов на карте |
| GET | /api/v6/transports/polygon | список в полигоне |

Параметр useBluetooth в приложении равен false. Это константа, а не настройка. Состояние AvailableViaBluetooth приходит с сервера, но BLE-ветка не используется.

В неофициальном OpenAPI встречается сервер https://app.urentbike.ru/gatewayclient/api, но он не совпадает с хостом APK. Приоритет у APK. Документ не является официальным и не подтверждает подпись ur-request-data.

## 6. DanyUrent

Сетевых разрешений и кода нет. Приложение не отправляет ключи, телеметрию и журнал. Это важно для модели угроз: утечка возможна только через BLE, файловую систему или демонстрацию экрана.

## 7. Риски

- LostBot и FishBot имеют сеть и широкие разрешения.
- FishBot содержит VPN и установку APK.
- Urent использует HTTPS и сетевую авторизацию.
- NineHack и DanyUrent не имеют сети.
- Отсутствие сети не защищает от перехвата BLE.
- Наличие сети не означает, что данные действительно отправляются: нужна проверка трафика.

## 8. Как проверить

Для перехвата HTTPS на своём устройстве:

1. Установить прокси-сертификат.
2. Настроить Wi-Fi proxy.
3. Запустить приложение.
4. Смотреть запросы в mitmproxy или Charles.
5. Учесть certificate pinning и сетевой security config.

Для BLE использовать HCI snoop log, а не сетевой прокси.
