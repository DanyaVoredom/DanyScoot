# Схемы данных

## 1. opcodes.csv

Разделитель: запятая. Кодировка: UTF-8.

| Колонка | Тип | Обязательна | Значение |
| --- | --- | --- | --- |
| code_hex | hex 1..2 символа | да | код опкода |
| code_dec | integer | да | тот же код в десятичном виде |
| name | string | да | имя из каталога или подтверждённое |
| direction | BIDIR, SET, NOTIF | да | направление |
| tx_payload | string | да | описание payload |
| tx_source | string | да | источник сборщика |
| rx_decode | string | да | описание разбора |
| rx_source | string | да | источник декодера |
| danyscoot | yes, decoder, manual | да | поддержка в приложении |
| confidence | confirmed, inferred, catalog | да | уверенность |

Инварианты:

- code_dec равен code_hex.
- code_hex уникален.
- danyscoot=yes только при наличии отдельной функции.
- confidence=confirmed требует источника сборщика или декодера.
- confidence=catalog не должен содержать догадок о payload.

### opcodes.json

Корневой объект:

- schema: danyscoot.opcodes/v1;
- count: число записей;
- source: references/opcodes.csv;
- opcodes: массив.

Поля opcodes[] совпадают с opcodes.csv: code, code_hex, name, direction, tx_payload, tx_source, rx_decode, rx_source, danyscoot, confidence.

Инварианты:

- count равен длине массива и числу строк opcodes.csv.
- code, code_hex и name совпадают с opcodes.csv.
- Нет записей, отсутствующих в opcodes.csv.

### builders.csv

| Колонка | Тип | Значение |
| --- | --- | --- |
| code_hex | hex | код опкода |
| code_dec | integer | тот же код |
| name | string | имя опкода |
| variant | string | вариант payload, пусто для единственного |
| payload_template | string | шаблон payload |
| builder_source | string | файл и строки сборщика |
| vector | string | идентификатор кадра или ручной пометки |
| status | confirmed, inferred, catalog, notification | статус |
| notes | string | ограничения |

Инварианты:

- code_hex существует в opcodes.csv.
- Пара code_hex и variant уникальна.
- status=notification не означает сборщик команды.

### rx_fields.csv

| Колонка | Тип | Значение |
| --- | --- | --- |
| cmd | hex | код ответа |
| condition | string | условие применения |
| offset | integer или пусто | смещение |
| size | integer, len, len-1 или пусто | размер |
| encoding | string | u8, u16be, u32be, ascii и подобные |
| formula | string | вычисление |
| field | string | имя поля |
| values | string | допустимые значения |
| source | string | файл и строки декодера |
| confidence | confirmed, inferred, catalog | уверенность |

Инварианты:

- cmd существует в opcodes.csv.
- offset и size являются числом или выражением от len.
- confidence=confirmed требует декодер в источнике.

## 2. frames.json

Корневой объект:

- crc8;
- crc16;
- tx;
- rx;
- malformed.

### crc8

- poly_reflected;
- init;
- check;
- empty.

### crc16

- poly_reflected;
- init;
- check.

### tx[]

- id: уникальная строка;
- cmd: hex;
- key: integer;
- rand: integer 1..254;
- wire_rand: integer;
- payload: hex;
- frame: hex;
- note: строка.

Инварианты:

- frame начинается с A3A4;
- length равен длине payload;
- wire_rand = rand + 50 по модулю 256;
- key и cmd в кадре ксорятся исходным rand;
- CRC8 соответствует frame.

### rx[]

- id;
- cmd;
- payload;
- note.

### malformed[]

- id;
- frame;
- error;
- note.

Инварианты:

- error соответствует фактической ошибке парсера;
- frame не должен быть валидным.

## 3. apks.csv

| Колонка | Тип | Значение |
| --- | --- | --- |
| app | string | идентификатор артефакта, совпадает с manifests.json |
| file | string | имя файла |
| tracked | yes, no | файл хранится в репозитории |
| package | string | applicationId |
| version_name | string | versionName |
| version_code | integer | versionCode |
| min_sdk | integer | minSdkVersion |
| target_sdk | integer | targetSdkVersion |
| compile_sdk | integer | compileSdkVersion |
| size_bytes | integer | размер файла |
| sha256 | hex | SHA-256 файла |
| signature | OK, DOES NOT VERIFY | результат apksigner |
| signature_sha256 | hex | SHA-256 сертификата подписи |
| source | path | путь к файлу в репозитории или источник |
| notes | string | происхождение и ограничения |

Инварианты:

- app уникален.
- tracked=yes требует существующий source.
- size_bytes и sha256 совпадают с файлом.
- source для tracked=yes указывает на файл внутри репозитория.
- официальные APK хранятся через Git LFS.

## 4. manifests.json

Корневой объект:

- schema: danyscoot.manifests/v1;
- apps: массив.

Поля app:

| Поле | Тип | Значение |
| --- | --- | --- |
| id | string | совпадает с apks.csv |
| package | string | applicationId |
| version_name | string | versionName |
| version_code | integer | versionCode |
| min_sdk | integer | minSdkVersion |
| target_sdk | integer | targetSdkVersion |
| compile_sdk | integer | compileSdkVersion |
| manifest_compile_sdk | string | compileSdkVersion из манифеста |
| application | object | атрибуты application без префикса android |
| component_counts | object | activity, activity_alias, service, receiver, provider, total |
| uses_features | array | объекты uses-feature |
| query_packages | array | пакеты из queries |
| query_actions | array | действия из queries |
| permission_count | integer | число uses-permission, uses-permission-sdk-23 и uses-permission-sdk-m |
| source | path | путь к декодированному AndroidManifest.xml |

Инварианты:

- id уникален.
- component_counts.total равен сумме пяти типов.
- permission_count не меньше числа уникальных строк permissions.csv для приложения.
- application.name указывает на существующий класс приложения.

## 5. permissions.csv

| Колонка | Тип | Значение |
| --- | --- | --- |
| app | string | идентификатор из manifests.json |
| permission | string | полное имя разрешения |
| max_sdk | integer или пусто | maxSdkVersion |
| flags | string или пусто | usesPermissionFlags |
| declared_count | integer | число объявлений |
| category | string | группа разрешения |
| risk | low, medium, high | оценка риска |
| source | path | манифест-источник |

Категории: audio, bluetooth, camera, contacts, device, foreground, install, internal, location, network, nfc, notification, other, overlay, packages, phone, sms, storage, system, vpn.

Инварианты:

- app существует в manifests.json.
- declared_count не меньше 1.
- BLUETOOTH_SCAN с neverForLocation содержит flags=neverForLocation.
- risk отражает опасность разрешения, а не его использование приложением.

## 6. components.csv

| Колонка | Тип | Значение |
| --- | --- | --- |
| app | string | идентификатор из manifests.json |
| type | activity, activity-alias, service, receiver, provider | тип компонента |
| name | string | имя класса |
| exported | true, false или пусто | android:exported |
| permission | string или пусто | android:permission |
| foreground_service_type | string или пусто | android:foregroundServiceType |
| process | string или пусто | android:process |
| launcher | yes или пусто | MAIN и LAUNCHER в одном intent-filter |
| actions | string | действия через точку с запятой |
| categories | string | категории через точку с запятой |
| data | JSON array или пусто | scheme, host, port, path, pathPrefix, pathPattern, pathAdvancedPattern, mimeType |
| source | path | манифест-источник |

Инварианты:

- app существует в manifests.json.
- launcher=yes только при наличии MAIN и LAUNCHER.
- data является валидным JSON-массивом или пустой строкой.
- exported фиксируется буквально из манифеста, без вычисления значения по умолчанию.

## 7. uuids.csv

| Колонка | Тип | Значение |
| --- | --- | --- |
| app | string | идентификатор приложения |
| role | string | service, write, notify, read, cccd, digital output, string, user control point |
| uuid | UUID или пусто | полный UUID |
| short | hex или пусто | короткий идентификатор |
| source | string | файл и класс |
| confidence | confirmed, unresolved | уверенность |

Инварианты:

- app существует в manifests.json.
- confidence=confirmed требует непустой uuid.
- confidence=unresolved допускает пустой uuid.
- Для Urent источником являются LostBot и NineHack, а не официальное приложение.

## 8. Проверка

```
python references/verify_frames.py
python references/validate_all.py
```

validate_all.py проверяет:

- обязательные колонки CSV;
- уникальность app и opcode;
- соответствие opcodes.json и opcodes.csv;
- уникальность пары code и variant в builders.csv;
- коды и confidence в rx_fields.csv;
- размер и SHA-256 всех tracked APK;
- суммы component_counts;
- категории и риск разрешений;
- типы компонентов;
- соответствие app в uuids.csv;
- структуру frames.json;
- вызов verify_frames.py.

## 9. Добавление записи

1. Выбрать уникальный id.
2. Указать источник и уверенность.
3. Не смешивать официальное приложение и сторонний клиент.
4. Для APK обновить apks.csv, manifests.json, permissions.csv и components.csv.
5. Для протокола обновить uuids.csv.
6. Запустить validate_all.py.
7. Не менять существующие id без причины.

## 12. Ограничения

- CSV не является схемой protobuf или JSON Schema.
- Нет JSON Schema для manifests.json.
- Категории разрешений заданы эвристикой и не являются классификацией Android.
- Нет проверки, что все 54 опкода покрыты кадрами.
- Нет проверки, что все поля DanyScoot существуют в коде.
- SHA-256 больших APK замедляет validate_all.py.
- CI не настроен.
