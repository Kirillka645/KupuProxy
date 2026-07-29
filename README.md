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

## Возможности (v1.3.3.5)

- **Kort Verified Collector**: единый автообновляемый MTProto snapshot, регионы RU/EU/US/Asia, upstream freshness и metadata-aware приоритет
- Фоновое обновление каждые 3/6/12/24 часа через WorkManager с выбором любой или только безлимитной сети
- Внешняя проверка используется только для приоритета: итог всегда подтверждает собственный **MTProto handshake KupuProxy**
- Полностью обновлённый интерфейс **Jetpack Compose + Material 3**
- **Мега-скан** с единым параллельным агрегатором, retry и CDN-фолбэком
- **TG-bypass**: если `t.me` недоступен — каналы читаются через Jina reader, RSSHub, allorigins, telesco.pe
- Ускоренный ограниченный **мультиформатный парсер**: tg://, t.me, JSON, host:port:secret, HTML, YAML, markdown, base64
- Настоящая **MTProto-проверка** (handshake + req_pq / resPQ) с ограниченным пулом воркеров
- **Профили Wi‑Fi / LTE**, ранняя остановка, live-результаты, seed offline, кэш и избранное
- **Канал @KupuProxy** в UI + безопасные пользовательские HTTPS-источники с защитой от SSRF
- **Room** + **WorkManager**, remote `sources_manifest.json`, MediaStore-экспорт в Downloads
- Проверяемое обновление APK: доверенный GitHub Release, SHA-256, package/version и сертификат подписи; видимый статус, ручная проверка в настройках и retry
- GitHub Actions: unit tests, lint и debug APK для каждого PR; отдельный workflow зеркалирует публичные фиды каждые 4 часа и коммитит только реальные изменения


## Архитектура (app)

```
core/       Constants, FeatureFlags, TelegramIntents, QrEncoder
domain/     models, ProxyParser, ProxySource, ProxyAggregator
data/       sources, Room, DataStore promo prefs, export, remote HTTP
ui/         Material 3 Compose theme, reusable proxy/channel components, Settings/About/Sources
```

## Источники

- SoliSpirit/mtproto
- [kort0881/telegram-proxy-collector](https://github.com/kort0881/telegram-proxy-collector): публичные generated feeds Verified + RU/EU/US/Asia. Это сторонние данные; KupuProxy не копирует код коллектора и независимо перепроверяет каждую прокси
- [shablin/mtproto-proxy](https://github.com/shablin/mtproto-proxy): MIT, latency-sorted TXT/JSON; ALIILAPRO, hookzof и dubblebyte используются как дополнительные публичные фиды
- Surfboardv2ray TGProto, Argh94 scraper, Grim1313

Workflow `.github/workflows/mirror-proxy-feeds.yml` каждые 4 часа сохраняет снимки в `proxy-feeds/`, проверяет JSON и размер ответов, строит дедуплицированный `mtproto_merged.txt` и делает commit только при изменении содержимого. SOCKS5-файлы зеркалируются как данные, но Android-приложение их не проверяет и не выдаёт за MTProto.
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
