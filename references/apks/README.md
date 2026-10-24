# APK

Каталог содержит base APK официальных приложений. Файлы хранятся через Git LFS.

## 1. Инвентарь

| Файл | Пакет | Версия | versionCode | Размер, байт | SHA-256 |
| --- | --- | --- | --- | --- | --- |
| urent-2.7.0-base.apk | ru.urentbike.app | 2.7.0 | 2070 | 82 428 322 | 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 |

SHA-256 сертификатов:

| Файл | Подпись | SHA-256 сертификата |
| --- | --- | --- |
| urent-2.7.0-base.apk | O=Urentbike | 90E028E6D376C9E79F8D0332BFFA8667F8D41F6E5B7A114A4602EC68A0AAE2DD |

## 2. Происхождение

APK получены через ADB с устройства SM-A175F, Android 16, SDK 36.

Пакеты до выгрузки:

| Пакет | Версия |
| --- | --- |
| ru.urentbike.app | 2.7.0 |

Сохранены только base APK. Split APK с native-библиотеками и ресурсами плотности не входят в этот каталог. Native-библиотеки не требуются для чтения Java/Kotlin-протокола.

## 3. Что подтверждено

| Приложение | BLE | Факт |
| --- | --- | --- |
| Urent 2.7.0 | нет | В манифесте нет BLUETOOTH-разрешений. В dex нет android.bluetooth и BluetoothGatt. Есть только uses-feature bluetooth_le, не связанный с клиентским стеком. |

## 4. Проверка

Get-FileHash references\apks\urent-2.7.0-base.apk -Algorithm SHA256

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\urent-2.7.0-base.apk

## 5. Ограничения

APK являются собственностью правообладателей и хранятся только для анализа совместимости. Файлы не распространяются как приложения и не заменяют официальные клиенты.

Отсутствие split APK не позволяет проверить native-часть, но не влияет на анализ dex-протоколов. Для Urent BLE-протокол не извлекается из официального APK: его там нет.
