# KupuProxy

[![Android CI](https://github.com/Kirillka645/KupuProxy/actions/workflows/android.yml/badge.svg)](https://github.com/Kirillka645/KupuProxy/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/Kirillka645/KupuProxy)](https://github.com/Kirillka645/KupuProxy/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/Kirillka645/KupuProxy/releases)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

KupuProxy — Android-приложение для поиска, проверки и подключения Telegram MTProto-прокси. Приложение собирает адреса из нескольких публичных источников и самостоятельно подтверждает их работоспособность через MTProto handshake.

> Скачать APK: [GitHub Releases](https://github.com/Kirillka645/KupuProxy/releases/latest)
>
> Telegram-канал: [@KupuProxy](https://t.me/KupuProxy)

## Возможности 1.4.0.1

- четыре режима проверки: быстрый, сбалансированный, полный и пользовательский;
- полный скан до 15 000 уникальных адресов;
- отдельные профили сети Auto, Wi-Fi и LTE;
- параллельная проверка MTProto-прокси с живыми результатами;
- история проверок, рейтинг надёжности и статистика источников;
- фоновый контроль избранных прокси с уведомлениями;
- создание QR-кода прокси и импорт QR из изображения;
- настройка порядка и видимости источников на главном экране;
- персональный дизайн: темы, палитры, HEX-цвета, скругления и размер текста;
- локализация интерфейса и поддержка системного языка;
- безопасные отступы для вырезов экрана, системных панелей и клавиатуры;
- офлайн seed, локальный кэш, избранное и экспорт списков;
- пользовательские HTTPS-источники с защитой от SSRF;
- проверяемые обновления через GitHub Releases и SHA-256.

В 1.4.0.1 ускорен сбор Telegram-источников: зеркала проверяются параллельно, учитываются только ответы с валидными прокси, а уже полученные результаты не теряются из-за медленного канала. Добавлены отдельные 72-часовые снимки источников, восстановление локальных данных после прерванной записи, полный английский fallback для непереведённых строк и более строгая проверка тега, имени APK, SHA-256 и сертификата обновления.

## Как пользоваться

1. Установите APK из раздела [Releases](https://github.com/Kirillka645/KupuProxy/releases).
2. Выберите профиль сети: Auto, Wi-Fi или LTE.
3. Выберите глубину сканирования.
4. Нажмите кнопку запуска сканирования.
5. Подключите найденный прокси или добавьте его в избранное.

Быстрый режим подходит для ежедневного использования. Полный режим проверяет весь собранный список и поэтому занимает больше времени.

## Безопасность и приватность

- нет рекламных SDK, аналитики и трекеров;
- подключение выполняется через установленный Telegram-клиент;
- KupuProxy независимо перепроверяет полученные MTProto-прокси;
- пользовательские источники принимаются только по HTTPS и проходят проверку URL;
- APK обновления сверяется по версии, имени пакета, сертификату и SHA-256.

Публичные прокси принадлежат сторонним операторам. Не используйте их для передачи чувствительной информации и не считайте прокси заменой сквозного шифрования.

## Источники

В агрегатор входят публичные фиды SoliSpirit, shablin, Dubblebyte, SurfboardV2ray, Argh94 и другие источники из `sources_manifest.json`. Данные источников дедуплицируются, после чего приложение выполняет собственную проверку доступности.

## exteraGram plugin

Плагин находится в [`exteragram/kupu_proxy.plugin`](exteragram/kupu_proxy.plugin). Инструкция: [`exteragram/README.md`](exteragram/README.md).

Команды: `.kupu scan`, `.kupu chat`, `.kupu auto`, `.kupu update`.

## Сборка

Требования: JDK 17 и Android SDK 35.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Основные каталоги:

```text
core/       константы и общие утилиты
domain/     модели, парсер, источники и агрегатор
data/       сеть, Room, DataStore и экспорт
ui/         Jetpack Compose и Material 3
work/       фоновые проверки и обновления
```

## English

KupuProxy is an Android app for collecting and independently verifying Telegram MTProto proxies. Version 1.4.0.1 speeds up Telegram source collection, preserves partial and cached source results, prevents blank localized labels, makes local state writes crash-safe, and hardens signed in-app updates. It also includes scan depth presets, reliability history, source statistics, favorite monitoring, QR import/export, personal themes, and checks of up to 15,000 unique addresses.

Download the APK from [GitHub Releases](https://github.com/Kirillka645/KupuProxy/releases/latest). No advertising SDKs, analytics, or trackers are included.

## License

[MIT](LICENSE)
