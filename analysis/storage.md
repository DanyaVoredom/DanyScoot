# Хранилища и данные

## 1. Сводка

| Приложение | SharedPreferences | Файлы | База | Сеть |
| --- | --- | --- | --- | --- |
| LostBot | lostbot_prefs, ble_saved_keys | baseline.prof | не найдена | токены и телеметрия |
| FishBot | те же | assets/oTMnsXXVnU, baseline.prof, baseline.profm | не найдена | C2 не подтверждён |
| NineHack | ninehack_settings | нет | нет | нет |
| Urent | множество SDK | кэш, файлы, push | Room-сервис в манифесте | да |
| DanyUrent | danyurent_prefs | нет | нет | нет |

## 2. LostBot

### lostbot_prefs

Ключи:

- show_all_scan_devices;
- show_scan_debug_log;
- auto_reconnect_enabled;
- auto_reconnect_max_attempts;
- auto_reconnect_delay_ms;
- auto_resume_spam;
- unlock_spam_interval_ms;
- transmission_device_type;
- transmission_update_key;
- use_demo;
- lb_access_token;
- lb_refresh_token;
- lb_entitlement_json;
- lb_entitlement_verified_at.

Значения по умолчанию:

- интервал спама 500 мс, clamp 2..3000;
- попытки 20, clamp 1..200;
- задержка 250 мс, clamp 50..10000;
- автовозобновление и автопереподключение зависят от сборки.

### Ключи BLE

Магазин ble_saved_keys и ble_selected_key создаётся, но в LostBot 1.2.1 рукопожатие берёт ключ из DI-провайдера C0141b(27), то есть константу w4S3Hqn5. Пользовательский выбор ключа в этой сборке не влияет на auth.

### Лицензия

Токены и entitlement хранятся в lostbot_prefs. При наличии lb_access_token и lb_refresh_token приложение считает вход выполненным. Это создаёт риск: подмена prefs может обойти отображение подписки, если проверка остаётся заглушкой.

## 3. FishBot

Наследует lostbot_prefs. Дополнительно:

- assets/oTMnsXXVnU, 25 714 байт;
- assets/dexopt/baseline.prof;
- assets/dexopt/baseline.profm;
- payload-плагин с AndroidManifest.xml, classes.dex, resources.arsc и layout.

Плагин содержит диалоги активации и регистрации, но не работает как shipped. Ассет может быть зашифрованным или сжатым пакетом.

## 4. NineHack

SharedPreferences: ninehack_settings.

Ключ:

- ble_key, по умолчанию 4BKNwi77.

Ключ хранится в открытом виде. Экран настроек собирается кодом и сохраняет trim-значение, пустая строка заменяется дефолтом.

Других данных нет: ни токенов, ни истории, ни файлов.

## 5. Urent

Официальное приложение использует множество SDK и хранилищ:

- push-токены и настройки push;
- аналитика Appsflyer, Yandex Metrica, MTS;
- платежи и NFC;
- кэш карты и поездок;
- Room-сервис для многопроцессной инвалидации.

Полный аудит хранилищ не проводился. Для задачи BLE достаточно факта: BLE-ключей и UUID в APK нет.

## 6. DanyUrent

SharedPreferences: danyurent_prefs.

Ключи:

- ble_key;
- unlock_spam_interval_ms;
- auto_reconnect_enabled;
- auto_reconnect_max_attempts;
- auto_reconnect_delay_ms;
- auto_resume_spam;
- show_all_scan_devices;
- show_scan_debug_log;
- use_demo;
- show_all_opcodes;
- theme;
- last_device_address;
- last_device_name.

Чувствительные данные:

- BLE-ключ в открытом виде;
- MAC-адрес последнего самоката;
- имя устройства.

Журнал хранится только в памяти, до 1000 строк. Файлов и базы нет.

## 7. Приватность

- LostBot и FishBot имеют сеть и токены.
- NineHack не имеет сети, но хранит ключ в открытом виде.
- DanyUrent не имеет сети, но хранит ключ и последнее устройство.
- Urent имеет широкий профиль данных и аналитику.

## 8. Рекомендации

- Не публиковать prefs и журналы без очистки.
- Использовать Keystore для ключа в публичной сборке.
- Отключить backup, что уже сделано в DanyUrent.
- Не хранить токены в открытом виде, если появится сеть.
- Добавить очистку журнала при выходе.
- Проверять экспортированные компоненты.
