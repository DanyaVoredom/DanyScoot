# Urent BLE probe

Инструмент для проверки протокола A3 A4 на своем самокате. Проверяет гипотезы, которые нельзя подтвердить по APK: обязательна ли авторизация, принимает ли прошивка команду с чужим session key, можно ли повторить записанный кадр.

Те же проверки bypass и brute есть в Android-приложении на вкладке Urent, поэтому телефон можно использовать без ПК. Python-вариант нужен для ноутбука с BLE и для режимов capture и replay.

Установка зависимости:

```
python -m pip install bleak
```

Сканирование:

```
python tools/urent_probe.py --scan
```

Авторизация известными ключами и разблокировка:

```
python tools/urent_probe.py --address AA:BB:CC:DD:EE:FF --mode auth --unlock
```

Обход авторизации, отправка unlock с key 0 и key 0x42:

```
python tools/urent_probe.py --address AA:BB:CC:DD:EE:FF --mode bypass
```

Перебор однобайтового session key 0..255:

```
python tools/urent_probe.py --address AA:BB:CC:DD:EE:FF --mode brute --gap 0.05
```

Запись входящих кадров:

```
python tools/urent_probe.py --address AA:BB:CC:DD:EE:FF --mode capture --seconds 30
```

Повтор кадра:

```
python tools/urent_probe.py --address AA:BB:CC:DD:EE:FF --mode replay --hex "A3 A4 ..."
```

Режимы bypass и brute экспериментальные. Прошивка может требовать успешный auth до любых команд, тогда перебор session key не сработает. Проверять только на своем самокате.
