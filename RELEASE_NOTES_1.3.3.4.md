# KupuProxy 1.3.3.4

## Kort Verified Collector

- Добавлен единый metadata-aware источник `kort0881/telegram-proxy-collector`.
- Поддержаны verified MTProto JSON, регионы RU/EU/US/Asia, upstream ping, FakeTLS SNI, метод внешней проверки и признак probe resistance.
- SOCKS5 намеренно не импортируется: приложение проверяет и подключает только MTProto.
- Если verified JSON временно недоступен, используется `proxy_all_mtproto.txt`, затем локальный snapshot/общий кэш.

## Автообновление и интерфейс

- GitHub Action раз в 4 часа зеркалирует Kort, Shablin, ALIILAPRO, hookzof и dubblebyte в `proxy-feeds/`, валидирует размер/JSON, дедуплицирует MTProto и коммитит только изменившиеся данные.
- Shablin latency-sorted и Dubblebyte добавлены как отдельные быстрые источники; локальные зеркала имеют прямые upstream fallback.
- SOCKS5 сохраняется только как зеркальный сторонний feed и не участвует в Android MTProto-проверке.
- Фоновый refresh больше не зависит от позиции источника в списке (`take(8)` удалён).
- WorkManager использует явный список источников, сетевые constraints и exponential backoff.
- Доступны интервалы 3/6/12/24 часа и режим только безлимитной сети.
- На главном экране отображаются свежесть, количество MTProto и региональная статистика, доступны быстрые фильтры Все/RU/EU/US/Asia.

## Надёжность

- Snapshot и status сохраняются атомарно; повреждённый/пустой ответ не заменяет хороший локальный список.
- Dedupe считает только независимые source IDs и объединяет полезные метаданные.
- Метаданные коллектора используются только для предварительного приоритета. Рабочей прокси считается исключительно адрес, который прошёл собственный MTProto/FakeTLS handshake KupuProxy.

## Атрибуция

KupuProxy использует публично опубликованные generated feeds проекта [kort0881/telegram-proxy-collector](https://github.com/kort0881/telegram-proxy-collector) как сторонний источник данных. Код внешнего проекта не копируется.
