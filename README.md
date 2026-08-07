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
| **Объявления** | `/broadcast` — чат, action bar, title, звук. |
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
| `/broadcast <текст>` | Объявление | `chatsync.broadcast` |
| `/playtime [игрок]` | Время игры | `chatsync.playtime` |
| `/playtimetop` | Топ по времени игры | `chatsync.playtimetop` |
| `/lastseen <игрок>` | Когда был онлайн | `chatsync.lastseen` |

Дополнительно: `chatsync.color`, `chatsync.bypass_cooldown`.

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

*ChatSync v1.1 · Paper 1.21 · Java 17+*
