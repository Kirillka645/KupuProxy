# KupuProxy

Современное Android-приложение для поиска, проверки и подключения **MTProto-прокси** к Telegram.

![Platform](https://img.shields.io/badge/Android-24%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-blue)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

## Возможности

- Загрузка прокси из открытых источников:
  - **Kort0881** — Россия (`proxy_ru.txt`) и Европа (`proxy_eu.txt`)
  - **SurfboardV2ray** — большой проверенный список
- Параллельная проверка доступности (до 50 соединений)
- Сортировка по пингу (зелёный / жёлтый / красный)
- Добавление прокси в Telegram одним тапом
- Копирование топ-10 или всего списка
- Объединение всех источников в `.txt` (Downloads)
- Проверка собственного файла с `tg://proxy` ссылками
- Светлая / тёмная / системная тема
- Проверка обновлений через GitHub Releases

## Дизайн

- Material 3
- Тёмно-бирюзовая палитра Kupu
- Карточки с мягкими радиусами, hero-блок, индикаторы пинга

## Сборка

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Откройте проект в **Android Studio** (Hedgehog+ / Ladybug+) и запустите на устройстве.

## Источники данных

Списки берутся из:

- https://github.com/kort0881/telegram-proxy-collector
- https://github.com/Surfboardv2ray/TGProto

## Дисклеймер

Работоспособность серверов **как прокси Telegram** не гарантируется.  
Проверяется TCP-доступность хоста:порта. Используйте на свой страх и риск.

## Структура

```
app/src/main/java/com/kupuproxy/app/
├── MainActivity.kt
├── ProxyLoadingActivity.kt
├── ProxyListActivity.kt
├── ProxyAdapter.kt
├── ProxyManager.kt
├── CheckFileActivity.kt
├── MergeProxiesActivity.kt
├── Models.kt
└── updater/
    ├── UpdateChecker.kt
    └── GitHubRelease.kt
```

## Лицензия

MIT
