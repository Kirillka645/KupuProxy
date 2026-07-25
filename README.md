# KupuProxy

Android-приложение для поиска и проверки **MTProto-прокси** Telegram.

**https://github.com/Kirillka645/KupuProxy**  
**Канал:** https://t.me/KupuProxy

## Android + плагин exteraGram

| | |
|--|--|
| **Android app** | [Releases](https://github.com/Kirillka645/KupuProxy/releases) — APK |
| **exteraGram plugin** | [`exteragram/kupu_proxy.plugin`](./exteragram/kupu_proxy.plugin) — [инструкция](./exteragram/README.md) |

Плагин: `.kupu scan` / `.kupu chat` / `.kupu auto` / `.kupu update` (в чат пишет только `.kupu chat`).

## Возможности (v1.3.3)

- **Мега-скан** с параллельным агрегатом (таймаут, retry, CDN-фолбэк)
- **TG-bypass**: если `t.me` недоступен — каналы читаются через Jina reader, RSSHub, allorigins, telesco.pe
- **Мультиформатный парсер**: tg://, t.me, JSON, host:port:secret, HTML, YAML, markdown, base64
- **MTProto-проверка** (handshake + req_pq / resPQ)
- **Профили Wi‑Fi / LTE**, seed offline, кэш, избранное
- **Канал @KupuProxy** в UI + **свои URL-источники** (без Telegram)
- **Room** + **WorkManager**, remote `sources_manifest.json`
- Обновление APK из GitHub Releases


## Архитектура (app)

```
core/       Constants, FeatureFlags, TelegramIntents, QrEncoder
domain/     models, ProxyParser, ProxySource, ProxyAggregator
data/       sources, Room, DataStore promo prefs, export, remote HTTP
ui/         Compose theme + channel components, Settings/About
```

## Источники

- SoliSpirit/mtproto, Yagami200, kort0881 (RU/EU/All)
- Surfboardv2ray TGProto, ALIILAPRO, Argh94 scraper, Grim1313
- Telegram-каналы через **зеркала** (не только прямой `t.me/s/...`)
- Remote manifest + пользовательские URL (Room `sources`)

## Сборка

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew test
```

Требования: JDK 17, Android SDK 35.

## Feature flags

| Flag | Default | Описание |
|------|---------|----------|
| `FEATURE_CHANNEL_FEED` | `false` | Лента постов `t.me/s/KupuProxy` внутри app |

## Privacy

- Нет рекламных SDK, трекеров и аналитики.
- Промо канала — только локальный UI + Intent в Telegram.

## English

KupuProxy finds and verifies Telegram MTProto proxies. Subscribe: **https://t.me/KupuProxy**.  
Plugin for exteraGram lives in `exteragram/`. Multi-format parser, parallel aggregation, MTProto liveness check, offline seed, channel promo without trackers.

## License

MIT
