# KupuProxy

Android-приложение для поиска и проверки **MTProto-прокси** Telegram.

**https://github.com/Kirillka645/KupuProxy**

## Android + плагин exteraGram

| | |
|--|--|
| **Android app** | [Releases](https://github.com/Kirillka645/KupuProxy/releases) — APK |
| **exteraGram plugin** | [`exteragram/kupu_proxy.plugin`](./exteragram/kupu_proxy.plugin) — [инструкция](./exteragram/README.md) |

Плагин: команды `.kupu scan` / `.kupu use 1` / `.kupu update` (самообновление).

## Возможности

- **Мега-скан** + live-список рабочих
- **CDN-зеркала** (jsDelivr, githack, ghproxy)
- **Yagami200 + SoliSpirit** и другие списки
- **Профили Wi‑Fi / LTE**
- **Seed** в APK + локальный кэш
- **Обновление APK** кнопкой «Скачать и установить»
- **Избранное ⭐**, фильтр пинга

## Источники

- SoliSpirit/mtproto
- kort0881 (RU / EU / All)
- Surfboardv2ray TGProto
- ALIILAPRO/MTProtoProxy

## Сборка

```bash
./gradlew assembleRelease
```

## Дисклеймер

TCP-пинг ≠ гарантия работы в Telegram. Используйте на свой страх и риск.

MIT
