# ChatSync 🌐

**Многофункциональный плагин чата для Minecraft (Paper / Spigot 1.21)**

Глобальный и локальный чат, личные сообщения, игнор, socialspy, `/me`, очистка чата с подтверждением, статистика, playtime, объявления, перевод сообщений о смерти, **кликабельные ники** (чат, смерть, достижения, топы) и полная локализация (ru / en / de / fr).

[![Paper](https://img.shields.io/badge/Paper-1.21-blue?logo=minecraft)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://openjdk.org/)

**Modrinth:** [modrinth.com/plugin/chatsync](https://modrinth.com/plugin/chatsync)  
**Репозиторий:** [github.com/Mutya660/ChatSync](https://github.com/Mutya660/ChatSync)

> Цвета в сообщениях: только `&a` `&c` `&7` `&8` `&f` `&e` и при необходимости `&l`.

---

## 📌 Основные возможности

| Возможность | Описание |
| :--- | :--- |
| **Глобальный / локальный чат** | Префикс `!` → глобальный чат; иначе локальный с радиусом. Кулдаун, `chatsync.bypass_cooldown`. Ники кликабельны (`/msg`). |
| **Многоязычность** | `ru`, `en`, `de`, `fr` по локали клиента. |
| **Перевод событий** | Сообщения о смерти и названия сущностей; ники в death-сообщениях кликабельны. |
| **Достижения** | Ник в анонсе достижения кликабелен → `/msg`. |
| **Личные сообщения** | `/msg`, `/reply` (+ алиасы), звук, кликабельные ники. |
| **Ролевой чат** | `/me` с цветами (`chatsync.color`). |
| **Очистка чата** | `/clear [игрок]` с кликабельным подтверждением. |
| **Статистика чата** | `/chatstats` — топ и детальная статистика; ники в топе кликабельны. |
| **Время игры** | `/playtime`, `/playtimetop`, `/lastseen` — онлайн, топ, last seen; ники кликабельны. |
| **Объявления** | `/broadcast` — чат, action bar, title, звук. Пресеты из `broadcast.presets` без подтверждения (`/broadcast restart_5m`). |
| **Сброс статистики** | `/chatstats reset <игрок>` и `/chatstats reset all` (с подтверждением). |
| **PlaceholderAPI** | Свои плейсхолдеры: `%chatsync_playtime%`, `%chatsync_messages_total%` и др. для TAB/scoreboard. |
| **Антиспам-алерты** | Стаффу с `chatsync.spam.notify` — уведомления о повторах, КАПСе и флуде (чат, /me, ЛС). |
| **Асинхронные логи** | `logs/chat-YYYY-MM-DD.log` без нагрузки на основной поток. |
| **Модерация** | `/ignore`, `/ignorelist`, `/socialspy`. |
| **Интеграции** | LuckPerms, CoreProtect, PlaceholderAPI, DiscordSRV (soft-depend). |

---

## ⚙️ Команды и права

| Команда | Описание | Право |
| :--- | :--- | :--- |
| `/chatsync reload` | Перезагрузка конфига и языков | `chatsync.admin` |
| `/msg <игрок> <текст>` | Личное сообщение | — |
| `/reply <текст>` | Ответ на последнее ЛС | — |
| `/ignore <игрок>` | Игнор вкл/выкл | — |
| `/ignorelist` | Список игнора | — |
| `/socialspy` | Прослушка ЛС | `chatsync.spy` |
| `/me <действие>` | От третьего лица | `chatsync.me` |
| `/clear [игрок]` | Очистка чата (с подтверждением) | `chatsync.clear` |
| `/chatstats [игрок]` | Топ / статистика | `chatsync.chatstats` / `.others` |
| `/chatstats reset <игрок\|all>` | Сброс статистики (all — с подтверждением) | `chatsync.chatstats.reset` |
| `/broadcast <текст\|пресет>` | Объявление или пресет из config | `chatsync.broadcast` |
| `/playtime [игрок]` | Время игры | `chatsync.playtime` |
| `/playtimetop` | Топ по времени игры | `chatsync.playtimetop` |
| `/lastseen <игрок>` | Когда был онлайн | `chatsync.lastseen` |

Дополнительно: `chatsync.color`, `chatsync.bypass_cooldown`, `chatsync.spam.notify`, `chatsync.spam.bypass`.

### Кликабельные ники

Клик по нику в чате, join/quit, death, **достижениях**, `/chatstats`, `/playtime`, `/playtimetop`, `/lastseen` подставляет в чат:

```text
/msg <ник> 
```

(можно сразу дописать сообщение).

Отключение: `toggles.clickable_death_name` / `toggles.clickable_advancement_name` в `config.yml`.

### `/clear`

1. `/clear` — всем, `/clear <игрок>` — одному.
2. Кликабельное подтверждение (таймаут `clear.confirm_timeout`).
3. Клик или `/clear confirm`.

### Playtime (как у TAB)

Источник — **ванильная статистика** Minecraft `PLAY_ONE_MINUTE` (та же, что `%statistic_hours_played%` в TAB / PlaceholderAPI).

- Онлайн: живое значение из статистики сервера.
- Офлайн / топ: кэш в `playtime.yml` (обновляется при выходе и по таймеру).
- Формат: **часы + минуты** (например `12ч 34м`), при днях — `2д 5ч 12м`.
- Выключить: `playtime.enabled: false`.

### `/broadcast`, автор и пресеты

Формат по умолчанию показывает **автора** (`%sender%`):

```yaml
broadcast:
  format: "&e&l[Объявление] &8(&7%sender%&8) &e%message%"
  show_sender: true
```

| Команда | Действие |
| :--- | :--- |
| `/broadcast <текст>` | Обычное объявление с автором |
| `/broadcast -h <текст>` | Объявление **без** автора (разово) |
| `/broadcast hide` | Toggle: всегда скрывать свой автор |
| `/broadcast <пресет>` | Готовое объявление из config |
| `/broadcast preset list` | Список пресетов |
| `/broadcast preset set <ключ> <текст>` | Создать/обновить пресет **в игре** |
| `/broadcast preset remove <ключ>` | Удалить пресет |

Права: `chatsync.broadcast`, `chatsync.broadcast.preset`.

```text
/broadcast preset set restart_5m &c&lРестарт через 5 минут!
/broadcast restart_5m
```

### `/chatstats reset`

| Команда | Действие |
| :--- | :--- |
| `/chatstats reset <игрок>` | Сброс статистики одного игрока (сразу) |
| `/chatstats reset all` | Запрос подтверждения |
| `/chatstats reset all confirm` | Сброс **всей** статистики |

Право: `chatsync.chatstats.reset`. Таймаут подтверждения: `stats.reset_confirm_timeout` (по умолчанию 15 сек).

### PlaceholderAPI

При установленном PlaceholderAPI плагин регистрирует expansion `chatsync`. Плейсхолдеры можно использовать в **TAB**, scoreboard, hologram, DeluxeMenus и т.п.

| Плейсхолдер | Описание |
| :--- | :--- |
| `%chatsync_playtime%` | Время игры (форматированное, напр. `12h 34m`) |
| `%chatsync_playtime_seconds%` | Время игры в секундах (число) |
| `%chatsync_messages_total%` | Всего сообщений (global + local + pm + me) |
| `%chatsync_messages_global%` | Сообщения в глобальном чате |
| `%chatsync_messages_local%` | Сообщения в локальном чате |
| `%chatsync_messages_pm%` | Личные сообщения |
| `%chatsync_messages_me%` | Сообщения `/me` |
| `%chatsync_messages_broadcast%` | Объявления `/broadcast` |

Алиасы: `%chatsync_messages%` = total, `%chatsync_global%` / `%chatsync_local%` / `%chatsync_pm%` / `%chatsync_me%` / `%chatsync_broadcast%`.

Пример в TAB (scoreboard line):

```text
&7Сообщений: &e%chatsync_messages_total%
&7Онлайн: &a%chatsync_playtime%
```

### Антиспам-алерты

Не блокирует сообщения — только уведомляет стафф в чат.

| Право | Назначение |
| :--- | :--- |
| `chatsync.spam.notify` | Получать алерты о спаме |
| `chatsync.spam.bypass` | Не попадать под детекцию |

Отслеживаются каналы: **глобальный / локальный чат**, `/me`, **ЛС**.

Типы срабатывания:

| Тип | Условие (настраивается в `spam.notify`) |
| :--- | :--- |
| **same** | Одно и то же сообщение ≥ `same_message_limit` раз за окно |
| **caps** | Доля ЗАГЛАВНЫХ ≥ `caps_ratio` при длине ≥ `caps_min_length` |
| **flood** | ≥ `flood_limit` любых сообщений за `window_seconds` |

Пример алерта:

```text
[SPAM] Nick повторяет одно сообщение (локальный чат): текст…
```

Конфиг по умолчанию:

```yaml
spam:
  notify:
    enabled: true
    permission: "chatsync.spam.notify"
    window_seconds: 10
    same_message_limit: 3
    flood_limit: 6
    caps_ratio: 0.7
    caps_min_length: 6
```

---

## 🚀 Установка

1. Соберите jar (`mvn clean package`) или скачайте релиз.
2. Положите в `plugins/` (Paper / Spigot **1.21**).
3. Перезапустите сервер.
4. Настройте `config.yml` и `lang/*.yml`.
5. Опционально: LuckPerms, CoreProtect, PlaceholderAPI, DiscordSRV.

---

## 🛠️ Сборка

```bash
git clone https://github.com/Mutya660/ChatSync.git
cd ChatSync
mvn clean package
# → target/*.jar
```

**Java 17+**, Maven.

---

## 📁 Данные плагина

```
plugins/ChatSync/
├── config.yml
├── lang/          (en, ru, de, fr)
├── death_messages_ru.json
├── entity_names_ru.json
├── stats.yml
├── playtime.yml
└── logs/chat-YYYY-MM-DD.log
```

---

## 🎨 Палитра цветов

Во всех сообщениях плагина используются только:

| Код | Назначение |
| --- | --- |
| `&a` | успех / позитив |
| `&c` | ошибка |
| `&e` | акцент / заголовки |
| `&f` | основной текст / ники |
| `&7` | вторичный текст |
| `&8` | приглушённый / разделители |
| `&l` | жирный (заголовки) |

---

*ChatSync v1.4 · Paper 1.21 · Java 17+*
