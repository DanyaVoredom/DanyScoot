# Темы

## 1. Список

| id | Название | Родитель | Фон | Основной цвет |
| --- | --- | --- | --- | --- |
| black | Черная | Theme.Material3.Dark.NoActionBar | 000000 | 3DDC84 |
| white | Белая | Theme.Material3.Light.NoActionBar | FFFFFF | 006D2C |
| light | Светлая | Theme.Material3.Light.NoActionBar | F6F8F4 | 1B6B3A |
| dark | Темная | Theme.Material3.Dark.NoActionBar | 101511 | 3DDC84 |
| amoled | AMOLED | Theme.Material3.Dark.NoActionBar | 000000 | 00E676 |

Тема по умолчанию: dark. Список задаётся enum AppTheme. Порядок в enum определяет порядок в диалоге выбора.

## 2. Различия

Black и AMOLED обе используют чёрный фон, но различаются акцентами и контейнерами. Black ближе к нейтральному тёмному интерфейсу с зелёным 3DDC84. AMOLED использует более насыщенный зелёный 00E676 и полностью чёрные поверхности для экономии энергии на OLED.

Light использует тёплый светлый фон F6F8F4 и тёмно-зелёный акцент. White использует чистый белый фон и более контрастный зелёный 006D2C.

Dark использует почти чёрный фон с зелёным оттенком 101511 и приглушённые поверхности.

## 3. Выбор темы

Тема хранится в Prefs по ключу theme. При старте MainActivity вызывает setTheme до super.onCreate, поэтому тема применяется без мигания.

Смена темы:

1. Пользователь открывает Настройки.
2. Нажимает кнопку темы.
3. Выбирает тему в диалоге.
4. Prefs.theme обновляется.
5. Activity пересоздаётся через recreate.
6. Новая тема применяется в onCreate.

Если id неизвестен, AppTheme.fromId возвращает dark.

## 4. Цветовые роли

Каждая тема задаёт:

- colorPrimary и colorOnPrimary;
- colorPrimaryContainer и colorOnPrimaryContainer;
- colorSecondary и colorSecondaryContainer;
- colorTertiary;
- colorSurface, colorOnSurface;
- colorSurfaceVariant, colorOnSurfaceVariant;
- colorSurfaceContainer, High, Highest;
- colorOutline и colorOutlineVariant;
- colorError;
- android:colorBackground.

Для новых элементов использовать тематические атрибуты, а не жёстко заданные цвета. Жёстко заданные цвета допустимы только для уровней журнала, где важна семантика.

## 5. Добавление темы

1. Добавить значение в enum AppTheme с уникальным id, labelRes и styleRes.
2. Добавить строку названия в strings.xml.
3. Добавить style в themes.xml с родителем Material3.
4. Задать все обязательные цветовые роли.
5. Проверить контраст текста на фоне.
6. Проверить журнал, кнопки, поля ввода, диалоги и нижнюю навигацию.
7. Обновить docs/themes.md и docs/ui.md.

Не добавлять тему только с другим primary color. Полный набор ролей нужен, чтобы Material 3 не подставлял случайные значения.

## 6. Проверка

Ручная матрица:

- главный экран;
- список устройств;
- поле ключа и клавиатура;
- поля настроек AI и клавиатура;
- диалог темы;
- журнал с уровнями TX, RX, OK, WARN, ERR;
- уведомление foreground-сервиса;
- поворот экрана;
- системная тёмная тема.

Автоматической проверки контраста нет. Это ограничение зафиксировано в docs/testing.md.
