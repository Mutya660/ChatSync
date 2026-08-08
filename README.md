# ChatSync

**Multifunctional chat plugin for Minecraft (Paper 1.21.x – 26.2)** · **v1.7**

Global & local chat, private messages, ignore, socialspy, `/me`, clear chat, statistics, playtime, broadcasts, death-message translation, clickable names, vanish-aware join/quit, full localization (**en / ru / de / fr**).

<p align="center">
  <a href="https://modrinth.com/plugin/chatsync"><img src="https://img.shields.io/badge/Available_on-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth"></a>
  <a href="https://www.spigotmc.org/resources/chatsync.137778/"><img src="https://img.shields.io/badge/Available_on-SpigotMC-ED8106?style=for-the-badge&logo=spigotmc&logoColor=white" alt="SpigotMC"></a>
  <a href="https://www.curseforge.com/minecraft/bukkit-plugins/chatsyns"><img src="https://img.shields.io/badge/Available_on-CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white" alt="CurseForge"></a>
</p>
<p align="center">
  <a href="https://papermc.io/"><img src="https://img.shields.io/badge/Available_for-Paper-1B1B1B?style=for-the-badge&logo=minecraft&logoColor=white" alt="Paper"></a>
  <a href="https://discord.gg/zQevSujnbe"><img src="https://img.shields.io/badge/Chat_with_me_on-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
  <a href="https://boosty.to/mutya660"><img src="https://img.shields.io/badge/Support_me_on-Boosty-F15F2C?style=for-the-badge" alt="Boosty"></a>
</p>
<p align="center">
  <a href="https://github.com/Mutya660/ChatSync"><img src="https://img.shields.io/badge/Available_on-GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub"></a>
</p>
<p align="center">
  <img src="https://img.shields.io/badge/Paper-1.21%E2%80%9326.2-blue?logo=minecraft" alt="Paper">
  <img src="https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk" alt="Java">
</p>

---

## English

### Features

| Feature | Description |
| :--- | :--- |
| **Global / local chat** | Prefix `!` → global; otherwise local with radius. Cooldown & slowmode. Clickable names. |
| **Localization** | `en`, `ru`, `de`, `fr`. `auto_language: true` follows the client language. |
| **Death messages** | Optional RU pack (Minecraft 26.2). Clickable names. |
| **Private messages** | `/msg`, `/reply`. |
| **Roleplay** | `/me`. |
| **Clear chat** | `/clear` with confirmation. |
| **Chat stats** | `/chatstats`, reset. |
| **Playtime** | `/playtime`, `/playtimetop`, `/lastseen`. |
| **Broadcasts** | `/broadcast`, presets, hide author. |
| **Vanish** | Hide join/quit when vanished. |
| **Anti-spam** | Staff alerts. |
| **Integrations** | LuckPerms, CoreProtect, PlaceholderAPI, DiscordSRV, LiteBans, SuperVanish / PremiumVanish / Essentials. |

### Screenshots

<p align="center">
  <img src="screenshots/en-chat.png" alt="">
  <img src="screenshots/en-pm.png" alt="">
  <img src="screenshots/en-broadcast.png" alt="">
  <img src="screenshots/en-hover.png" alt="">
</p>

### Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/chatsync reload` | Reload config & languages | `chatsync.admin` |
| `/msg <player> <text>` | Private message | *(default true)* |
| `/reply <text>` | Reply to last PM | *(default true)* |
| `/ignore <player>` | Toggle ignore | *(default true)* |
| `/ignorelist` | List ignored players | *(default true)* |
| `/socialspy` | Spy on PMs / local chat | `chatsync.spy` |
| `/me <action>` | Roleplay message | `chatsync.me` |
| `/clear [player]` | Clear chat (confirm) | `chatsync.clear` |
| `/chatstats [player\|top]` | Chat statistics | `chatsync.chatstats` |
| `/chatstats reset <player\|all>` | Reset stats | `chatsync.chatstats.reset` |
| `/playtime [player]` | Playtime | `chatsync.playtime` |
| `/playtimetop` | Playtime leaderboard | `chatsync.playtimetop` |
| `/lastseen <player>` | Last online | `chatsync.lastseen` |
| `/broadcast <msg\|preset>` | Announcement | `chatsync.broadcast` |
| `/broadcast -h` / `hide` | Announcement without author | `chatsync.broadcast` |
| `/broadcast preset …` | Manage presets | `chatsync.broadcast.preset` |

#### Team commands (`/team`, aliases: `/party`, `/squad`)

A player can be in **only one team** at a time. To join another team, leave the current one first.

Each team has its **own chat symbol** (e.g. `#`, `$`, `~`). Only members can write with that symbol.

| Command | Description | Who |
| :--- | :--- | :--- |
| `/team create <name>` | Create a team (auto chat symbol) | `chatsync.team.create` |
| `/team invite <player>` | Invite (clickable Accept / Deny) | owner / co-owner |
| `/team accept` / `deny` | Accept or deny invite | invited player |
| `/team leave` | Leave the team | member |
| `/team kick <player>` | Kick a member | owner / co-owner |
| `/team disband` | Delete the team | owner |
| `/team chat <text>` | Message to your team | member |
| `#text` (or your team’s symbol) | Same as `/team chat` | member |
| `/team name <new>` | Rename | owner / co-owner |
| `/team color <code>` | Color (`&c`, `&c&l`, …) | owner / co-owner |
| `/team symbol <char>` | Unique chat prefix | owner / co-owner |
| `/team info` | Info (`*` owner, `+` co-owner) | member |
| `/team transfer <player>` | Transfer ownership | owner |
| `/team promote <player>` | Add co-owner | owner |
| `/team demote <player>` | Remove co-owner | owner |

### Permissions

| Permission | Default | Description |
| :--- | :--- | :--- |
| `chatsync.admin` | op | Reload |
| `chatsync.bypass_cooldown` | op | Bypass chat cooldown |
| `chatsync.spy` | op | Socialspy |
| `chatsync.color` | op | `&` colors in chat |
| `chatsync.me` | true | `/me` |
| `chatsync.clear` | op | `/clear` |
| `chatsync.chatstats` | true | View stats |
| `chatsync.chatstats.others` | op | Other players’ stats |
| `chatsync.chatstats.reset` | op | Reset stats |
| `chatsync.broadcast` | op | Broadcast |
| `chatsync.broadcast.preset` | op | Presets |
| `chatsync.playtime` | true | Playtime |
| `chatsync.playtimetop` | true | Leaderboard |
| `chatsync.lastseen` | true | Last seen |
| `chatsync.spam.notify` | op | Spam alerts |
| `chatsync.spam.bypass` | op | Bypass spam check |
| `chatsync.team` | true | Use `/team` |
| `chatsync.team.create` | true | Create a team |
| `chatsync.team.admin` | op | Admin bypass |

### PlaceholderAPI

| Placeholder | Description |
| :--- | :--- |
| `%chatsync_playtime%` | Formatted playtime |
| `%chatsync_playtime_seconds%` | Seconds |
| `%chatsync_messages_total%` | Total messages |
| `%chatsync_messages_global%` | Global |
| `%chatsync_messages_local%` | Local |
| `%chatsync_messages_pm%` | PMs |
| `%chatsync_messages_me%` | `/me` |
| `%chatsync_messages_broadcast%` | Broadcasts |
| `%chatsync_team%` | Team name |
| `%chatsync_team_symbol%` | Chat symbol (`#`, `$`, …) |
| `%chatsync_team_color%` | Color codes |
| `%chatsync_team_owner%` | Owner name |
| `%chatsync_team_size%` | Members count |
| `%chatsync_team_members%` | Member list |
| `%chatsync_in_team%` | `yes` / `no` |
| `%chatsync_team_is_owner%` | `yes` / `no` |
| `%chatsync_team_is_leader%` | Owner or co-owner |

### Quick setup

```yaml
language: "en"
auto_language: true

vanish:
  hide_join_quit: true

teams:
  enabled: true
  max_members: 8
  max_co_owners: 3
  default_symbol: "#"
  symbol_pool: "#$~@%^*"
  format: "&8[%color%%team%&8] &f%player%&7: &f%message%"
```

### Build

**JDK 21+** · `mvn clean package` → `target/chatsync-1.7.jar` · Paper **1.21.x – 26.2**

### Support

Discord: [discord.gg/zQevSujnbe](https://discord.gg/zQevSujnbe)

---

## Русский

### Возможности

| Возможность | Описание |
| :--- | :--- |
| **Глобальный / локальный чат** | `!` → глобал; иначе локал. Кулдаун, slowmode, кликабельные ники. |
| **Языки** | `en`, `ru`, `de`, `fr`. `auto_language: true` — по клиенту. |
| **Смерти / достижения** | Перевод, кликабельные ники. |
| **ЛС, /me, очистка, статистика, playtime** | Полный набор. |
| **Объявления** | `/broadcast`, пресеты, скрытие автора. |
| **Ваниш** | Без join/quit. |
| **Интеграции** | LuckPerms, CoreProtect, PAPI, DiscordSRV, LiteBans, ваниш-плагины. |

### Скриншоты

<p align="center">
  <img src="screenshots/ru-chat.png" alt="">
  <img src="screenshots/ru-pm.png" alt="">
  <img src="screenshots/ru-broadcast.png" alt="">
  <img src="screenshots/ru-hover.png" alt="">
</p>

### Команды

Основные: `/chatsync reload`, `/msg`, `/reply`, `/ignore`, `/socialspy`, `/me`, `/clear`, `/chatstats`, `/playtime`, `/playtimetop`, `/lastseen`, `/broadcast` — см. таблицу в English.

#### Команды `/team` (алиасы: `/party`, `/squad`)

Игрок может состоять **только в одной команде**. Чтобы вступить в другую — сначала `/team leave`.

У каждой команды свой **символ чата** (`#`, `$`, `~`…): писать им могут только участники.

| Команда | Описание |
| :--- | :--- |
| `/team create <имя>` | Создать команду (свой символ чата) |
| `/team invite <игрок>` | Пригласить (кнопки Принять / Отклонить) |
| `/team accept` / `deny` | Принять / отклонить |
| `/team leave` | Выйти |
| `/team kick <игрок>` | Исключить |
| `/team disband` | Распустить |
| `/team chat <текст>` или `#текст` | Написать в чат команды |
| `/team name` / `color` / `symbol` | Имя, цвет, символ чата |
| `/team info` | Информация (`*` владелец, `+` совладелец) |
| `/team transfer` / `promote` / `demote` | Передать владение / совладельцы |

Права: `chatsync.team`, `chatsync.team.create`, `chatsync.team.admin`.  
Плейсхолдеры: `%chatsync_team%`, `%chatsync_team_symbol%`, `%chatsync_in_team%` и др. — см. English.

### Сборка

**JDK 21+** · `mvn clean package` → `chatsync-1.7.jar`

### Поддержка

Discord: [discord.gg/zQevSujnbe](https://discord.gg/zQevSujnbe)

---

*ChatSync v1.7*

<sub>This plugin was developed with the help of AI. / Плагин сделан с помощью ИИ.</sub>
