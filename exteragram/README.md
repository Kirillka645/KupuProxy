# KupuProxy — плагин для exteraGram

Поиск и проверка **MTProto-прокси** прямо в [exteraGram](https://github.com/exteraSquad/exteraGram), с **самообновлением** с GitHub.

## Установка

1. Скачайте [`kupu_proxy.py`](./kupu_proxy.py)  
   или из [Releases](https://github.com/Kirillka645/KupuProxy/releases) файл `kupu_proxy.plugin`
2. Переименуйте в **`kupu_proxy.plugin`** (если нужно)
3. Отправьте файл себе в **Избранное** (Saved Messages) в exteraGram
4. Откройте файл → **Install plugin**
5. Включите плагин в настройках

Требуется **exteraGram 11.9.1+** (лучше 12.8.1+).

## Команды

| Команда | Действие |
|---------|----------|
| `.kupu` | Справка |
| `.kupu scan` | Скачать списки + TCP-проверка |
| `.kupu list` / `.kupu top` | Список рабочих |
| `.kupu use 1` | Подключить прокси №N |
| `.kupu update` | Обновить плагин с GitHub |
| `.kupu menu` | Диалог (скан / топ / update) |

Также: пункт **KupuProxy** в боковом меню (drawer).

## Источники

- [SoliSpirit/mtproto](https://github.com/SoliSpirit/mtproto)
- [Yagami200/free-mtproto-proxies](https://github.com/Yagami200/free-mtproto-proxies)
- kort0881, Argh94 scraper, Surfboard, ALIILAPRO  
(через CDN-зеркала: jsDelivr / githack)

## Самообновление

- При старте (если включено в настройках) плагин проверяет  
  `raw.githubusercontent.com/Kirillka645/KupuProxy/.../kupu_proxy.py`
- Команда `.kupu update` — принудительно
- Новая версия **перезаписывает файл** плагина → перезапустите exteraGram

## Настройки плагина

- Авто-проверка обновлений  
- Лимит проверки / параллельность / timeout  
- Стоп после N рабочих  

## Заметки

- В плагине проверка **TCP** (быстро). Полноценный MTProto handshake — в Android-приложении KupuProxy.
- Подключение через `tg://proxy` (нативный диалог Telegram) + fallback SharedConfig.

## Android-приложение

Тот же проект: [Kirillka645/KupuProxy](https://github.com/Kirillka645/KupuProxy)

## Лицензия

MIT
