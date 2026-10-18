# Референсы

Каталог содержит три APK для анализа BLE-протокола. Официальные APK Urent в репозиторий не добавлены: они большие и не являются исходниками DanyUrent. Их хэши и результаты анализа зафиксированы в analysis/references.md и analysis/report_urent.md.

## 1. Файлы

| Файл | Пакет | Версия | Размер, байт | SHA-256 |
| --- | --- | --- | --- | --- |
| FishBot.apk | FishBot.en.Milky | 1.0.0 (1) | 3 895 634 | 7E05FEEABDB3E2FB119FB60BFE437E8433EEE5BC33D473BFC4FB23BB09B1FEFA |
| LostBot.apk | ru.lostbotapp.Free | 1.2.1 (4) | 2 536 378 | D1D8713820F3EBC480236E113020C0C547B94453D416EDE894D27A4205245882 |
| NineHack.apk | com.timeylies.ninehack.ru | 1.2 (1) | 11 497 879 | E21272AC4C1A74E1BFE5AF824EE2257BB15D0BB5A8C014F75014C6DE1931D497 |

## 2. Подписи

| Файл | Результат apksigner | Сертификат |
| --- | --- | --- |
| FishBot.apk | DOES NOT VERIFY, Missing META-INF/MANIFEST.MF | v1 отсутствует, проверка не проходит |
| LostBot.apk | OK | CN=np, OU=np, O=np, L=??, ST=??, C=??, SHA-256 492682F877607EE99DF2DDD2BD5953FD727BDF6E19D397DE9DBBAFD582BCAD75 |
| NineHack.apk | OK | тот же CN=np и SHA-256 492682F877607EE99DF2DDD2BD5953FD727BDF6E19D397DE9DBBAFD582BCAD75 |

Совпадение сертификатов LostBot и NineHack означает общую подпись сборки, а не общий исходный код. FishBot не проходит проверку, потому что его пересобрали или удалили v1-подпись; для анализа кода это не препятствует.

## 3. Официальные Urent

| Артефакт | Версия | Размер, байт | SHA-256 |
| --- | --- | --- | --- |
| urent-1902.xapk | 1.90.2 (1902) | 95 646 408 | FA689FF0813A332F374D5A1FECDD4AF939F9780B1087FFDB3F75A7FEB50491E8 |
| urent-1902 base APK | 1.90.2 | 68 207 063 | B42D28566AD4BCC600BBD7CFDF2D5197B2B0DA24B1291D217E80AFC7C0CF6B68 |
| urent.xapk | 2.7.0 (2070) | 117 800 904 | 22CA0CF66B22EA13E67953ED9158B1DA5508B668A91A17BA373C69513063ADBF |
| urent 2.7.0 base APK | 2.7.0 | 82 428 322 | 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 |

Источник: APKPure, https://apkpure.com/urent/ru.urentbike.app и https://apkpure.com/urent/ru.urentbike.app/versions. Обе версии подписаны O=Urentbike, SHA-256 90E028E6D376C9E79F8D0332BFFA8667F8D41F6E5B7A114A4602EC68A0AAE2DD. В репозиторий не добавлены.

## 4. Как получены дампы

Использованы apktool 3.0.3 и jadx 1.5.6. Дампы лежат в analysis/FishBot, analysis/LostBot, analysis/NineHack и analysis/java/fishbot/sources. Официальные Urent разбирались во временных каталогах; результаты перенесены в analysis/report_urent.md.

Проверка подписи:

    apksigner verify --print-certs <apk>

Проверка пакета и версии:

    aapt dump badging <apk>

Поиск BLE-следов в Urent:

    rg -a -n "android/bluetooth|BluetoothGatt|BluetoothAdapter|BluetoothLeScanner" <dump>/smali*
    rg -a -n "6e400001|6e400002|6e400003|4BKNwi77|w4S3Hqn5" <dump>/smali*

## 5. Что искать в дампах

| Что | Где в LostBot/FishBot | Где в NineHack |
| --- | --- | --- |
| UUID и GATT | z2/i.java, z2/i.smali | controlActivity.smali |
| Кадр и CRC8 | A2/f.java, A2/f.smali, A2/c.java | CommandCreator.smali, Crc8.smali |
| Рукопожатие | C2/F.java, C2/F.smali | controlActivity.smali |
| Ключ по умолчанию | z2/l.java, D2/C0141b.java | strings.xml, controlActivity.smali |
| Спам | C2/F.java, SpamService | нет |
| Прошивка и CRC16 | C2/F.java | нет |
| Телеметрия | A2/c.java | нет |
| Сохраненные ключи | C2/C0121b.java, SavedBleKey | ninehack_settings |

## 6. Машиночитаемые данные

| Файл | Содержимое |
| --- | --- |
| opcodes.csv | 54 опкода: код, имя, направление, payload, сборщик, декодер, поддержка DanyUrent, уверенность |
| frames.json | 48 TX, 20 RX, 4 битых кадра, CRC-константы |
| generate_frames.py | Генератор frames.json без внешних зависимостей |
| verify_frames.py | Независимый разбор всех кадров и проверка CRC |
| xddbot.md | Публичный XDDbot, совпадения и расхождения с APK |

Поле danyurent: yes означает отдельную кнопку или метод, decoder означает только разбор ответа, manual означает отправку через общий диалог опкодов. Поле confidence: confirmed означает сборщик или декодер в APK, inferred означает payload из декодера, catalog означает только имя из каталога.

Проверка:

    python references/generate_frames.py
    python references/verify_frames.py
    python -c "import csv; r=list(csv.DictReader(open('references/opcodes.csv',encoding='utf-8'))); print(len(r))"
    python -c "import json; d=json.load(open('references/frames.json',encoding='utf-8')); print(len(d['tx']),len(d['rx']),len(d['malformed']))"

Ожидаемый вывод: ok 72, затем 54 и 48 20 4.

## 7. Ограничения

- APK не содержат прошивку самоката. Все выводы о прошивке сделаны по клиентскому коду и тест-векторам.
- Известные ключи 4BKNwi77 и w4S3Hqn5 могут не подойти к конкретному самокату. Это проверяется только на hardware.
- Официальное Urent 1.90.2/2.7.0 не содержит BLE-стека и ключа. Это установленный факт, а не неудачный поиск.
- FishBot не проходит проверку подписи; его код пригоден для анализа, но не как доверенный артефакт.
- APK могут содержать вредоносный или нежелательный код. Запускать их на основном устройстве не следует.
