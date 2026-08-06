# ChatSync 🌐

**Многофункциональный плагин чата для Minecraft (Paper / Spigot 1.21)**

Глобальный и локальный чат, личные сообщения, игнор, socialspy, `/me`, очистка чата с подтверждением, статистика, объявления, перевод сообщений о смерти, полная локализация интерфейса (ru / en / de / fr) и асинхронные логи.

[![Paper](https://img.shields.io/badge/Paper-1.21-blue?logo=minecraft)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-lightgrey)](https://github.com/Mutya660/ChatSync)

**Modrinth:** [modrinth.com/plugin/chatsync](https://modrinth.com/plugin/chatsync)  
**Репозиторий:** [github.com/Mutya660/ChatSync](https://github.com/Mutya660/ChatSync)

---

## 📌 Основные возможности

| Возможность | Описание |
| :--- | :--- |
| **Глобальный / локальный чат** | Сообщения с префиксом (по умолчанию `!`) идут в глобальный чат, остальные — в локальный с настраиваемым радиусом. Кулдаун глобального чата, обход по праву `chatsync.bypass_cooldown`. |
| **Многоязычность** | Встроенная поддержка `ru`, `en`, `de`, `fr`. Язык выбирается по локали клиента; fallback — `language` из `config.yml`. |
| **Перевод событий** | Автоматический перевод сообщений о смерти (`death_messages_ru.json`) и названий сущностей (`entity_names_ru.json`). |
| **Личные сообщения** | `/msg`, `/reply` с алиасами (`m`, `tell`, `t`, `w`, `r`), звук, форматы отправителя/получателя. |
| **Ролевой чат** | `/me` — сообщения от третьего лица с поддержкой цветов (`chatsync.color`). |
| **Очистка чата** | `/clear [игрок]` с кликабельным подтверждением и таймаутом. |
| **Статистика чата** | `/chatstats` — топ и подробная статистика (глобальный / локальный / ЛС / `/me` / всего). |
| **Объявления** | `/broadcast` — чат, action bar, title, звук (настраивается в `config.yml`). |
| **Асинхронные логи** | Глобальный, локальный, ЛС, `/me`, broadcast → `logs/chat-YYYY-MM-DD.log` без нагрузки на основной поток. |
| **Модерация** | `/ignore`, `/ignorelist`, `/socialspy` (право `chatsync.spy`). |
| **Интеграции (soft-depend)** | **LuckPerms** — префикс/суффикс через API, если нет PlaceholderAPI<br>**CoreProtect** — журналирование `/clear` для `/co lookup`<br>**PlaceholderAPI**, **DiscordSRV** |

Все форматы, права и переключатели задаются в `config.yml` (двуязычные комментарии `[EN]` / `[RU]`). Тексты для игроков — в `lang/<код>.yml`.

---

## ⚙️ Команды и права

| Команда | Описание | Право (по умолчанию) |
| :--- | :--- | :--- |
| `/chatsync reload` | Перезагрузка конфигурации и языков | `chatsync.admin` |
| `/msg <игрок> <текст>` | Личное сообщение | — |
| `/reply <текст>` | Ответ на последнее ЛС | — |
| `/ignore <игрок>` | Добавить/убрать из игнора | — |
| `/ignorelist` | Список игнорируемых | — |
| `/socialspy` | Режим прослушки ЛС (и локального чата) | `chatsync.spy` |
| `/me <действие>` | Сообщение от третьего лица | `chatsync.me` |
| `/clear [игрок]` | Очистить чат (с подтверждением) | `chatsync.clear` |
| `/chatstats [игрок]` | Топ или статистика игрока | `chatsync.chatstats` / `chatsync.chatstats.others` |
| `/broadcast <текст>` | Серверное объявление | `chatsync.broadcast` |

Дополнительно:
- `chatsync.color` — использование `&`-кодов в чате, `/me` и `/broadcast`
- `chatsync.bypass_cooldown` — обход кулдауна глобального чата

Права и форматы можно переопределить в `config.yml` без пересборки.

### Как работает `/clear`

1. `/clear` — очистить чат **всем**, или `/clear <игрок>` — только указанному игроку.
2. Плагин отправляет кликабельное сообщение-подтверждение (таймаут: `clear.confirm_timeout` в `config.yml`).
3. Клик по сообщению или `/clear confirm` (или `/clear <игрок> confirm`) выполняет очистку.

### Как работает `/chatstats`

- `/chatstats` — топ активных игроков (размер: `stats.top_size`).
- `/chatstats <игрок>` — детальная статистика. Чужую статистику смотрят с правом `chatsync.chatstats.others`.

### Глобальный и локальный чат

- По умолчанию сообщение, начинающееся с `!`, уходит в **глобальный** чат; без символа — в **локальный** (радиус из `chat.local.radius`).
- Символ, требование символа, форматы, кулдаун и плейсхолдеры (`%player%`, `%message%`, `%luckperms_prefix%`, `%luckperms_suffix%`) настраиваются в секции `chat` файла `config.yml`.

---

## 🚀 Установка

1. Скачайте `.jar` (релиз или сборка из исходников).
2. Положите файл в папку `plugins` сервера (**Paper** / Spigot, API 1.21).
3. Запустите или перезапустите сервер — создадутся `config.yml`, `lang/` и остальные файлы.
4. Отредактируйте `config.yml` и нужные `lang/*.yml`.
5. *(Опционально)* Установите LuckPerms, CoreProtect, PlaceholderAPI или DiscordSRV — плагин подхватит их автоматически.

---

## 🛠️ Сборка из исходников

Требуются **Java 17+** и **Maven**:

```bash
git clone https://github.com/Mutya660/ChatSync.git
cd ChatSync
mvn clean package
```

Готовый jar появится в `target/` (имя вида `ChatSync-1.1.jar` или аналогичное, в зависимости от `pom.xml`).

---

## 📁 Структура данных плагина

После первого запуска в папке плагина:

```
plugins/ChatSync/
├── config.yml          # основные настройки (форматы, права, toggles)
├── lang/
│   ├── en.yml
│   ├── ru.yml
│   ├── de.yml
│   └── fr.yml
├── death_messages_ru.json
├── entity_names_ru.json
└── logs/
    └── chat-YYYY-MM-DD.log
```

---

## 🔗 Полезные ссылки

- [Modrinth](https://modrinth.com/plugin/chatsync)
- [GitHub](https://github.com/Mutya660/ChatSync)
- [Issues](https://github.com/Mutya660/ChatSync/issues)

---

*ChatSync v1.1 · Paper 1.21 · Java 17+*
