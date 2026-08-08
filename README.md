# ChatSync

**Multifunctional chat plugin for Minecraft (Paper 1.21.x – 26.2)** · **v1.7**

Global & local chat, private messages, teams/party chat, ignore, socialspy, `/me`, clear chat, statistics, playtime, broadcasts, death-message translation, **clickable names**, vanish-aware join/quit, full localization (**en / ru / de / fr**).

<p align="center">
  <a href="https://modrinth.com/plugin/chatsync"><img src="https://img.shields.io/badge/Available_on-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth"></a>
  <a href="https://www.spigotmc.org/resources/chatsync.137778/"><img src="https://img.shields.io/badge/Available_on-SpigotMC-ED8106?style=for-the-badge&logo=spigotmc&logoColor=white" alt="SpigotMC"></a>
  <a href="https://www.curseforge.com/minecraft/bukkit-plugins/chatsyns"><img src="https://img.shields.io/badge/Available_on-CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white" alt="CurseForge"></a>
  <a href="https://github.com/Mutya660/ChatSync"><img src="https://img.shields.io/badge/Available_on-GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub"></a>
</p>
<p align="center">
  <a href="https://papermc.io/"><img src="https://img.shields.io/badge/Available_for-Paper-1B1B1B?style=for-the-badge&logo=minecraft&logoColor=white" alt="Paper"></a>
  <a href="https://discord.gg/zQevSujnbe"><img src="https://img.shields.io/badge/Chat_with_me_on-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
  <a href="https://boosty.to/mutya660"><img src="https://img.shields.io/badge/Support_me_on-Boosty-F15F2C?style=for-the-badge" alt="Boosty"></a>
</p>
<p align="center">
  <a href="https://papermc.io/"><img src="https://img.shields.io/badge/Paper-1.21%E2%80%9326.2-blue?logo=minecraft" alt="Paper versions"></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk" alt="Java"></a>
</p>

---

## English

### Features

| Feature | Description |
| :--- | :--- |
| **Global / local chat** | Prefix `!` → global; otherwise local with radius. Cooldown & slowmode. Clickable names → `/msg`. |
| **Team / party chat** | `/team` — create, invite (clickable Accept/Deny), leave, kick, disband, rename, color, **transfer ownership**, **co-owners**. Chat via `#message` or `/team chat`. |
| **Localization** | `en`, `ru`, `de`, `fr`. `auto_language: true` follows the client language. |
| **Death messages** | Optional RU pack (Minecraft 26.2 keys). Clickable names. |
| **Advancements** | Clickable player name in advancement announcements. |
| **Private messages** | `/msg`, `/reply`, sound, clickable names. |
| **Roleplay** | `/me` with colors (`chatsync.color`). |
| **Clear chat** | `/clear [player]` with confirmation. |
| **Chat stats** | `/chatstats`, `/chatstats reset <player\|all>`. |
| **Playtime** | `/playtime`, `/playtimetop`, `/lastseen`. |
| **Broadcasts** | `/broadcast`, presets, hide author (`-h` / `hide`). Layout per language in `lang/*.yml`. |
| **Vanish** | Hide join/quit when vanished (SuperVanish / PremiumVanish / Essentials). |
| **Anti-spam alerts** | Staff with `chatsync.spam.notify`. |
| **Integrations** | LuckPerms, CoreProtect, PlaceholderAPI, DiscordSRV, LiteBans (mutes), vanish plugins. |

### Screenshots

<p align="center">
  <img src="screenshots/en-chat.png" alt="">
  <img src="screenshots/en-pm.png" alt="">
  <img src="screenshots/en-broadcast.png" alt="">
  <img src="screenshots/en-hover.png" alt="">
</p>

### Commands & permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/chatsync reload` | Reload config & languages | `chatsync.admin` |
| `/msg` / `/reply` | Private messages | (default true) |
| `/ignore` / `/ignorelist` | Ignore list | (default true) |
| `/socialspy` | Spy PMs / local | `chatsync.spy` |
| `/me` | Roleplay | `chatsync.me` |
| `/clear [player]` | Clear chat | `chatsync.clear` |
| `/chatstats` / `reset` | Stats | `chatsync.chatstats` / `.reset` |
| `/playtime` / `/playtimetop` / `/lastseen` | Playtime | `chatsync.playtime` … |
| `/broadcast …` | Announcements | `chatsync.broadcast` |
| `/team …` | Team / party system | `chatsync.team` |
| `/team create` | Create team | `chatsync.team.create` |

**Team subcommands:** `create`, `invite`, `accept`, `deny`, `leave`, `kick`, `disband`, `chat`, `name`, `color`, `info`, `transfer`, `promote`, `demote`

| Permission | Default | Description |
| :--- | :--- | :--- |
| `chatsync.team` | true | Use `/team` |
| `chatsync.team.create` | true | Create teams |
| `chatsync.team.admin` | op | Admin bypass |
| `chatsync.spam.notify` | op | Spam alerts |
| `chatsync.bypass_cooldown` | op | Bypass chat cooldown |

### PlaceholderAPI

```
%chatsync_playtime%
%chatsync_playtime_seconds%
%chatsync_messages_total%
%chatsync_messages_global%
%chatsync_messages_local%
%chatsync_messages_pm%
%chatsync_messages_me%
%chatsync_messages_broadcast%
%chatsync_team% / %chatsync_team_name%
%chatsync_team_color%
%chatsync_team_owner%
%chatsync_team_size%
%chatsync_team_members%
%chatsync_in_team%
%chatsync_team_is_owner%
%chatsync_team_is_leader%
```

### Quick setup

1. Put the JAR into `plugins/`.
2. Start once, edit `plugins/ChatSync/config.yml`.
3. `/chatsync reload`.

```yaml
language: "en"
auto_language: true          # follow client language

vanish:
  hide_join_quit: true

teams:
  enabled: true
  max_teams: 50
  max_members: 8
  max_co_owners: 3
  chat_symbol: "#"
  format: "&8[%color%%team%&8] &f%player%&7: &f%message%"
```

Announcement **layout** is in `lang/<code>.yml` (`broadcast.lines` / `lines_hidden`).  
Player-facing texts are only in `lang/*.yml`.

### Build

**JDK 21+** required.

```bash
mvn clean package
# → target/chatsync-1.7.jar
```

Compiled against Paper API **1.21.4**. Same JAR runs on **1.21.x – 26.2**.

### Support

Found a bug? Discord: [discord.gg/zQevSujnbe](https://discord.gg/zQevSujnbe)

---

## Русский

**Многофункциональный плагин чата для Minecraft (Paper 1.21.x – 26.2)** · **v1.7**

Глобальный и локальный чат, команды/party, ЛС, игнор, socialspy, `/me`, очистка, статистика, playtime, объявления, перевод смертей, **кликабельные ники**, скрытие join/quit в ванише, локализация (**en / ru / de / fr**).

### Возможности

| Возможность | Описание |
| :--- | :--- |
| **Глобальный / локальный чат** | `!` → глобал; иначе локал с радиусом. Кулдаун и slowmode. |
| **Команды (team)** | `/team` — создание, приглашения с кнопками, кик, роспуск, цвет, имя, **передача владения**, **совладельцы**. Чат: `#текст` или `/team chat`. |
| **Языки** | `en`, `ru`, `de`, `fr`. `auto_language: true` — по языку клиента. |
| **Смерти** | Опциональный RU-пакет (ключи 26.2). Кликабельные ники. |
| **Достижения** | Кликабельный ник в анонсе. |
| **ЛС** | `/msg`, `/reply`. |
| **Ролевой чат** | `/me`. |
| **Очистка** | `/clear` с подтверждением. |
| **Статистика** | `/chatstats`, сброс. |
| **Время игры** | `/playtime`, `/playtimetop`, `/lastseen`. |
| **Объявления** | `/broadcast`, пресеты, скрытие автора. |
| **Ваниш** | Без join/quit в ванише (SuperVanish / PremiumVanish / Essentials). |
| **Антиспам** | Алерты стаффу. |
| **Интеграции** | LuckPerms, CoreProtect, PlaceholderAPI, DiscordSRV, LiteBans, ваниш-плагины. |

### Скриншоты

<p align="center">
  <img src="screenshots/ru-chat.png" alt="">
  <img src="screenshots/ru-pm.png" alt="">
  <img src="screenshots/ru-broadcast.png" alt="">
  <img src="screenshots/ru-hover.png" alt="">
</p>

### Команды и права

| Команда | Описание | Право |
| :--- | :--- | :--- |
| `/chatsync reload` | Перезагрузка | `chatsync.admin` |
| `/msg` / `/reply` | ЛС | (по умолчанию) |
| `/ignore` / `/ignorelist` | Игнор | (по умолчанию) |
| `/socialspy` | Просмотр ЛС | `chatsync.spy` |
| `/me` | Роль | `chatsync.me` |
| `/clear` | Очистка чата | `chatsync.clear` |
| `/chatstats` | Статистика | `chatsync.chatstats` |
| `/playtime` / `/playtimetop` / `/lastseen` | Онлайн | `chatsync.playtime` … |
| `/broadcast` | Объявления | `chatsync.broadcast` |
| `/team` | Система команд | `chatsync.team` |
| `/team create` | Создать команду | `chatsync.team.create` |

**Подкоманды team:** `create`, `invite`, `accept`, `deny`, `leave`, `kick`, `disband`, `chat`, `name`, `color`, `info`, `transfer`, `promote`, `demote`

| Право | По умолчанию |
| :--- | :--- |
| `chatsync.team` | true |
| `chatsync.team.create` | true |
| `chatsync.team.admin` | op |
| `chatsync.spam.notify` | op |

### PlaceholderAPI

```
%chatsync_playtime%
%chatsync_messages_total% …
%chatsync_team%
%chatsync_team_owner%
%chatsync_team_size%
%chatsync_in_team%
%chatsync_team_is_owner%
%chatsync_team_is_leader%
```

### Быстрая настройка

1. JAR → `plugins/`.
2. Первый запуск → `config.yml`.
3. `/chatsync reload`.

```yaml
language: "en"
auto_language: true

vanish:
  hide_join_quit: true

teams:
  enabled: true
  max_teams: 50
  max_members: 8
  max_co_owners: 3
  chat_symbol: "#"
```

Оформление объявлений — в `lang/*.yml`. Тексты игрокам — только там.

### Сборка

**JDK 21+**.

```bash
mvn clean package
# → target/chatsync-1.7.jar
```

Сборка против Paper API **1.21.4**. JAR работает на **1.21.x – 26.2**.

### Поддержка

Баги и ошибки: Discord [discord.gg/zQevSujnbe](https://discord.gg/zQevSujnbe)

---

*ChatSync v1.7 · [github.com/Mutya660/ChatSync](https://github.com/Mutya660/ChatSync)*

---

<sub>This plugin was developed with the help of AI. / Плагин сделан с помощью ИИ.</sub>
