# APK

Каталог содержит base APK официальных приложений. Файлы хранятся через Git LFS.

## 1. Инвентарь

| Файл | Пакет | Версия | versionCode | Размер, байт | SHA-256 |
| --- | --- | --- | --- | --- | --- |
| urent-2.7.0-base.apk | ru.urentbike.app | 2.7.0 | 2070 | 82 428 322 | 503007AC2D084F95100145BD9D77D69B991E337F2642FF362BBF7F4A5EB8B7C3 |
| whoosh-4.0.0-base.apk | com.punicapp.whoosh | 4.0.0 | 418060 | 54 707 264 | 912079486F06E84581C92055C3E87DAF8C3CB11F55B8F81F41BA907ADDB53C29 |
| yandex-go-5.98.2-base.apk | ru.yandex.taxi | 5.98.2 | 50128633 | 192 834 008 | 27A75FDF62C9AB601BDDD89C6559E75231F868AC3F08FA463AE7A85D8B5C1862 |

SHA-256 сертификатов:

| Файл | Подпись | SHA-256 сертификата |
| --- | --- | --- |
| urent-2.7.0-base.apk | O=Urentbike | 90E028E6D376C9E79F8D0332BFFA8667F8D41F6E5B7A114A4602EC68A0AAE2DD |
| whoosh-4.0.0-base.apk | CN=Whoosh.Bike | 12607A1082202FEAFAA03AC22A3814F9EC770D731D87C667CE9FCA7B164F6BAE |
| yandex-go-5.98.2-base.apk | CN=OOO Yandex | ACA405DED8B25CB2E8C6DA69425D2B4307D087C1276FC06AD5942731CCC51DBA |

## 2. Происхождение

APK получены через ADB с устройства SM-A175F, Android 16, SDK 36.

Пакеты до выгрузки:

| Пакет | Версия |
| --- | --- |
| ru.urentbike.app | 2.7.0 |
| com.punicapp.whoosh | 4.0.0 |
| ru.yandex.taxi | 5.98.2 |

Сохранены только base APK. Split APK с native-библиотеками и ресурсами плотности не входят в этот каталог. Для Urent, Whoosh и Yandex Go BLE-код находится в dex base APK. Native-библиотеки не требуются для чтения Java/Kotlin-протокола.

## 3. Что подтверждено

| Приложение | BLE | Факт |
| --- | --- | --- |
| Urent 2.7.0 | нет | В манифесте нет BLUETOOTH-разрешений. В dex нет android.bluetooth и BluetoothGatt. Есть только uses-feature bluetooth_le, не связанный с клиентским стеком. |
| Whoosh 4.0.0 | да | Пакеты bike.whoosh.shared.ble.library_client, library_client_offline, library_server, library_scanner. IoT-профиль, строковые команды, шифрование на стороне API. |
| Yandex Go 5.98.2 | да | Пакет com.yandex.go.scooters.ble, Ninebot-протокол, команды AUTH, LOCK, UNLOCK, SOUND, CABLE_UNLOCK, UNLOCK_BATTERY_COVER. Провайдер протокола в сборке возвращает null, статические UUID и ключи отсутствуют. |

## 4. Проверка

Get-FileHash references\apks\urent-2.7.0-base.apk -Algorithm SHA256

Get-FileHash references\apks\whoosh-4.0.0-base.apk -Algorithm SHA256

Get-FileHash references\apks\yandex-go-5.98.2-base.apk -Algorithm SHA256

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\urent-2.7.0-base.apk

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\whoosh-4.0.0-base.apk

C:\Android\Sdk\build-tools\36.1.0\apksigner.bat verify --print-certs references\apks\yandex-go-5.98.2-base.apk

## 5. Ограничения

APK являются собственностью правообладателей и хранятся только для анализа совместимости. Файлы не распространяются как приложения и не заменяют официальные клиенты.

Отсутствие split APK не позволяет проверить native-часть, но не влияет на анализ dex-протоколов. Для Urent BLE-протокол не извлекается из официального APK: его там нет.
