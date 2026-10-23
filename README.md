# KupuProxy

![KupuProxy](assets/branding/readme-icon.png)

[![Android CI](https://github.com/Kirillka645/KupuProxy/actions/workflows/android.yml/badge.svg)](https://github.com/Kirillka645/KupuProxy/actions/workflows/android.yml)
[![Desktop CI](https://github.com/Kirillka645/KupuProxy/actions/workflows/desktop.yml/badge.svg)](https://github.com/Kirillka645/KupuProxy/actions/workflows/desktop.yml)
[![Latest release](https://img.shields.io/github/v/release/Kirillka645/KupuProxy)](https://github.com/Kirillka645/KupuProxy/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/Kirillka645/KupuProxy/releases)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

KupuProxy — Android- и Desktop-приложение (Windows, Linux, macOS) для поиска и проверки Telegram-прокси. Общее Kotlin-ядро проверяет MTProto, SOCKS5, HTTP и WEB (HTTP CONNECT поверх TLS); подключение подходящих прокси выполняется через установленный Telegram-клиент.

> Скачать APK: [GitHub Releases](https://github.com/Kirillka645/KupuProxy/releases/latest)
>
> Telegram-канал: [@KupuProxy](https://t.me/KupuProxy)

## Возможности

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

Подробные изменения каждой опубликованной версии — в [заметках о релизах](docs/releases/).
Текущая версия, Android versionCode и версия Desktop-пакета задаются в `gradle.properties`.

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

Требования: **JDK 17** и Android SDK 35. Gradle 8.9 и AGP 8.7 не принимают JDK 21+ —
сборка падает на разборе, поэтому версия JVM задаётся явно:

```bash
export JAVA_HOME=/path/to/jdk-17
```

### Android (APK)

```bash
# Проверки + debug APK
./gradlew testDebugUnitTest lintDebug assembleDebug

# Release APK (нужны четыре KUPU_KEYSTORE_*/KUPU_KEY_* переменные; см. инструкцию ниже)
./gradlew assembleRelease

# Готовый файл
# app/build/outputs/apk/release/app-release.apk
```

Release-сборка больше не использует debug-ключ по умолчанию. Для обновления уже
установленных копий нужен **исходный ключ разработчика**, а не новый ключ раннера.
Переменные окружения, secrets CI и проверка сертификата описаны в [SIGNING.md](docs/releases/SIGNING.md).

### Desktop (Windows / Linux / macOS)

Сборка идёт через Compose Multiplatform и `jpackage` из JDK 17.

```bash
# Запускаемый uber-jar для текущей ОС
./gradlew :desktop:packageUberJarForCurrentOS
# desktop/build/compose/jars/KupuProxy-<os>-<version>.jar
java -jar desktop/build/compose/jars/KupuProxy-windows-x64-1.4.0.4.jar

# Windows: установщик .msi
./gradlew :desktop:packageMsi
# desktop/build/compose/binaries/main/msi/KupuProxy-1.4.4.msi

# Windows: portable-версия без установщика
./gradlew :desktop:createDistributable
# desktop/build/compose/binaries/main/app/KupuProxy/KupuProxy.exe

# Linux: .deb
./gradlew :desktop:packageDeb
# desktop/build/compose/binaries/main/deb/kupuproxy_1.4.4-1_amd64.deb

# Linux: portable .tar.gz (из portable-образа)
./gradlew :desktop:createDistributable
tar -czf KupuProxy-Desktop-linux-x64.tar.gz -C desktop/build/compose/binaries/main/app KupuProxy

# macOS: .dmg
./gradlew :desktop:packageDmg
# desktop/build/compose/binaries/main/dmg/KupuProxy-1.4.4.dmg
```

> `jpackage` требует версию в формате `MAJOR.MINOR.BUILD`, поэтому «маркетинговая» версия
> `1.4.0.4` упаковывается как `1.4.4` (последняя цифра становится BUILD, чтобы номер рос
> от релиза к релизу). Полная версия показывается в заголовке окна и в трее.

> Сборка `.msi` дополнительно требует **WiX Toolset 3.x** — он нужен `jpackage` на Windows.
> Остальные форматы (`.deb`, `.dmg`, portable, `.tar.gz`) собираются без него.
> Portable-сборка и на Windows, и на Linux — `createDistributable`
> (`packageDistributionForCurrentOS` на Linux собирает тот же `.deb`).

Запуск сразу на нужном разделе:

```bash
java -jar KupuProxy-Desktop-1.4.0.4.jar --tab=proxies
java -jar KupuProxy-Desktop-1.4.0.4.jar --tray      # стартовать свёрнутым в трей
```

### Обновление манифеста автообновления

Манифест содержит размер и SHA-256 **реально выпущенного** APK, поэтому заполняется после
сборки, а не вручную:

```bash
./gradlew assembleRelease
pwsh tools/release/update-manifest.ps1 \
  -ApkPath app/build/outputs/apk/release/app-release.apk \
  -Version 1.4.0.4 \
  -Changelog "Краткое описание релиза."
```

### Проверки и публикация

```bash
python3 -m unittest discover -s tools/release/tests -v
./gradlew :shared:desktopTest :desktop:desktopTest
# Проверка собранного Linux/Windows portable-образа без GUI
python3 tools/release/smoke_desktop.py --version 1.4.0.4
```

Desktop CI и release workflow используют общий `tools/release/stage_desktop.py`.
MSI, Windows ZIP/JAR, Linux DEB/tar.gz, macOS DMG и подписанный APK обязательны для релиза.
Workflow проверяет сертификат APK, размер/SHA-256/URL манифеста и создаёт `SHA256SUMS` для всех ассетов.
Неполный релиз не публикуется. После merge и зелёного CI выпускайте **новый тег**, не заменяйте старый.

Об ошибках сообщайте через [Issues](https://github.com/Kirillka645/KupuProxy/issues/new/choose).
См. [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md) и [план дальнейших улучшений](docs/IMPROVEMENT_PLAN.md).

### Основные каталоги

```text
app/         Android-приложение (Kotlin, Jetpack Compose, Material 3)
shared/      общий код для Android и десктопа:
             модель прокси, парсер, проверка MTProto/SOCKS5/HTTP/WEB, токены оформления
desktop/     десктоп-клиент (Compose Multiplatform, трей, автозапуск, статистика)
proxy-feeds/ зеркала публичных списков прокси
tools/       служебные скрипты релиза
```

Внутри `app`:

```text
core/       константы и общие утилиты
domain/     источники и агрегатор
data/       сеть, Room, DataStore и экспорт
ui/         Jetpack Compose и Material 3
work/       фоновые проверки и обновления
```

## English

KupuProxy collects and independently verifies MTProto, SOCKS5, HTTP and WEB proxies on Android,
Windows, Linux and macOS. The clients share a Kotlin core; Desktop includes built-in proxy feeds,
an offline snapshot, tray integration and a local proxy tunnel. See [release notes](docs/releases/)
for version-specific changes. Version values live in `gradle.properties`.

Build with JDK 17 and Android SDK 35. Release APKs require the original developer signing key
and a pinned certificate fingerprint; there is no implicit debug-signing fallback.
See [signing instructions](docs/releases/SIGNING.md). Release publishing requires all platform
packages and tests to pass; portable packaging is shared with Desktop CI.
Download the APK from [GitHub Releases](https://github.com/Kirillka645/KupuProxy/releases/latest). No advertising SDKs, analytics, or trackers are included.

## License

[MIT](LICENSE)
