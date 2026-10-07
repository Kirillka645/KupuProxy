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

Требования: **JDK 17** и Android SDK 35. Gradle 8.9 и AGP 8.7 не принимают JDK 21+ —
сборка падает на разборе, поэтому версия JVM задаётся явно:

```bash
export JAVA_HOME=/path/to/jdk-17
```

### Android (APK)

```bash
# Проверки + debug APK
./gradlew testDebugUnitTest lintDebug assembleDebug

# Release APK (нужен подписанный ключ в ~/.android/debug.keystore или в secrets CI)
./gradlew assembleRelease

# Готовый файл
# app/build/outputs/apk/release/app-release.apk
```

### Desktop (Windows / Linux / macOS)

Сборка идёт через Compose Multiplatform и `jpackage` из JDK 17.

```bash
# Запускаемый uber-jar для текущей ОС
./gradlew :desktop:packageUberJarForCurrentOS
# desktop/build/compose/jars/KupuProxy-<os>-<version>.jar
java -jar desktop/build/compose/jars/KupuProxy-windows-x64-1.4.0.jar

# Windows: установщик .msi
./gradlew :desktop:packageMsi
# desktop/build/compose/binaries/main/msi/KupuProxy-1.4.0.msi

# Windows: portable-версия без установщика
./gradlew :desktop:createDistributable
# desktop/build/compose/binaries/main/app/KupuProxy/KupuProxy.exe

# Linux: .deb
./gradlew :desktop:packageDeb
# desktop/build/compose/binaries/main/deb/kupuproxy_1.4.0_amd64.deb

# Linux: portable .tar.gz
./gradlew :desktop:packageDistributionForCurrentOS
# desktop/build/compose/binaries/main/tar/kupuproxy-1.4.0.tar.gz

# macOS: .dmg
./gradlew :desktop:packageDmg
# desktop/build/compose/binaries/main/dmg/KupuProxy-1.4.0.dmg
```

> `jpackage` требует версию в формате `MAJOR.MINOR.BUILD`, поэтому «маркетинговая» версия
> `1.4.0.2` упаковывается как `1.4.0`. Полная версия доступна внутри приложения и в окне
> «О программе».

> Сборка `.msi` дополнительно требует **WiX Toolset 3.x** — он нужен `jpackage` на Windows.
> Остальные форматы (`.deb`, `.dmg`, portable, `.tar.gz`) собираются без него.
> Portable-сборка на Windows — `createDistributable`, на Linux — `packageDistributionForCurrentOS`.

Запуск сразу на нужном разделе:

```bash
java -jar KupuProxy-windows-x64-1.4.0.jar --tab=proxies
java -jar KupuProxy-windows-x64-1.4.0.jar --tray      # стартовать свёрнутым в трей
```

### Обновление манифеста автообновления

Манифест содержит размер и SHA-256 **реально выпущенного** APK, поэтому заполняется после
сборки, а не вручную:

```bash
./gradlew assembleRelease
pwsh tools/release/update-manifest.ps1 \
  -ApkPath app/build/outputs/apk/release/app-release.apk \
  -Version 1.4.0.2 \
  -Changelog "Краткое описание релиза."
```

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

KupuProxy collects and independently verifies Telegram proxies. Version 1.4.0.2 adds a desktop client for Windows, Linux and macOS built on Compose Multiplatform, sharing one Kotlin core with the Android app, and adds SOCKS5, HTTP and WEB proxy support alongside MTProto. Availability checks now run a fast TCP preflight per host before the full protocol handshake, measure jitter across several samples, and colour-code connection quality. The brand palette is unified across both clients.

Download the APK from [GitHub Releases](https://github.com/Kirillka645/KupuProxy/releases/latest). No advertising SDKs, analytics, or trackers are included.

## License

[MIT](LICENSE)
