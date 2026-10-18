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
| danyurent | yes, decoder, manual | да | поддержка в приложении |
| confidence | confirmed, inferred, catalog | да | уверенность |

Инварианты:

- code_dec равен code_hex.
- code_hex уникален.
- danyurent=yes только при наличии отдельной функции.
- confidence=confirmed требует источника сборщика или декодера.
- confidence=catalog не должен содержать догадок о payload.

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

## 3. Проверка

```
python references/verify_frames.py
```

Скрипт проверяет:

- сборку TX;
- совпадение hex;
- разбор;
- CRC8;
- CRC16;
- ожидаемые ошибки malformed.

## 4. Добавление записи

1. Выбрать уникальный id.
2. Указать cmd, key, rand, payload.
3. Сгенерировать frame.
4. Проверить verify_frames.py.
5. Указать note и источник.
6. Не менять существующие id.

## 5. Ограничения

- CSV не является схемой protobuf или JSON Schema.
- JSON не содержит версии схемы.
- Нет автоматической проверки уникальности id в CSV.
- Нет проверки, что все 54 опкода покрыты кадрами.
- Нет проверки, что все поля DanyUrent существуют в коде.

Эти проверки можно добавить в CI.
