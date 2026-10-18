# Публичный XDDbot

Источники:

- https://github.com/bitplugg/XDDbot
- https://raw.githubusercontent.com/wiki/bitplugg/XDDbot/Protocol.md

Публичный проект описывает тот же транспорт и тот же кадр, что LostBot, NineHack и FishBot. Использовать его можно как второе мнение, но приоритет у кода APK.

## 1. Совпадает

| Пункт | XDDbot | APK |
| --- | --- | --- |
| Сервис | 6e400001-b5a3-f393-e0a9-e50e24dcca9e | то же |
| Write | 6e400002-b5a3-f393-e0a9-e50e24dcca9e | то же |
| Notify | 6e400003-b5a3-f393-e0a9-e50e24dcca9e | то же |
| CCCD | 00002902-0000-1000-8000-00805f9b34fb | то же |
| Заголовок | A3 A4 | то же |
| Поле 3 | (rand + 50) & 255 | то же |
| XOR | все байты после len на исходный rand | то же |
| CRC8 | MAXIM/DALLAS, check A1 | то же |
| CRC16 | MODBUS, check 4B37 | то же |
| MTU | запрос MTU, чанк max(20, mtu - 3) | LostBot то же |
| Сборка notify | буфер и проверка CRC8 | LostBot то же |

## 2. Расхождения

| Пункт | XDDbot | APK |
| --- | --- | --- |
| Поле 4 кадра | deviceType ^ rand | sessionKey ^ rand после рукопожатия |
| Ответ рукопожатия | payload[0] = реальный deviceType | payload[0] = статус 1, payload[1] = sessionKey |
| Поиск самоката | опкод 120 FIND_CAR | LostBot использует 162 (0xA2) с payload 01/02/03/06, 120 в каталоге называется XM_FIND_CAR |
| Полный список опкодов | protocol/Opcode.kt | A2/c.java:19-36, 54 записи |

Поле 4 не является отдельным deviceType. В запросе рукопожатия key=0, поэтому байт выглядит как rand и может трактоваться как deviceType. После рукопожатия это sessionKey. Ответ 01 XX в APK означает статус 1 и ключ XX, а не deviceType.

## 3. Практический вывод

- UUID, кадр, CRC8, CRC16 и чанкинг можно брать из XDDbot без изменений.
- Рукопожатие и поиск самоката нужно брать из APK.
- Если конкретная прошивка ведет себя как XDDbot, а не как LostBot, проверить оба варианта на hardware.
- Публичный код не заменяет проверку CRC и длины входящего кадра.
