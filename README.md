# ChatSync

**Multifunctional chat plugin for Minecraft (Paper 1.21.x – 26.2)** · **v1.7**

Global & local chat, **teams with per-team chat prefixes**, private messages, statistics, playtime, broadcasts, vanish-aware join/quit, clickable names, full localization (**en / ru / de / fr**).

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
  <img src="https://img.shields.io/badge/Paper-1.21%E2%80%9326.2-blue?logo=minecraft" alt="Paper">
  <img src="https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk" alt="Java">
</p>

---

## English

### How teams work

You can create **many teams** on the server (limit: `teams.max_teams`).  
Each team has its **own chat symbol**, for example:

| Team | Symbol | How to write in team chat |
|------|--------|---------------------------|
| Alpha | `#` | `#hello everyone` |
| Beta | `$` | `$need help` |
| Gamma | `~` | `~raid at 8` |

- Only **members** of that team can use its symbol.
- Symbol is assigned automatically on create (from `default_symbol` / `symbol_pool`).
- Owner/co-owner can change it: `/team symbol $`
- You can still use `/team chat <message>` (uses **your** team).
- Player is in **one** team at a time (leave to join another).

### Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/chatsync reload` | Reload config and language files | `chatsync.admin` |
| `/msg <player> <text>` | Private message | *(default true)* |
| `/reply <text>` / `/r` | Reply to last PM | *(default true)* |
| `/ignore <player>` | Toggle ignore | *(default true)* |
| `/ignorelist` | List ignored players | *(default true)* |
| `/socialspy` | Spy on PMs / local chat | `chatsync.spy` |
| `/me <action>` | Roleplay line | `chatsync.me` |
| `/clear [player]` | Clear chat (with confirm) | `chatsync.clear` |
| `/chatstats [player\|top]` | Chat statistics | `chatsync.chatstats` |
| `/chatstats reset <player\|all>` | Reset stats | `chatsync.chatstats.reset` |
| `/playtime [player]` | Playtime | `chatsync.playtime` |
| `/playtimetop` | Playtime leaderboard | `chatsync.playtimetop` |
| `/lastseen <player>` | Last online | `chatsync.lastseen` |
| `/broadcast <msg\|preset>` | Server announcement | `chatsync.broadcast` |
| `/broadcast -h …` / `hide` | Announcement without author | `chatsync.broadcast` |
| `/broadcast preset …` | Manage presets | `chatsync.broadcast.preset` |
| **`/team create <name>`** | Create a team (+ auto chat symbol) | `chatsync.team` + `chatsync.team.create` |
| **`/team invite <player>`** | Invite (clickable **Accept** / **Deny**) | `chatsync.team` (leader) |
| **`/team accept`** / **`deny`** | Accept or deny invite | `chatsync.team` |
| **`/team leave`** | Leave team | `chatsync.team` |
| **`/team kick <player>`** | Kick member | `chatsync.team` (leader) |
| **`/team disband`** | Delete team | `chatsync.team` (owner) |
| **`/team chat <text>`** | Message to your team | `chatsync.team` |
| **`#text` / `$text` / …** | Same, using **that team’s** symbol | member of that team |
| **`/team name <new>`** | Rename team | leader |
| **`/team color <code>`** | Set color (`&c`, `&c&l`, …) | leader |
| **`/team symbol <char>`** | Set unique chat prefix (`#` `$` `~`) | leader |
| **`/team info`** | Name, symbol, members (`*` owner, `+` co-owner) | member |
| **`/team transfer <player>`** | Give primary ownership | owner |
| **`/team promote <player>`** | Add co-owner | owner |
| **`/team demote <player>`** | Remove co-owner | owner |

Aliases: `/party`, `/squad` → `/team`

### Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `chatsync.admin` | op | `/chatsync reload` |
| `chatsync.bypass_cooldown` | op | Bypass global/local cooldown |
| `chatsync.spy` | op | Socialspy |
| `chatsync.color` | op | Use `&` color codes in chat / me / broadcast |
| `chatsync.me` | true | `/me` |
| `chatsync.clear` | op | `/clear` |
| `chatsync.chatstats` | true | View stats |
| `chatsync.chatstats.others` | op | `/chatstats <player>` |
| `chatsync.chatstats.reset` | op | Reset stats |
| `chatsync.broadcast` | op | `/broadcast` |
| `chatsync.broadcast.preset` | op | Manage presets |
| `chatsync.playtime` | true | `/playtime` |
| `chatsync.playtimetop` | true | `/playtimetop` |
| `chatsync.lastseen` | true | `/lastseen` |
| `chatsync.spam.notify` | op | Anti-spam staff alerts |
| `chatsync.spam.bypass` | op | Bypass spam detection |
| `chatsync.team` | true | Use `/team` |
| `chatsync.team.create` | true | Create teams |
| `chatsync.team.admin` | op | Admin bypass |

### PlaceholderAPI

| Placeholder | Description |
|-------------|-------------|
| `%chatsync_playtime%` | Formatted playtime |
| `%chatsync_playtime_seconds%` | Playtime in seconds |
| `%chatsync_messages_total%` | All counted messages |
| `%chatsync_messages_global%` | Global chat count |
| `%chatsync_messages_local%` | Local chat count |
| `%chatsync_messages_pm%` | Private messages |
| `%chatsync_messages_me%` | `/me` count |
| `%chatsync_messages_broadcast%` | Broadcasts sent |
| `%chatsync_team%` / `%chatsync_team_name%` | Team name (or empty) |
| `%chatsync_team_symbol%` | Team chat prefix (`#`, `$`, …) |
| `%chatsync_team_color%` | Team color codes |
| `%chatsync_team_owner%` | Owner name |
| `%chatsync_team_size%` | Member count |
| `%chatsync_team_members%` | Member list |
| `%chatsync_in_team%` | `yes` / `no` |
| `%chatsync_team_is_owner%` | `yes` / `no` |
| `%chatsync_team_is_leader%` | Owner or co-owner: `yes` / `no` |

### Config (teams & vanish)

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
  default_symbol: "#"
  symbol_pool: "#$~@%^*"    # auto-pick if # is taken
  default_color: "&b"
  allow_color_change: true
  format: "&8[%color%%team%&8] &f%player%&7: &f%message%"
  persist: true
```

### Screenshots

<p align="center">
  <img src="screenshots/en-chat.png" alt="">
  <img src="screenshots/en-pm.png" alt="">
  <img src="screenshots/en-broadcast.png" alt="">
  <img src="screenshots/en-hover.png" alt="">
</p>

### Build

JDK **21+**. `mvn clean package` → `target/chatsync-1.7.jar`  
Runs on Paper **1.21.x – 26.2**.

### Support

Discord: [discord.gg/zQevSujnbe](https://discord.gg/zQevSujnbe)

---

## Русский

### Как работают команды (teams)

На сервере можно создать **много команд** (`teams.max_teams`).  
У **каждой** свой **символ чата**:

| Команда | Символ | Как писать |
|---------|--------|------------|
| Alpha | `#` | `#привет` |
| Beta | `$` | `$нужна помощь` |
| Gamma | `~` | `~рейд в 8` |

- Писать символом могут только **участники** этой команды.
- Символ выдаётся при создании; сменить: `/team symbol $`
- Либо `/team chat <текст>` (ваша команда).
- Игрок состоит **в одной** команде одновременно.

### Команды

| Команда | Описание | Право |
|---------|----------|-------|
| `/chatsync reload` | Перезагрузка | `chatsync.admin` |
| `/msg` `/reply` | ЛС | по умолчанию |
| `/ignore` `/ignorelist` | Игнор | по умолчанию |
| `/socialspy` | Слежка | `chatsync.spy` |
| `/me` | Роль | `chatsync.me` |
| `/clear` | Очистка чата | `chatsync.clear` |
| `/chatstats` | Статистика | `chatsync.chatstats` |
| `/playtime` `/playtimetop` `/lastseen` | Онлайн | `chatsync.playtime` … |
| `/broadcast` | Объявления | `chatsync.broadcast` |
| `/team create <имя>` | Создать (+ символ чата) | `chatsync.team.create` |
| `/team invite` | Пригласить (кнопки) | лидер |
| `/team accept` / `deny` | Принять / отклонить | |
| `/team leave` / `kick` / `disband` | Выйти / кик / распуск | |
| `/team chat` или `#текст` | Чат команды | участник |
| `/team name` / `color` / `symbol` | Имя, цвет, **свой символ** | лидер |
| `/team info` | Инфо (`*` владелец, `+` совладелец) | |
| `/team transfer` / `promote` / `demote` | Владение / совладельцы | владелец |

### Права и плейсхолдеры

См. английские таблицы выше — те же ключи (`chatsync.team`, `%chatsync_team_symbol%` и т.д.).

### Скриншоты

<p align="center">
  <img src="screenshots/ru-chat.png" alt="">
  <img src="screenshots/ru-pm.png" alt="">
  <img src="screenshots/ru-broadcast.png" alt="">
  <img src="screenshots/ru-hover.png" alt="">
</p>

### Сборка

**JDK 21+**. `mvn clean package` → `chatsync-1.7.jar`

### Поддержка

Discord: [discord.gg/zQevSujnbe](https://discord.gg/zQevSujnbe)

---

*ChatSync v1.7 · [github.com/Mutya660/ChatSync](https://github.com/Mutya660/ChatSync)*

<sub>This plugin was developed with the help of AI. / Плагин сделан с помощью ИИ.</sub>
