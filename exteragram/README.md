# KupuProxy — плагин для exteraGram

Поиск и проверка **MTProto-прокси** прямо в [exteraGram](https://github.com/exteraSquad/exteraGram), с **самообновлением** с GitHub.

## Установка

1. Скачайте `kupu_proxy.plugin` из релиза  
   **[v1.3.1](https://github.com/Kirillka645/KupuProxy/releases/tag/v1.3.1)**  
   (там же APK и исходник `kupu_proxy.py`)
2. Переименуйте в **`kupu_proxy.plugin`** (если нужно)
3. Отправьте файл себе в **Избранное** (Saved Messages) в exteraGram
4. Откройте файл → **Install plugin**
5. Включите плагин в настройках

Требуется **exteraGram 11.9.1+** (лучше 12.8.1+). SDK plugins **>= 1.4.0**.

## ⚡ Одной кнопкой

После установки плагина откройте:

**Настройки → Данные и память → Прокси** (экран «Настройки прокси»)

Сверху появится блок **KupuProxy** с кнопкой **«⚡ Сделать всё»**.

Она по очереди:

1. Скачивает списки прокси (SoliSpirit, Yagami200, …)
2. Проверяет, кто реально открывается
3. **Добавляет все рабочие** в список прокси Telegram
4. **Удаляет мёртвые** из вашего списка
5. **Включает прокси** и **автопереключение** (если один упадёт — Telegram перейдёт на другой)
6. Выбирает самый быстрый как текущий

То же самое командой в любом чате:

```
.kupu auto
```

Или через боковое меню → **KupuProxy** → **Сделать всё**.

## Команды

| Команда | Действие |
|---------|----------|
| `.kupu` | Справка |
| `.kupu auto` | **Полный автомат** (как кнопка «Сделать всё») |
| `.kupu scan` | Скачать списки + TCP-проверка |
| `.kupu chat` | Все рабочие `tg://` ссылки **в чат** |
| `.kupu add` | Добавить все рабочие в список прокси Telegram |
| `.kupu del` | Удалить **нерабочие** из списка прокси Telegram |
| `.kupu list` / `.kupu top` | Краткий список |
| `.kupu use 1` | Подключить прокси №N |
| `.kupu update` | Обновить плагин с GitHub |
| `.kupu menu` | Диалог |

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
- Подключение через SharedConfig + `ConnectionsManager.setProxySettings`.
- Кнопка на экране прокси: hook `ProxyListActivity` (header / overlay). Если кнопки нет — используйте `.kupu auto`.

## Android-приложение

Тот же проект: [Kirillka645/KupuProxy](https://github.com/Kirillka645/KupuProxy)

## Лицензия

MIT
