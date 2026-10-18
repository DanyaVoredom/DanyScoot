# FishBot.apk: анализ

Сводный индекс: analysis/references.md. Полный протокол: analysis/protocol_full.md.

Разбор: analysis/FishBot (apktool, пакет FishBot.en.Milky), база сравнения: analysis/LostBot (ru.lostbotapp.Free). Ассет плагина: analysis/payload.

## 1. Состав

- smali: код приложения ru.lostbotapp.
- smali_classes2: упаковщик и движок: cd 1076 файлов, mirrorb 421, com/px 306, com/cloudinject 30, org/lsposed/hiddenapibypass 12, com/sadfxg/fasg.
- В LostBot этих пакетов нет: поиск по sadfxg, mirrorb, cloudinject, VpnService, 104.18 в analysis/LostBot/smali дал 0 совпадений.

## 2. Работоспособность инжектора com.px / com.cloudinject

Как shipped инжектор нерабочий: стартовый plumbing живой, полезная нагрузка отсутствует.

Живое:

- ru/lostbotapp/LostBotApplication.smali строка 2: суперкласс com/sadfxg/fasg/App, framework вызывает App.attachBaseContext.
- smali_classes2/com/sadfxg/fasg/App.smali: attachBaseContext (строка 4311) -> attachBaseContext2 (198) и handleCrash (1740).
- attachBaseContext2: строка 213 -> featureHookPms (293), строка 262 -> initFeature (1828).
- featureHookPms: строка 1094 const-class Landroid/app/ActivityThread; строки 700-702 new Lcd/uk. cd/uk.smali реализует InvocationHandler, собирает массив Landroid/content/pm/Signature; (237) и подменяет PackageInfo.signatures (291). Спуфинг подписи в PMS.
- initFeature, ветка sswitch_7 (2065-2079, дубль 2757-2769): Assets -> mirrorb/android/webkit/<имя>.smali:872 -> AssetManager.open -> mirrorb/android/service/persistentdata/<имя> (установка ассет-плагина).
- Мертвые ветки: App.onCreate (5176) и onCreate2 (3120) не вызываются: LostBotApplication.<init> строка 35 зовет Application.<init>, строка 104 зовет Application.onCreate.

Отсутствует:

- Манифест FishBot не содержит компонентов com.px и com.cloudinject (AndroidManifest.xml, строки 30-55: только ru.lostbotapp.* и androidx).
- Пакеты com.cloudinject.core и com.cloudinject.plugincore отсутствуют; есть только com/cloudinject/core/utils/compat, customview, feature, model, CustomDialog.
- Launcher плагина com.cloudinject.core.app.MainActivity отсутствует и в APK, и в ассете (analysis/payload/AndroidManifest.xml: пакет com.cloudinject.resplugin, launcher com.cloudinject.core.app.MainActivity, разрешений нет).
- com/cloudinject/feature/MainActivity extends Landroid/support/v7/app/d, но android/support/v7 в APK нет (glob smali*/android/support/v7: 0 файлов).
- Cross-dex ссылок на упаковщик две: App.smali и smali_classes2/com/px/FService.smali:141 (App.getContext).

## 3. Цели инжектора

- Цель первая: само приложение. PMS-хук подменяет подписи (cd/uk), из ассетов ставится плагин, используются DexClassLoader и InMemoryDexClassLoader (cd/kb 1087-1097, cd/ya 11024/11352, cd/ya.d BaseDexClassLoader).
- cd: VirtualApp-подобный движок, строки ActivityThread$H, IActivityManager, LoadedApk, sPackageManager, mPM, currentActivityThread, PackageParser$SigningDetails, libcore.io.Libcore, IWifiManager.
- В mirrorb лежит туннель Cloudflare/WARP: строки fastTestCloudflare, socks5 auth failed, 200 Connection established, CONNECT OK, doDirect, doProxy, адреса 104.18.115.242, 162.159.38.227, 2606:4700:3110::.
- com.cloudinject.feature.model.RemoteShareInfo (title, message, neutral, themeColor, showType, shareContent, ACTION_BROWSER/QQ_GROUP/QQ/COPY/SHARE): модель удаленно управляемого диалога и шаринга.

## 4. Лишние разрешения

AndroidManifest.xml строки 17-28 (блок дописан в конец, дублирует INTERNET, FOREGROUND_SERVICE, ACCESS_NETWORK_STATE со строк 3-7): REORDER_TASKS, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, READ_PHONE_STATE, BIND_VPN_SERVICE, REQUEST_INSTALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES, ACCESS_WIFI_STATE. В манифесте LostBot их нет.

Ни одна из строк не встречается в smali (поиск по BIND_VPN_SERVICE, QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, REQUEST_INSTALL_PACKAGES: 0 совпадений); компоненты в манифесте отсутствуют. Кода правки манифеста нет: единственное упоминание AndroidManifest в smali_classes2 это диагностическая строка org/lsposed/hiddenapibypass/<имя>.smali:1153. Разрешения дописаны при пересборке вручную.

## 5. Различия кода FishBot против LostBot

Реальные различия 6 файлов, остальные 53 отличия это шум (сдвиг индекса поля LC2/F после удаления f:Lx2/f и перестановка пар smali у apktool):

- smali/a/a.smali:11080: const-string "License" вместо байтов 43 72 61 63 6b 65 64 20 62 79 20 4d 69 6c 6b 79 (Cracked by Milky).
- smali/D2/C0.smali: decode(String) = Base64.decode(str,0) и new String(bytes, UTF_8) вместо mky()/mky2() с байтами MilkyU_a_fficial.
- smali/y2/s.smali: добавлен decode(String), укорочен b() (в LostBot b() читает lb_access_token из Ly2/t).
- smali/C2/x.smali: убраны поле LC2/F;->f:Lx2/f, ветка rem-int и период 300000 мс, вместо этого каждый цикл вызывает C2/F->N().
- smali/E2/a.smali: палитра. FishBot: 0xff050505, 0xff140a10, 0xfff5f5f7, 0xffd98ec8, 0xff7a6b75, 0xffff5fa2, 0x29ff5fa2, 0xffff8fc7, 0xfff7a8d0, 0xffc83a7a (розовая). LostBot: 0xff000000, 0xff1a1a1a, 0xffffffff, 0xffaaaaaa, 0xff555555, 0xffc7c7c7, 0x29c7c7c7, 0xffcccccc, 0xffeeeeee, 0xff888888 (серая).
- x2/f.smali идентичен LostBot x2/g.smali (переименование), MainActivity.smali и SpamService.smali отличаются на одну строку (тот же сдвиг поля).

Инфраструктура:

- Плюс весь упаковщик (sadfxg, cd, mirrorb, com.px, org.lsposed.hiddenapibypass, com.cloudinject) и ассет плагина.
- Плагин: AndroidManifest.xml 1676 Б, classes.dex 10196 Б, resources.arsc, res/layout/cloudinject_*.xml; в dex маркер сборки B~~D8 {"compilation-mode":"release","min-api":20,"version":"1.4.94"}; layouts содержат EditText, Spinner, ProgressBar и com.cloudinject.customview.FeatureButton/FeatureLinearLayout (диалог ключа активации, регистрации, пароля).
- Манифест: пакет FishBot.en.Milky, тема и стиль переименованы в APKTOOL_RENAMED_0x7f0a000a, добавлен usesCleartextTraffic="true" (строка 29). Компоненты те же 4.
- apktool.yml: FishBot minSdk 17 / targetSdk 28 / versionCode 1 / versionName 1.0.0; LostBot minSdk 4 / targetSdk 42 / versionCode 4 / versionName 1.2.1.
- Подпись: FishBot стоковый AOSP debug (CN=Android Debug, 2008-02-29 до 2035-07-17). LostBot свой ключ от 2021-04-25 на 30 лет.

## 6. Антитампер, лицензия, телеметрия

- Антитампер: mirrorb/android/providers/<имя>.smali:1462 маркер Signatures Data; cd/uk подмена PackageInfo.signatures через PMS. В самих APK файлах маркера нет, он только как строка фреймворка.
- StringFog: код с сообщениями Stringfog implementation class not found / access / new instance failed. Константы u06xx в App.smali это ключи диспетчеризации control-flow flattening (sparse-switch), не полезная нагрузка. Реальные константы лежат открытым текстом в mirrorb, com/px, com/cloudinject.
- Сеть и C2: у упаковщика своего канала нет: в cd нет java/net/URL, HttpURLConnection, okhttp3, Socket (0 совпадений). Весь сетевой код в mirrorb (туннель Cloudflare/WARP).
- Лицензионные маркеры изменены: License и Base64-декодеры вместо Cracked by Milky и MilkyU_a_fficial; убран периодический опрос C2/F->f:Lx2/f с периодом 300000 мс.
- Имя выгружаемого файла: xz.apk (mirrorb/com/android/internal/view/<имя>.smali:1097).

## 7. Роль com.sadfxg.fasg.App

Application-класс упаковщика, суперкласс приложения. Константы (smali_classes2/com/sadfxg/fasg/App.smali строки 14-53): APP_ID 74008b4ea3a81cf40000242d50cf1fbf, PLUGIN_VERIFY d0194472979a4086f01b1c48753c4b5d, INCREMENT_DATA f77f26c0004a9e696205b8b44eed340b55940840bbe5783e, A b054a54d5673d87956f28ef720a4cb44bc0f04d2dec07fee0778d303fc1efe1e53122b046d5870b2, RS oTMnsXXVnU, VERSION_CODE 23, VERSION_NAME 2.3, PLATFORM 2, API_VERSION 1, TAG App.

Задача: перехватить жизненный цикл приложения без правки его кода. Точка входа attachBaseContext: хук PMS, чтение и установка плагина из ассетов, crash handler. Методы onCreate, onCreate2, requestPermissionList мертвы. Обфускация: control-flow flattening, строковые dispatch-ключи u06xx, имена классов в mirrorb арабо-персидскими буквами.

## 8. Выводы

- Инжектор как shipped нерабочий: plumbing выполняется на каждом запуске, но компоненты com.px и com.cloudinject не объявлены в манифесте, пакеты core и plugincore и launcher-активити плагина отсутствуют, android.support.v7 нет, UI плагина собрать нечем.
- Упаковщик нацелен на само приложение: подмена подписи, установка dex/apk из ассетов (xz.apk), VirtualApp-движок с hidden-API и DexClassLoader, туннель Cloudflare/WARP.
- 9 добавленных разрешений обслуживают упаковщик (VPN, установка пакетов, обход scoped storage, перечисление пакетов, Wi-Fi), а не функциональность FishBot.
- Доработка FishBot поверх LostBot: розовая тема, ребрендинг (пакет, тема, cleartext, debug-подпись), замена лицензионных маркеров на License, добавленные Base64-декодеры, снятие периодического опроса лицензии по сети, плюс упаковщик с плагином.
- Для DanyUrent имеет значение только прикладной слой: протокол LostBot, команды ключа, разблокировки, спама и телеметрии; упаковщик и плагин не используются.
