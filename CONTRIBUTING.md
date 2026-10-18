# Участие

## Ветки

Рабочая ветка: dev. Стабильная: prod. Не коммитить напрямую в prod.

## Перед изменением

1. Прочитать docs/development.md и docs/testing.md.
2. Проверить, что факт протокола подтверждён в analysis.
3. Не добавлять команду без известного payload и направления.
4. Не добавлять сетевые разрешения без необходимости.

## Код

- Пакет ru.dany.urent.
- Kotlin, 4 пробела.
- Пользовательские строки в res/values/strings.xml.
- Состояние через StateFlow.
- Комментарии не добавлять.
- Новые команды: ScooterCodec, OpcodeCatalog, ScooterClient, тест.
- Новые настройки: Prefs с ограничением диапазона.

## Проверка

```
cd app
.\gradlew.bat --no-daemon testDebugUnitTest assembleDebug lintDebug assembleRelease
python ..\references\verify_frames.py
```

## Документация

Обновить затронутые файлы docs, analysis и references. Для нового опкода обновить references/opcodes.csv, analysis/opcode_reference.md и, если возможно, references/frames.json.

## Коммиты

Префиксы: feat:, fix:, doc:, chore:. Сообщение на английском, нижний регистр, кратко. Одна задача - один коммит.

## Безопасность

Не коммитить keystore, ключи, дампы с персональными данными и рабочие эксплойты. Для уязвимостей использовать правила из SECURITY.md.
