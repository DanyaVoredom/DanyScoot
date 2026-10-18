# Выпуск

## 1. Текущее состояние

Release-вариант собирается, но использует debug signingConfig. Это значит:

- APK можно установить для проверки;
- APK нельзя публиковать в RuStore, Google Play или другом магазине;
- обновление поверх другой сборки возможно только при совпадении подписи;
- хеш и сертификат не являются доверенными.

Для публичного выпуска нужен отдельный keystore и signingConfig release.

## 2. Версионирование

versionCode и versionName задаются в app/app/build.gradle.kts:

```
versionCode = 1
versionName = "1.0.0"
```

Правила:

- versionCode увеличивается на 1 для каждого публичного артефакта.
- versionName следует схеме major.minor.patch.
- Исправление ошибок: patch.
- Новая совместимая функция: minor.
- Несовместимое изменение протокола или формата настроек: major.
- versionCode никогда не уменьшается.

## 3. Подпись

Для локального release нужен файл keystore вне репозитория. Пример:

```
keytool -genkeypair -v -keystore danyscoot-release.jks -alias danyscoot -keyalg RSA -keysize 4096 -validity 10000
```

Параметры не коммитить. В CI использовать секреты. В build.gradle.kts добавить release signingConfig, читающий переменные окружения или local.properties.

Проверка подписи:

```
apksigner verify --print-certs app\app\build\outputs\apk\release\app-release.apk
```

## 4. Сборка релиза

```
cd app
.\gradlew.bat --no-daemon clean testDebugUnitTest lintDebug assembleRelease
```

Проверить:

- тесты проходят;
- lint не содержит ошибок;
- APK устанавливается на чистое устройство;
- демо-режим работает;
- реальное BLE-подключение проверено на совместимом самокате;
- ключ и журнал не раскрывают лишние данные;
- версия и подпись корректны.

## 5. Артефакты

| Артефакт | Путь |
| --- | --- |
| Debug APK | app\app\build\outputs\apk\debug\app-debug.apk |
| Release APK | app\app\build\outputs\apk\release\app-release.apk |
| Mapping | app\app\build\outputs\mapping\release\mapping.txt, если включён minify |
| Lint | app\app\build\reports\lint-results-debug.html |
| Тесты | app\app\build\reports\tests\testDebugUnitTest\index.html |

Для релиза фиксировать SHA-256:

```
Get-FileHash app\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

## 6. Чек-лист перед публикацией

- versionCode увеличен.
- versionName соответствует изменениям.
- Release подписан постоянным ключом.
- Debug-подпись не используется.
- Сетевых разрешений нет, если не добавлен сетевой функционал.
- Ключи не печатаются в журнал в открытом виде.
- Журнал не сохраняется на диск без согласия пользователя.
- Все строки переведены и не содержат заглушек.
- Темы проверены на светлом и тёмном фоне.
- Разрешения объяснены пользователю.
- Демо-режим не требует Bluetooth.
- Инструкция по отключению спама доступна.
- Правовая оговорка присутствует.
- Хеш и подпись записаны в release notes.

## 7. Публикация

В репозитории используются ветки dev и prod. Рабочая ветка: dev. После завершения задачи:

```
git status
git add <files>
git commit -m "feat: short description"
git push origin dev
git push origin dev:prod
```

Перед push в prod убедиться, что dev содержит только проверенные изменения. Не переписывать историю prod без необходимости.

## 8. Откат

Если релиз ошибочен:

1. Не удалять артефакт и тег.
2. Зафиксировать проблему в changelog.
3. Исправить в dev.
4. Выпустить новый versionCode.
5. Push в dev и prod.

Понижение versionCode не допускается. Пользовательские настройки при откате могут остаться от новой версии, поэтому миграции должны быть обратно совместимыми.
