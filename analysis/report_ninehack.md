# NineHack (com.timeylies.ninehack.ru): состав APK, BLE-транспорт, кадр, рукопожатие, команды, ответы, UI, аномалии

Сводный индекс: analysis/references.md. Полный протокол: analysis/protocol_full.md.

## 0. Артефакты и источники

- APK: references\NineHack.apk, 11 497 879 байт. Дамп apktool 3.0.3: analysis\NineHack (apktool.yml:1-2, apkFileName NineHack.apk).
- Пакет com.timeylies.ninehack.ru, versionCode 1, versionName 1.2, minSdk 3, targetSdk 49, compileSdk 34 (AndroidManifest.xml:2, apktool.yml:6-11).
- Java-декомпиляции (jadx) для NineHack нет, анализ выполнен по smali. Ниже пути без префикса analysis\NineHack\.
- Классы приложения: smali_classes3\com\timeylies\ninehack (50 smali файлов, включая синтетические lambdas), bleSettingsActivity.smali и bleSettingsActivity$1.smali в smali_classes4\com\timeylies\ninehack.
- Условные обозначения: строковые литералы приведены без завершающего восклицательного знака (в исходниках он есть, например BUG CRC Bad); остальные символы сохранены. Пометкой не подтверждено дампом отмечены утверждения, которые по дампу проверить нельзя.
- Байтовые примеры кадров в разделах 3, 5, 6 не являются литералами из дампа: они вычислены повторной реализацией сборки кадра и CRC8 из CommandCreator.smali и Crc8.smali при originalRand=0x01 и actualKey=0x00 (см. также раздел 3).

## 1. Состав APK и манифест

- Четыре dex: smali (classes.dex, 4522 smali), smali_classes2 (245), smali_classes3 (50), smali_classes4 (5124). Код приложения целиком в classes3, кроме экрана настроек (classes4).
- Разрешения (AndroidManifest.xml:3-13): BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION, ACCESS_COARSE_LOCATION (все с maxSdkVersion=30), BLUETOOTH_SCAN с usesPermissionFlags=neverForLocation, BLUETOOTH_CONNECT, а также DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (protectionLevel=signature).
- Разрешение INTERNET отсутствует (AndroidManifest.xml:3-13), сетевого кода в com.timeylies нет (grep java/net, HttpURLConnection, okhttp по smali_classes3\com\timeylies: совпадений нет). Телеметрия не заявлена.
- uses-feature android.hardware.bluetooth и android.hardware.bluetooth_le, оба required=true (AndroidManifest.xml:10-11).
- application: debuggable=true, allowBackup=true, extractNativeLibs=true, dataExtractionRules/backup_rules, theme Theme.NineHack (AndroidManifest.xml:14).
- Компоненты (AndroidManifest.xml:15-24): mainActivity exported=true (MAIN/LAUNCHER) и controlActivity, permissionsActivity, scanActivity, bleSettingsActivity - все exported=false.
- Служебная обвязка: androidx.startup.InitializationProvider с EmojiCompat/ProcessLifecycle/ProfileInstaller (AndroidManifest.xml:25-29), ProfileInstallReceiver exported=true с permission DUMP (AndroidManifest.xml:30-43).
- Подпись: v1 (original\META-INF\MANIFEST.MF, 2305 строк; original\META-INF\CERT.SF со строкой X-Android-APK-Signed: 2, 3, CERT.SF:1-5, Created-By: NP), то есть заявлены схемы v1/v2/v3.
- Отображаемое имя НайнХак   (Переделано Milky), версия Версия 1.2 (strings.xml:31-32).

## 2. BLE-транспорт

UUID (controlActivity.smali:31-62, :254-272; controlActivity$2.smali:536-546):
| Роль | UUID | Ссылка |
| Сервис (Nordic UART) | 6e400001-b5a3-f393-e0a9-e50e24dcca9e | controlActivity.smali:35 |
| Notify, TX (прием) | 6e400003-b5a3-f393-e0a9-e50e24dcca9e | controlActivity.smali:44 |
| Write, RX (передача) | 6e400002-b5a3-f393-e0a9-e50e24dcca9e | controlActivity.smali:53 |
| CCCD | 00002902-0000-1000-8000-00805f9b34fb | controlActivity.smali:264, controlActivity$2.smali:540 |

Подключение (controlActivity.smali:197-252):
- Адрес из intent-extra BLE_ADDRESS (controlActivity.smali:499-510), getRemoteDevice, connectGatt(this, false, callback, 2) то есть TRANSPORT_LE (controlActivity.smali:237), инкремент connectCount (controlActivity.smali:244-248). connectCount инициализируется 0 в конструкторе (controlActivity.smali:78-80) и больше нигде не сбрасывается.

GATT-цепочка (controlActivity$2.smali):
- onConnectionStateChange (265-395): status==0 и newState==2 - лог Подключено (284-286), затем Handler(main).postDelayed(discoverServices, 1000 мс) (289-305). status==0 и newState==0 - лог Отключено и controlTabs(false) (316-330). status не равен 0 - лог Couldn't Connect (339-341), disconnect (344), и если connectCount < 3, лог Переподключение и повторный connect по BLE_ADDRESS (349-379), иначе диалог Could not connect с finish (383-389, 84-124).
- onServicesDiscovered (571-650): status==0 - лог Сервисы Найдены (585), обход сервисов, поиск UART_SERVICE (619-629), для найденного сервиса enableNotifications(UART_TX_CHAR) (632-640).
- enableNotifications (controlActivity.smali:254-292): лог Enabling Notifications, поиск CCCD (264-272) и readDescriptor (278). Включение нотификаций начинается с чтения дескриптора, а не с записи.
- onDescriptorRead (397-524): setCharacteristicNotification(true) (417-419); для SDK>=33 (0x21) вызывается writeDescriptor(descriptor, ENABLE_NOTIFICATION_VALUE) с проверкой кода возврата (452-463); для более старых версий setValue(ENABLE_NOTIFICATION_VALUE) плюс writeDescriptor(descriptor) (472-495). Ветка status1==false печатает Couldn't Set Enable Notifications for Characteristic (515-519).
- onDescriptorWrite (526-569): UUID==CCCD и status==0 - лог Successfully enabled Notifications (557-559) и writeFirstStage() (562-564).
- onCharacteristicChanged (126-215): для UART_TX_CHAR лог Got Message, Intercepting и interceptMessage(value) (199-210). Иначе значение только логируется (141-183).
- onCharacteristicWrite (217-263): для UART_RX_CHAR и status==0 лог Successfully Wrote to Characteristic (251-258).

Передача (controlActivity.smali:632-687 и 689-773):
- UART_RX_CHAR.setValue(command), setWriteType(2) то есть WRITE_TYPE_DEFAULT (658 и 745), writeCharacteristic. Логи Writing Command либо Couldn't write command (672-682).
- requestMtu и onMtuChanged в коде приложения отсутствуют полностью (grep по smali_classes3\com\timeylies: совпадений нет). MTU не запрашивается и не отслеживается.
- Чанкинга нет: кадр передается одним writeCharacteristic без учета MTU. Максимальный кадр в этом протоколе 17 байт (unlock, payload 10), кадр set 13 байт, кадр ключа 15 байт, поэтому при стартовом MTU 23 усечение маловероятно; при MTU по умолчанию у Android ничего не запрашивается, реальный MTU зависит от платформы. Не подтверждено дампом: фактический MTU, согласованный конкретной прошивкой.
- CRC входящего кадра не проверяется: interceptMessage (CommandCreator.smali:1362-1529) CRC не вычисляет, последний байт кадра не читается. Ошибка CRC сообщается самим устройством веткой cmd 0x10 (раздел 6).

## 3. Формат кадра и CRC8

Раскладка кадра (сборка: CommandCreator.smali:855-941 для lock, :669-752 для key; константы CommandCreator.smali:44-60):
| Смещение | Поле | Значение, формула |
| 0..1 | заголовок | 0xA3 0xA4 (Header, CommandCreator.smali:44-48) |
| 2 | len | длина payload в байтах, константа метода |
| 3 | rand, проводной | (originalRand + 0x32) как byte (CommandCreator.smali:804-808) |
| 4 | key | actualKey XOR originalRand (CommandCreator.smali:810-815) |
| 5 | cmd | originalCmd XOR originalRand (CommandCreator.smali:816-821) |
| 6..5+len | data | payload[i] XOR originalRand (цикл CommandCreator.smali:828-845) |
| 6+len | CRC8 | Crc8.compute по байтам 0..5+len (CommandCreator.smali:899-905, 917-934) |

- originalRand = Random().nextInt(0xFE) + 1, то есть 1..254 (CommandCreator.smali:766-778).
- Ключевое отличие от wire-байта: XOR выполняется значением originalRand, а проводной байт rand сдвинут на +0x32. Разбор ответа симметричен: rand = msg[3] - 0x32 (CommandCreator.smali:1415-1421), далее XOR тем же originalRand (1427-1470).
- Опкоды commandBytes (CommandCreator.smali:52-60): 0x01 getKey, 0x10 error, 0x05 unlock, 0x15 lock, 0x61 set, 0x81 eject.

CRC8 (Crc8.smali):
- Таблица crcTable 256 int (Crc8.smali:45-52, значения :59-317, первые два 0x00 и 0x31).
- Поля: initial=0 (30-32), finalXor=0 (34-35), inputReflected=true (38-40), resultReflected=true (42-43).
- compute (390-442): crc=0; для каждого байта curByte = reflect8(b) (410), data = curByte XOR crc (416), crc = crcTable[data] (420-422); после цикла crc = reflect8(crc) XOR 0 (434-439). reflect8 - 343-386.
- Поля inputReflected/resultReflected в compute не читаются, отражение выполняется безусловно (не подтверждено дампом: намерение автора; фактическое поведение отражено в compute).
- Повторная реализация алгоритма дает контрольное значение CRC(123456789) = 0xA1, что соответствует CRC-8/MAXIM: полином 0x31 отраженный, init 0x00, refin/refout true, xorout 0x00. Контрольное значение в дампе не задано, вывод получен вычислением.

Вычисленные примеры (originalRand=0x01, actualKey=0x00; CRC посчитан по разделу выше):
| Кадр | Байты hex |
| KEY для ключа 4BKNwi77 | A3 A4 08 33 01 00 35 43 4A 4F 76 68 36 36 E7 |
| UNLOCK | A3 A4 0A 33 01 04 00 00 00 00 00 00 00 00 00 00 31 |
| LOCK | A3 A4 01 33 01 14 00 CE |
| SET, mode=2 headlight=2 throttle=2 | A3 A4 04 33 01 60 03 03 03 01 E4 |
| EJECT | A3 A4 01 33 01 80 04 57 |
| Ответ устройства: cmd 0x01, статус 0x00, ключ 0x01 | A3 A4 02 33 01 00 00 01 06 |
| Ответ устройства: cmd 0x01, статус 0x00, ключ 0x00 | A3 A4 02 33 01 00 00 00 58 |

## 4. Рукопожатие и ключ сессии

- Поле CommandCreator.actualKey (CommandCreator.smali:13), в конструкторе 0 (69-72). Это единственное хранилище сессионного ключа; при пересоздании controlActivity оно теряется.
- getKeyCommand всегда собирает кадр с originalKey=0 (CommandCreator.smali:600-603), то есть key = 0 XOR originalRand. В остальных командах originalKey = actualKey (CommandCreator.smali:784, 411, 977, 1175).
- writeFirstStage (controlActivity.smali:689-773): лог Trying to write GetKey Command, чтение ключа из SharedPreferences ninehack_settings по ключу ble_key с дефолтом @string/control_defaultBleKey (716-734), сборка getKeyCommand(key) (736), setValue, setWriteType(2), writeCharacteristic (740-750).
- Дефолтный ключ: strings.xml:44 = 4BKNwi77.
- Ввод ключа: bleSettingsActivity строит экран кодом (LinearLayout, TextView, EditText, Button), строки в base64 и декодируются методом dec (bleSettingsActivity.smali:19-44, :74-118, :156); текущее значение подставляется из SharedPreferences ninehack_settings/ble_key (174-196); saveBleKey сохраняет trim-значение, пустая строка заменяется дефолтом (201-252); клик по кнопке вызывает saveBleKey и finish (bleSettingsActivity$1.smali:27-35). Ключ хранится в открытом виде.
- Обработка ответа: cmd 0x01, при data[0]==1 выполняется actualKey = data[1] (CommandCreator.smali:100-107), controlTabs(true) (110-112), логи Ключ верный (117) и Key: 0x + hex (128-152). Иначе логи Неверный ключ:( и Попробуй другой ключ в настройках. (156-172).
- Таймаута и повторов рукопожатия нет: после writeFirstStage ожидание ответа не ограничено, повторная отправка ключа не выполняется. Не подтверждено дампом: поведение прошивки при неверном ключе (ветка при data[0] не равном 1 в дампе есть, но чем она вызывается на стороне устройства, неизвестно).
- До успешного рукопожатия кнопки вкладки Основное заблокированы: init вызывает controlTabs(false) (controlActivity.smali:436-438), который шлет fragment result tab1rx с data=false (797-823), а фрагмент по этому флагу включает либо выключает кнопки (control_tab1.smali:311-353).

## 5. Каталог команд и диспетчер UI

Команды (все в CommandCreator.smali):
| Метод | cmd | len | payload до XOR | Ссылки |
| getKeyCommand | 0x01 | 0x08 | байты строки ключа (String.getBytes) | 573-752, опкод 604-611, data 612-617, len 578 |
| getUnlockCommand | 0x05 | 0x0A | десять байт 0x01 | 1147-1349, опкод 1179-1186, data 1187-1191 и 1336-1348, len 1151 |
| getLockCommand | 0x15 | 0x01 | 0x01 | 754-942, опкод 788-795, data 796-800, len 758 |
| getSetScooterCommand | 0x61 | 0x04 | headlight, mode, throttle, 0 | 944-1145, опкод 981-988, data 989-1003, len 951 |
| getEjectBatteryCommand | 0x81 | 0x01 | 0x05 | 381-571, опкод 415-422, data 423-429, len 385 |

- 0x10 в commandBytes это не команда приложения, а признак ошибки устройства (раздел 6).
- Длина payload зашита константой. Для getKeyCommand это 8 при фактической длине key.getBytes(): если в настройках сохранен ключ другой длины, поле len разойдется с числом переданных байт data (CommandCreator.smali:578 против 612-617).

Диспетчер UI (controlActivity.smali:876-1126):
- Источник - fragment result tab1tx из control_tab1 (подписка controlActivity.smali:1157-1167), в bundle ключ data (byte[]), data[0] - действие (882-899).
- data[0]=0 - getUnlockCommand (1080-1095), data[0]=1 - getLockCommand (1062-1077), data[0]=2 - getEjectBatteryCommand (1044-1059), data[0]=3 - getSetScooterCommand (904-1038).
- Для set: data[1] - режим, packed-switch 0..2 отображается в mode 1, 2, 3 (918, 923-952); data[2] - переключатели, 0 - headlight=1 и throttle=1 (1014-1023), 1 - headlight=2 (1002-1008), 2 - throttle=2 (990-996), 3 - headlight=2 и throttle=2 (976-985). Вызов getSetScooterCommand(headlight, mode, throttle) (1030-1036).
- Порядок аргументов метода: headlight, mode, throttle (CommandCreator.smali:944-948), байты пишутся в data[0..2] и data[3]=0 (989-1003).
- Значения headlight/throttle принимают только 1 и 2; в протоколе это соответствует кодам, которые формирует UI (control_tab1.smali:216-271: оба включены - 3, только фары - 1, только газ - 2, ничего - 0).

Управление со вкладки Основное (control_tab1.smali):
- Кнопки: t1b1 Разблокир. шлет data {0} (42-76), t1b2 Заблокир. шлет {1} (78-112), t1b4 Извлечь Батарею шлет {2} (114-150), t1b3 Отправить шлет {3, режим, переключатели} (152-309).
- Режим: mode1RB - 0, mode2RB - 1, mode3RB - 2, не выбран - -1 (159-215). Переключатели: оба - 3, только headlight - 1, только throttle - 2, оба выключены - 0 (216-271).

## 6. Разбор ответов

interceptMessage (CommandCreator.smali:1362-1529):
- Логирование Intercepting Command (1367-1371), проверка заголовка mHeader=={0xA3,0xA4} (1373-1404).
- len = msg[2] (1406-1412); rand = (msg[3] - 0x32) как byte (1413-1424); key = msg[4] XOR rand (1425-1436); cmd = msg[5] XOR rand (1437-1448); data[i] = msg[6+i] XOR rand для i < len (1453-1475).
- CRC не сверяется; длина кадра не проверяется (обращение к массиву по индексам без проверки длины).
- Перебор commandBytes: при совпадении cmd вызывается decodeMessage (1486-1517), иначе кадр игнорируется.

decodeMessage (CommandCreator.smali:77-377), таблица веток:
| cmd | Условие | Лог | Ссылки |
| 0x01 | data[0]==1 | Ключ верный, Key: 0x.., actualKey=data[1], controlTabs(true) | 93-155 |
| 0x01 | иначе | Неверный ключ:( и Попробуй другой ключ в настройках. | 156-172 |
| 0x10 | всегда | Uh oh, we got an error from the scooter | 177-198 |
| 0x10 | data[0]==1 | BUG CRC Bad | 200-208 |
| 0x05 | data[0]==1 | Самокат успешно разблокирован | 212-237 |
| 0x05 | иначе | Ошибка разблокировки | 240-248 |
| 0x15 | data[0]==1 | Самокат Заблокирован | 253-278 |
| 0x15 | иначе | Ошибка блокировки. | 281-289 |
| 0x61 | data[0]==0 | Самокат установлен успешно | 294-319 |
| 0x61 | иначе | Установка самоката не удалась | 322-330 |
| 0x81 | data[0]==1 | Успешное изъятие батареи | 335-360 |
| 0x81 | иначе | Ошибка Изъятия... | 363-371 |

- Телеметрии нет: параметры батареи, пробега, скорости, состояния замка не запрашиваются и в ответах не разбираются. Вне списка commandBytes ответ игнорируется молча (1494-1517).
- Логи выводятся и в Log.i с тегами CommandCreator/FragData, и в TextView через controlActivity.addLogText (controlActivity.smali:778-796) с автопрокруткой вниз (fullScroll 0x82, controlActivity.smali:449-459).

## 7. UI и сценарии

mainActivity (mainActivity.smali, activity_main.xml):
- activity_main.xml: кнопка Settings (android:onClick openSettings, activity_main.xml:4), заголовок app_name (5), версия app_version (6), кнопка Start с @string/main_startButtonText (7, strings.xml:85).
- openSettings открывает bleSettingsActivity (mainActivity.smali:358-371).
- Start: проверка наличия адаптера, диалог Bluetooth Not Supported с @string/main_dialog_bluetooth_not_supported (175-193, 246-284, strings.xml:84); checkPerms (24-85): API>=31 - BLUETOOTH_CONNECT и BLUETOOTH_SCAN (34-44), иначе FINE/COARSE_LOCATION и BLUETOOTH (48-58); при успехе открывается scanActivity, иначе permissionsActivity (197-227).

permissionsActivity (permissionsActivity.smali, activity_permissions.xml):
- Экран: заголовок @string/permissions_headingText, пояснение @string/permissions_explanationText, три CheckBox (checkBox1 Блютуз, checkBox2 Геолокация, checkBox3 Ближайшие устройства; strings.xml:175-177), кнопка startButton @string/permissions_startButtonText (Начать скан., strings.xml:182), изначально disabled (activity_permissions.xml:11).
- Методы: askBluetoothPermissions (136), askLocationPermissions (207), askNearbyDevicesPermission (347), askPerms (481), checkCheckboxes (601), initCheckboxes (677), updateCheckboxes (796). Строки пояснений strings.xml:178-181.

scanActivity (scanActivity.smali, activity_scan.xml):
- SCAN_PERIOD = 0x2710 = 10000 мс (поле scanActivity.smali:9, использование 317-319).
- Кнопка Найти / Поиск... (@string/scan_scanButtonText и scan_scanButtonText2, strings.xml:184-185; переключение scanActivity.smali:337-341 и 366-370), ListView device_list (activity_scan.xml:5).
- checkAndStart (77-112): checkPerms, при отсутствии разрешений переход в permissionsActivity; checkPerms (114-176) аналогичен mainActivity по составу разрешений.
- scanLeDevice (265-406): проверка включенного GPS-провайдера и SDK<31, при выключенном - диалог @string/scan_dialog_enable_location (275-307, strings.xml:183); postDelayed остановки через 10000 мс (311-319); очистка списка и адаптера (322-334); startScan(leScanCallback) без ScanFilter и ScanSettings (344-348); стоп: removeCallbacksAndMessages, stopScan, flushPendingScanResults (373-391).
- ScanCallback (scanActivity$1.smali:146-165): onScanResult через runOnUiThread; дедупликация по BluetoothDevice, имя или No Name, строка имя\nMAC в ArrayAdapter (36-143).
- Клик по устройству (scanActivity.smali:420-507): Toast Address: MAC, intent в controlActivity с extra BLE_ADDRESS, остановка сканирования, cancelDiscovery, finish.
- Field EXTRAS_BLE_ADDRESS = BLE_ADDRESS (scanActivity.smali:7).

controlActivity (controlActivity.smali, activity_control.xml):
- Экран: ScrollView с TextView лога logTextView (activity_control.xml:4-6), TabLayout с тремя вкладками Основное, Конфиг, Инфо. (activity_control.xml:7-11, strings.xml:56-58), ViewPager2 (12).
- onCreate (1128-1213): setContentView activity_control (1139-1141), подписка на tab1tx (1157-1167), init (1170), checkPerms и переход в permissionsActivity при неудаче (1173-1194), OnBackPressedCallback с disconnect/close/finish (1199-1209, controlActivity$1.smali:37-65).
- init (341-447): привязка ViewPager2/TabLayout/ScrollView/logTextView, TabPagerAdapter, TabLayoutMediator, очистка лога, controlTabs(false), CommandCreator.init(this).
- start (491-514): лог Запущено, connect по BLE_ADDRESS.
- controlTabs (797-823): fragment result tab1rx с boolean data.
- addLogText (778-796).
- checkPerms (134-195): API>=31 - BLUETOOTH_CONNECT/BLUETOOTH_SCAN, иначе FINE/COARSE_LOCATION и BLUETOOTH; плюс проверка isEnabled адаптера.

Вкладки:
- control_tab1 (fragment_control_tab1.xml): кнопки t1b1 Разблокир., t1b2 Заблокир., t1b3 Отправить, t1b4 Извлечь Батарею (5-6, 22-24), блок Основные настройки: с RadioGroup mode1RB/mode2RB/mode3RB (по умолчанию checked mode3RB) (9-13) и SwitchMaterial headlightSwitch/throttleSwitch (оба checked по умолчанию) с подписями Фары и Газ(???) (14-21, strings.xml:46-55).
- control_tab2 и control_tab3 - заглушки: раздувают fragment_control_tab2/3.xml с единственным TextView @string/hello_blank_fragment = Пусто(Заглушка) (strings.xml:64, control_tab2.smali:18-34).
- TabPagerAdapter: три позиции, фрагменты control_tab1/2/3, getItemCount = 3 (TabPagerAdapter.smali:20-91).

## 8. Аномалии, отладочный мусор, подпись

- debuggable=true в APK, который распространяется как готовое приложение (AndroidManifest.xml:14).
- В APK упакован отладочный инструментарий Crashlytics: smali_classes4\com\google\firebase\crashlytics (3270 smali файлов), unknown\com\google\firebase\crashlytics\... (java-исходники javax.annotation и таблицы commons-codec), unknown\dump_syms\linux\dump_syms.bin (2 396 136 байт), unknown\dump_syms\macos\dump_syms.bin (1 460 176), unknown\dump_syms\windows\dump_syms.bin (4 787 443) и dump_syms.obj, unknown\DebugProbesKt.bin (1738 байт), unknown\LICENSES.txt, unknown\mozilla\public-suffix-list.txt (208 704 байта), unknown\META-INF\com\android\build\gradle\app-metadata.properties (appMetadataVersion=1.1, androidGradlePluginVersion=8.5.0). Суммарно около 8.6 МБ из 11.0 МБ APK.
- Сетевой код этих библиотек из приложения не вызывается: разрешения INTERNET нет, вызовов сетевых классов в com.timeylies нет (раздел 1). Инициализация Crashlytics в коде приложения не найдена.
- Ошибка проверки статуса в onDescriptorRead: результат writeDescriptor для SDK>=33 сравнивается с 1 (controlActivity$2.smali:452-463), тогда как BluetoothStatusCodes.SUCCESS равен 0, поэтому при успешной записи дескриптора печатается ошибка Couldn't write Enable Indication to Descriptor (не подтверждено дампом: фактическое значение, возвращаемое платформой, по дампу не проверяется; в коде явно сравнивается с 1).
- Нотификации включаются через readDescriptor (controlActivity.smali:278), а не сразу через writeDescriptor; цепочка рукопожатия стартует только из onDescriptorWrite (controlActivity$2.smali:552-564). При отказе чтения дескриптора лог Couldn't Read Descriptor (controlActivity.smali:285) и процесс дальше не идет.
- Автопереподключение срабатывает только при status не равном 0 (controlActivity$2.smali:334-379). Штатный разрыв (status==0, newState==0) обрабатывается логом Отключено без переподключения (316-330). connectCount суммирует все подключения за время жизни активности и не сбрасывается (controlActivity.smali:78-80, 244-248), поэтому после трех подключений переподключение прекращается.
- Длины len зашиты константами; для кадра ключа len=8 при длине строки ключа из настроек (CommandCreator.smali:578, 612-617). Ключ другой длины даст несоответствие len и фактического числа байт data.
- Таймаутов нет ни для рукопожатия, ни для команд: ответ не ожидается, ошибки записи только логируются (controlActivity.smali:669-682).
- Проверка CRC входящих кадров отсутствует; единственный признак проблем с CRC - сообщение устройства cmd 0x10 с data[0]==1 (CommandCreator.smali:200-208).
- Строки в ресурсах с ошибками оформления: НайнХак   (Переделано Milky) (strings.xml:31), Газ(???) (strings.xml:55), это нормально это андройд, андройда (strings.xml:180, 183).
- Дублирование кода: запись команды реализована дважды, writeCommand и writeFirstStage (controlActivity.smali:632-687 и 689-773).
- Crc8 хранит статическое поле instance и метод getInstance, но getInstance в коде приложения не используется; код строит Crc8 через new (CommandCreator.smali:901-903). Поля inputReflected/resultReflected в compute не читаются (Crc8.smali:390-442).
- bleSettingsActivity строит весь экран кодом без layout-ресурса и хранит строки в base64 (bleSettingsActivity.smali:54-172); activity_ble_settings.xml в дампе присутствует, но бинарный и не декодирован (не подтверждено дампом: его содержимое).
- Подпись: v1-манифест 2305 строк, CERT.SF заявляет схемы 2 и 3 (CERT.SF:1-5); отдельный факт о фактической проверке схем на устройстве по дампу не проверяется.

## 9. Сводка для переноса в DanyScoot

Минимальный набор для полной совместимости с NineHack-совместимыми самокатами:
1. Транспорт: сервис 6e400001-b5a3-f393-e0a9-e50e24dcca9e, прием (notify) 6e400003, передача (write) 6e400002, CCCD 00002902-0000-1000-8000-00805f9b34fb; connectGatt с TRANSPORT_LE; нотификации включать через setCharacteristicNotification плюс запись ENABLE_NOTIFICATION_VALUE в CCCD (в NineHack перед этим лишнее чтение дескриптора); write type WRITE_TYPE_DEFAULT.
2. Запись: один кадр на команду, без чанкинга; при переносе добавить requestMtu (например 247) и чанкинг по max(20, MTU-3) для устойчивости.
3. Кадр: A3 A4 len randWire key cmd data[len] crc8, где originalRand = Random.nextInt(254)+1, randWire = (originalRand+0x32) and 0xFF, key = actualKey XOR originalRand, cmd = opcode XOR originalRand, data[i] = payload[i] XOR originalRand, crc8 = CRC-8/MAXIM (poly 0x31 reflected, init 0x00, refin/refout true, xorout 0x00) по всем байтам кадра, включая заголовок.
4. Рукопожатие: отправить cmd 0x01 с payload = байты строки ключа (дефолт 4BKNwi77 из strings.xml:44), ждать cmd 0x01 с data[0]==1, запомнить actualKey = data[1]; при переносе добавить таймаут и повтор (в NineHack их нет).
5. Команды: 0x05 unlock (payload 10 байт 0x01), 0x15 lock (payload 0x01), 0x61 set (payload headlight, mode, throttle, 0), 0x81 eject (payload 0x05); headlight/throttle коды 1 и 2, mode 1..3; UI-мэппинг вкладки: data[0] 0/1/2/3 равно unlock/lock/eject/set, data[1] 0..2 равно mode 1..3, data[2] 0/1/2/3 равно (headlight,throttle) (1,1)/(2,0)/(0,2)/(2,2) в терминах кодов протокола.
6. Ответы: проверять заголовок, брать len из msg[2], rand = msg[3]-0x32, восстанавливать cmd и data; учитывать коды 0x01 (ключ), 0x10 (ошибка, data[0]==1 - ошибка CRC), 0x05, 0x15, 0x61 (data[0]==0 успех), 0x81. При переносе дополнительно проверять CRC и длину кадра, чего в NineHack нет.
7. Ограничения NineHack, которые не нужно переносить: отсутствие таймаутов и повторов, отсутствие проверки CRC и длины, фиксированный len в кадре ключа, отсутствие запроса MTU, ошибка сравнения статуса writeDescriptor с 1, debuggable-сборка и отладочный мусор Crashlytics.
