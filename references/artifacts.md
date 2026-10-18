# Артефакты

## 1. APK в репозитории

| Файл | Пакет | Версия | Размер, байт | SHA-256 |
| --- | --- | --- | --- | --- |
| references/LostBot.apk | ru.lostbotapp.Free | 1.2.1 (4) | 2 536 378 | D1D8713820F3EBC480236E113020C0C547B94453D416EDE894D27A4205245882 |
| references/FishBot.apk | FishBot.en.Milky | 1.0.0 (1) | 3 895 634 | 7E05FEEABDB3E2FB119FB60BFE437E8433EEE5BC33D473BFC4FB23BB09B1FEFA |
| references/NineHack.apk | com.timeylies.ninehack.ru | 1.2 (1) | 11 497 879 | E21272AC4C1A74E1BFE5AF824EE2257BB15D0BB5A8C014F75014C6DE1931D497 |
| references/apks/urent-2.7.0-base.apk | ru.urentbike.app | 2.7.0 (2070) | 82 428 322 | 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 |
| references/apks/whoosh-4.0.0-base.apk | com.punicapp.whoosh | 4.0.0 (418060) | 54 707 264 | 912079486F06E84581C92055C3E87DAF8C3CB11F55B8F81F41BA907ADDB53C29 |
| references/apks/yandex-go-5.98.2-base.apk | ru.yandex.taxi | 5.98.2 (50128633) | 192 834 008 | 27A75FDF62C9AB601BDDD89C6559E75231F868AC3F08FA463AE7A85D8B5C1862 |

Три старых APK нужны для воспроизведения анализа LostBot, FishBot и NineHack. Не запускать их на основном устройстве.

Три официальных base APK получены через ADB с SM-A175F. Они хранятся через Git LFS. Split APK и native-библиотеки не входят в репозиторий.

## 2. Артефакты вне репозитория

| Артефакт | Версия | Размер, байт | SHA-256 |
| --- | --- | --- | --- |
| urent-1902.xapk | 1.90.2 (1902) | 95 646 408 | FA689FF0813A332F374D5A1FECDD4AF939F9780B1087FFDB3F75A7FEB50491E8 |
| urent-1902 base APK | 1.90.2 | 68 207 063 | B42D28566AD4BCC600BBD7CFDF2D5197B2B0DA24B1291D217E80AFC7C0CF6B68 |
| urent.xapk | 2.7.0 (2070) | 117 800 904 | 22CA0CF66B22EA13E67953ED9158B1DA5508B668A91A17BA373C69513063ADBF |
| whoosh-4.0.0-arm64.apk | 4.0.0 | 21 705 584 | не зафиксирован |
| yandex-go-5.98.2-arm64.apk | 5.98.2 | 116 139 945 | не зафиксирован |
| app-release.apk DanyUrent | 1.0.0 | не зафиксирован | не зафиксирован |

Urent 1.90.2 и 2.7.0 xapk не хранятся. Urent 2.7.0 base APK уже в references/apks. Split APK Whoosh и Yandex Go не хранятся, потому что BLE-код находится в dex base APK.

## 3. Подписи

| Артефакт | Результат | Сертификат |
| --- | --- | --- |
| LostBot.apk | OK | CN=np, SHA-256 492682F877607EE99DF2DDD2BD5953FD727BDF6E19D397DE9DBBAFD582BCAD75 |
| NineHack.apk | OK | тот же сертификат |
| FishBot.apk | DOES NOT VERIFY | v1 отсутствует, META-INF/MANIFEST.MF отсутствует |
| Urent 1.90.2 | OK | O=Urentbike, SHA-256 90E028E6D376C9E79F8D0332BFFA8667F8D41F6E5B7A114A4602EC68A0AAE2DD |
| Urent 2.7.0 | OK | O=Urentbike, тот же сертификат |
| Whoosh 4.0.0 | OK | CN=Whoosh.Bike, SHA-256 12607A1082202FEAFAA03AC22A3814F9EC770D731D87C667CE9FCA7B164F6BAE |
| Yandex Go 5.98.2 | OK | CN=OOO Yandex, SHA-256 ACA405DED8B25CB2E8C6DA69425D2B4307D087C1276FC06AD5942731CCC51DBA |

## 4. Проверка

Размер и хеш:

Get-ChildItem references\*.apk, references\apks\*.apk | Select-Object Name,Length

Get-FileHash references\apks\urent-2.7.0-base.apk -Algorithm SHA256

Get-FileHash references\apks\whoosh-4.0.0-base.apk -Algorithm SHA256

Get-FileHash references\apks\yandex-go-5.98.2-base.apk -Algorithm SHA256

Подпись:

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\LostBot.apk

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\NineHack.apk

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\FishBot.apk

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\urent-2.7.0-base.apk

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\whoosh-4.0.0-base.apk

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\yandex-go-5.98.2-base.apk

Пакет и версия:

C:\Android\Sdk\build-tools\36.1.0\aapt.exe dump badging references\apks\urent-2.7.0-base.apk

Git LFS:

git lfs ls-files

## 5. Дампы

| Каталог | Содержимое |
| --- | --- |
| analysis/LostBot | apktool-дамп LostBot |
| analysis/FishBot | apktool-дамп FishBot |
| analysis/NineHack | apktool-дамп NineHack |
| analysis/java/fishbot/sources | jadx-декомпиляция LostBot и FishBot |
| C:/Users/Dmode/AppData/Local/Temp/opencode/urent_dec | apktool-дамп Urent 2.7.0 |
| C:/Users/Dmode/AppData/Local/Temp/opencode/whoosh_dec | apktool-дамп Whoosh 4.0.0 |
| C:/Users/Dmode/AppData/Local/Temp/opencode/whoosh_jadx | jadx-декомпиляция Whoosh 4.0.0 |
| C:/Users/Dmode/AppData/Local/Temp/opencode/yandex_dec | apktool-дамп Yandex Go 5.98.2 |

Временные каталоги не коммитятся. Результаты перенесены в analysis/report_urent.md, analysis/report_whoosh.md и analysis/report_yandex_go.md.

## 6. Происхождение

- LostBot, FishBot, NineHack: сторонние APK, предоставленные для анализа.
- Urent 1.90.2: APKPure, https://apkpure.com/urent/ru.urentbike.app и https://apkpure.com/urent/ru.urentbike.app/versions.
- Urent 2.7.0, Whoosh 4.0.0, Yandex Go 5.98.2: ADB, устройство SM-A175F, Android 16, SDK 36.
- Публичное описание XDDbot: https://github.com/bitplugg/XDDbot и wiki Protocol.md.

Ссылки могут быть недоступны. Факты в analysis не зависят от доступности ссылок.

## 7. Целостность

При расхождении хеша:

1. Не использовать артефакт для выводов.
2. Найти исходный файл.
3. Сравнить размер и подпись.
4. Обновить documentation только после проверки.

Хеш в документации является единственным критерием соответствия артефакта.
