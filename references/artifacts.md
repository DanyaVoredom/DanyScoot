# Артефакты

## 1. APK в репозитории

| Файл | Пакет | Версия | Размер, байт | SHA-256 |
| --- | --- | --- | --- | --- |
| FishBot.apk | FishBot.en.Milky | 1.0.0 (1) | 3 895 634 | 7E05FEEABDB3E2FB119FB60BFE437E8433EEE5BC33D473BFC4FB23BB09B1FEFA |
| LostBot.apk | ru.lostbotapp.Free | 1.2.1 (4) | 2 536 378 | D1D8713820F3EBC480236E113020C0C547B94453D416EDE894D27A4205245882 |
| NineHack.apk | com.timeylies.ninehack.ru | 1.2 (1) | 11 497 879 | E21272AC4C1A74E1BFE5AF824EE2257BB15D0BB5A8C014F75014C6DE1931D497 |

Эти файлы нужны для воспроизведения анализа. Не запускать их на основном устройстве.

## 2. Официальные Urent

| Артефакт | Версия | Размер, байт | SHA-256 |
| --- | --- | --- | --- |
| urent-1902.xapk | 1.90.2 (1902) | 95 646 408 | FA689FF0813A332F374D5A1FECDD4AF939F9780B1087FFDB3F75A7FEB50491E8 |
| urent-1902 base APK | 1.90.2 | 68 207 063 | B42D28566AD4BCC600BBD7CFDF2D5197B2B0DA24B1291D217E80AFC7C0CF6B68 |
| urent.xapk | 2.7.0 (2070) | 117 800 904 | 22CA0CF66B22EA13E67953ED9158B1DA5508B668A91A17BA373C69513063ADBF |
| urent 2.7.0 base APK | 2.7.0 | 82 428 322 | 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 |

Официальные APK в репозиторий не добавлены. Хеши зафиксированы для проверки источников.

## 3. Подписи

| Артефакт | Результат | Сертификат |
| --- | --- | --- |
| LostBot.apk | OK | CN=np, SHA-256 492682F877607EE99DF2DDD2BD5953FD727BDF6E19D397DE9DBBAFD582BCAD75 |
| NineHack.apk | OK | тот же сертификат |
| FishBot.apk | DOES NOT VERIFY | v1 отсутствует |
| Urent 1.90.2 | OK | O=Urentbike, SHA-256 90E028E6D376C9E79F8D0332BFFA8667F8D41F6E5B7A114A4602EC68A0AAE2DD |
| Urent 2.7.0 | OK | тот же сертификат Urentbike |

## 4. Проверка

Размер и хеш:

```
Get-ChildItem references\*.apk | Select-Object Name,Length
Get-FileHash references\LostBot.apk -Algorithm SHA256
```

Подпись:

```
apksigner verify --print-certs references\LostBot.apk
apksigner verify --print-certs references\NineHack.apk
apksigner verify --print-certs references\FishBot.apk
```

Пакет и версия:

```
aapt dump badging references\LostBot.apk
```

## 5. Дампы

| Каталог | Содержимое |
| --- | --- |
| analysis/LostBot | apktool-дамп LostBot |
| analysis/FishBot | apktool-дамп FishBot |
| analysis/NineHack | apktool-дамп NineHack |
| analysis/java/fishbot/sources | jadx-декомпиляция LostBot и FishBot |

Официальные Urent разбирались во временных каталогах. Результаты перенесены в analysis/report_urent.md. Временные каталоги не коммитятся.

## 6. Происхождение

- LostBot, FishBot, NineHack: сторонние APK, предоставленные для анализа.
- Urent: APKPure, https://apkpure.com/urent/ru.urentbike.app и https://apkpure.com/urent/ru.urentbike.app/versions.
- Публичное описание XDDbot: https://github.com/bitplugg/XDDbot и wiki Protocol.md.

Ссылки могут быть недоступны. Факты в analysis не зависят от доступности ссылок.

## 7. Целостность

При расхождении хеша:

1. Не использовать артефакт для выводов.
2. Найти исходный файл.
3. Сравнить размер и подпись.
4. Обновить documentation только после проверки.

Хеш в документации является единственным критерием соответствия артефакта.
