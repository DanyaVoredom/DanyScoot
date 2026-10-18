# FishBot: материалы для передачи

## 1. Что передавать

Минимальный комплект:

| Путь | Содержимое | Размер |
| --- | --- | --- |
| references/FishBot.apk | исходный образец | 3 895 634 байт |
| analysis/FishBot | apktool-разбор, smali и ресурсы | 6 592 файла, 56 728 497 байт |
| analysis/payload | распакованный ассет плагина | 15 файлов, 46 910 байт |
| analysis/payload_apktool | apktool-разбор плагина | 7 файлов, 12 041 байт |
| analysis/java/fishbot | jadx-декомпиляция | 4 781 файл, 30 969 051 байт |
| analysis/report_fishbot.md | отчёт по реверсу | 11 700 байт |
| analysis/fishbot_handoff.md | этот индекс | - |

Дополнительно для сравнения:

| Путь | Зачем |
| --- | --- |
| references/LostBot.apk | база, с которой собран FishBot |
| analysis/LostBot | apktool-разбор LostBot |
| analysis/report_lostbot.md | отчёт по LostBot |
| analysis/java/ninehack | jadx NineHack, если нужен слой BLE |
| analysis/protocol_full.md | полный протокол LostBot и NineHack |
| references/apks.csv | хеши и метаданные образцов |
| references/artifacts.md | карта артефактов и проверок |

## 2. Идентификация образца

| Параметр | Значение |
| --- | --- |
| Файл | references/FishBot.apk |
| Пакет | FishBot.en.Milky |
| Версия | 1.0.0 (1) |
| minSdk / targetSdk | 17 / 28 |
| SHA-256 | 7E05FEEABDB3E2FB119FB60BFE437E8433EEE5BC33D473BFC4FB23BB09B1FEFA |
| Подпись | стоковый AOSP debug, CN=Android Debug |
| apksigner | не проверяется, отсутствует META-INF/MANIFEST.MF |

## 3. Главные выводы

FishBot это пересобранный LostBot с розовой темой, заменой лицензионных маркеров и встроенным упаковщиком com.px / com.cloudinject / com.sadfxg.fasg.

Прикладной слой LostBot не изменён по протоколу. Изменены 6 файлов: лицензионные строки, Base64-декодеры, палитра, удалён периодический опрос лицензии, переименован x2/f.

Упаковщик как shipped нерабочий. Он выполняет attachBaseContext, хукает PMS и подписи, но полезная нагрузка отсутствует: нет компонентов в манифесте, нет com.cloudinject.core и com.cloudinject.plugincore, нет launcher-активити плагина, нет android.support.v7.

Собственного C2 у упаковщика нет. Сетевой код находится в mirrorb и относится к туннелю Cloudflare/WARP.

Добавленные разрешения обслуживают упаковщик, а не функции самоката.

## 4. Индикаторы

| Тип | Значение |
| --- | --- |
| Упаковщик | com.sadfxg.fasg.App |
| Движок | cd, mirrorb, com.px |
| Плагин | com.cloudinject, org.lsposed.hiddenapibypass |
| APP_ID | 74008b4ea3a81cf40000242d50cf1fbf |
| PLUGIN_VERIFY | d0194472979a4086f01b1c48753c4b5d |
| INCREMENT_DATA | f77f26c0004a9e696205b8b44eed340b55940840bbe5783e |
| A | b054a54d5673d87956f28ef720a4cb44bc0f04d2dec07fee0778d303fc1efe1e53122b046d5870b2 |
| RS | oTMnsXXVnU |
| VERSION_CODE | 23 |
| VERSION_NAME | 2.3 |
| PLATFORM | 2 |
| API_VERSION | 1 |
| Выгружаемый файл | xz.apk |
| IP | 104.18.115.242, 162.159.38.227 |
| IPv6 | 2606:4700:3110:: |
| Сетевые маркеры | fastTestCloudflare, socks5 auth failed, 200 Connection established, CONNECT OK, doDirect, doProxy |
| Лицензионные маркеры | License, MilkyU_a_fficial, Cracked by Milky |

Лишние разрешения: REORDER_TASKS, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, READ_PHONE_STATE, BIND_VPN_SERVICE, REQUEST_INSTALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES, ACCESS_WIFI_STATE.

## 5. Что не подтверждено

Работоспособность плагина на устройстве не проверялась. Полезная нагрузка плагина отсутствует, поэтому проверить нечего.

Связь с внешним оператором не подтверждена. В cd нет java/net/URL, HttpURLConnection, okhttp3 и Socket.

Прикладной протокол FishBot не отличается от LostBot. Отдельного BLE-протокола FishBot нет.

## 6. Правила обращения

Образец не устанавливать на рабочий телефон. Хранить в архиве с паролем или в изолированной среде.

Для анализа использовать только статический разбор. При необходимости динамики применять отдельное устройство без аккаунтов и SIM.

## 7. Упаковка

Если нужен один архив:

    Compress-Archive -Path references\FishBot.apk,analysis\FishBot,analysis\payload,analysis\payload_apktool,analysis\java\fishbot,analysis\report_fishbot.md,analysis\fishbot_handoff.md -DestinationPath fishbot_handoff.zip

Пароль на архив стандартными средствами PowerShell не ставится. Для пароля использовать 7-Zip:

    7z a -p -mhe=on fishbot_handoff.7z references\FishBot.apk analysis\FishBot analysis\payload analysis\payload_apktool analysis\java\fishbot analysis\report_fishbot.md analysis\fishbot_handoff.md
