# Сборка

## 1. Параметры проекта

| Параметр | Значение |
| --- | --- |
| applicationId | ru.dany.scoot |
| namespace | ru.dany.scoot |
| versionCode | 8 |
| versionName | 1.5.0 |
| compileSdk | 36 |
| minSdk | 23 |
| targetSdk | 35 |
| Java | 17 |
| Gradle | 8.14.3 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin | 2.2.0 |

Проект Gradle находится в каталоге app. Корневой каталог репозитория не является Gradle-проектом.

## 2. Отладочная сборка

```
cd app
.\gradlew.bat --no-daemon assembleDebug
```

Результат:

```
app\app\build\outputs\apk\debug\app-debug.apk
```

## 3. Release-сборка

```
cd app
.\gradlew.bat --no-daemon assembleRelease
```

Результат:

```
app\app\build\outputs\apk\release\app-release.apk
```

Важно: в текущей конфигурации release использует debug signingConfig и отключённый minify. Это удобно для локальной проверки, но не подходит для публикации. Порядок выпуска описан в docs/release.md.

## 4. Тесты

```
cd app
.\gradlew.bat --no-daemon testDebugUnitTest
```

Тестовый отчёт:

```
app\app\build\reports\tests\testDebugUnitTest\index.html
```

## 5. Lint

```
cd app
.\gradlew.bat --no-daemon lintDebug
```

Отчёт:

```
app\app\build\reports\lint-results-debug.html
```

Текущий порог: сборка не падает на предупреждениях. Перед релизом нужно просматривать отчёт и устранять ошибки. Предупреждения категории UnusedResources, SetTextI18n, GradleDependency, InefficientWeight и NotifyDataSetChanged не влияют на работу, но снижают качество.

## 6. Полная проверка

```
cd app
.\gradlew.bat --no-daemon testDebugUnitTest assembleDebug lintDebug assembleRelease
```

Эта команда используется перед коммитом. Она проверяет компиляцию обоих вариантов, unit-тесты и lint.

## 7. Чистая сборка

Если результаты выглядят устаревшими:

```
cd app
.\gradlew.bat --no-daemon clean
.\gradlew.bat --no-daemon assembleDebug
```

Не удалять каталог app/.gradle вручную без необходимости. Он содержит кэш конфигурации.

## 8. Установка и запуск

```
adb devices
adb install -r app\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n ru.dany.scoot/.MainActivity
```

Если установлена другая подпись, использовать:

```
adb uninstall ru.dany.scoot
adb install app\app\build\outputs\apk\debug\app-debug.apk
```

## 9. Проверка APK

```
aapt dump badging app\app\build\outputs\apk\debug\app-debug.apk
apksigner verify --print-certs app\app\build\outputs\apk\debug\app-debug.apk
```

Ожидаемые признаки: package ru.dany.scoot, versionName 1.5.0, minSdk 23, targetSdk 35, label DanyScoot.

## 10. Типовые проблемы

| Симптом | Причина | Решение |
| --- | --- | --- |
| SDK location not found | нет local.properties или ANDROID_HOME | создать app/local.properties с sdk.dir или задать ANDROID_HOME |
| Unsupported class file major version | неверная JDK | переключить JAVA_HOME на JDK 17 |
| Could not resolve androidx | нет сети или неверные репозитории | проверить google() и mavenCentral() |
| compileSdk 36 not found | не установлен platform 36 | установить через sdkmanager |
| INSTALL_FAILED_UPDATE_INCOMPATIBLE | другая подпись | удалить приложение и установить заново |
| INSTALL_FAILED_OLDER_SDK | устройство ниже API 23 | использовать устройство с Android 6.0 или выше |
| Gradle daemon error | занятая память или процесс | повторить с --no-daemon, перезапустить |
| Lint принимает предупреждения за ошибки | изменён baseline или severity | проверить app/app/build.gradle.kts и lint.xml |

## 11. Воспроизводимость

Для проверки соответствия артефакта:

```
Get-FileHash app\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

Хеш отладочной сборки не является стабильным между машинами и запусками. Для релиза фиксируются versionCode, versionName, подпись и хеш конкретного файла.
