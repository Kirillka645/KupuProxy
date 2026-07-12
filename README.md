# KupuProxy

Android-приложение для поиска и проверки **MTProto-прокси** Telegram.

**https://github.com/Kirillka645/KupuProxy**

## v1.1.0

- **Мега-скан** — 6+ источников, ~500–1000 прокси после дедупа
- **CDN-зеркала** (jsDelivr, githack, ghproxy) — работает, когда raw.githubusercontent режется на LTE
- **Профили Wi‑Fi / LTE / Авто** — разный batch, таймаут и лимит проверки
- **Seed ~580** вшит в APK навсегда (офлайн)
- **Локальный кэш** + «Последние Wi‑Fi / LTE»
- **Избранное ⭐**, фильтр пинга, шаринг
- Скачивание в Downloads **и** app-кэш

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
