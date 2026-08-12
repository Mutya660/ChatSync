# ChatSync

**Multifunctional chat plugin for Minecraft (Paper 1.21.x – 26.2)** · **v1.7.2**

Global & local chat · private messages · ignore · socialspy · teams · playtime · broadcasts · heads · multi-language (**en / ru / de / fr**)

<p align="center">
  <a href="https://modrinth.com/plugin/chatsync"><img src="https://img.shields.io/badge/Available_on-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth"></a>
  <a href="https://www.spigotmc.org/resources/chatsync.137778/"><img src="https://img.shields.io/badge/Available_on-SpigotMC-ED8106?style=for-the-badge&logo=spigotmc&logoColor=white" alt="SpigotMC"></a>
  <a href="https://www.curseforge.com/minecraft/bukkit-plugins/chatsync"><img src="https://img.shields.io/badge/Available_on-CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white" alt="CurseForge"></a>
</p>
<p align="center">
  <a href="https://discord.com/invite/zQevSujnbe"><img src="https://img.shields.io/badge/Chat_with_me_on-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
  <a href="https://boosty.to/mutya660/donate"><img src="https://img.shields.io/badge/Support_me_on-Boosty-F15F2C?style=for-the-badge" alt="Boosty"></a>
  <a href="https://github.com/Mutya660/ChatSync"><img src="https://img.shields.io/badge/Available_on-GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub"></a>
</p>

---

## English

### Features

| Feature | Description |
|--------|-------------|
| **Global / local chat** | `!` prefix for global; local with radius, cooldown, slowmode |
| **Formats** | `%head%`, `{username-color}`, `%luckperms_prefix%` / `%luckperms_suffix%`, hex `&#RRGGBB` |
| **Private messages** | `/msg`, `/reply`, `/msg console` |
| **Ignore** | `/ignore`, `/ignorelist` — click name to unignore |
| **SocialSpy** | PMs, local, team, `/me` — **persists after rejoin** |
| **Teams** | One team per player, symbol chat, invites, co-owners, transfer |
| **Heads** | Native (client 1.21.9+) + SkinsRestorer textures |
| **Playtime** | `/playtime`, `/playtimetop`, `/lastseen` (vanish-aware) |
| **Broadcasts** | Presets, hide author |
| **Stats** | `/chatstats` + reset |
| **Death / join-quit** | Translated deaths, clickable names, vanish hide |
| **Languages** | en, ru, de, fr + `auto_language` |
| **Integrations** | LuckPerms, PlaceholderAPI, DiscordSRV, LiteBans, CoreProtect, SuperVanish / PremiumVanish / Essentials, SkinsRestorer |

### Commands

| Command | Description | Default permission |
|---------|-------------|-------------------|
| `/msg <player\|console> <msg>` | Private message or message to console | everyone / `chatsync.msg.console` |
| `/reply <msg>` | Reply to last PM | everyone |
| `/ignore <player>` | Toggle ignore | everyone |
| `/ignorelist` | List ignored (click to unignore) | everyone |
| `/socialspy` | Toggle SocialSpy (saved) | `chatsync.spy` |
| `/me <action>` | Roleplay message | `chatsync.me` |
| `/clear [player] [confirm]` | Clear chat | `chatsync.clear` |
| `/chatstats [player]` | Chat statistics | `chatsync.chatstats` |
| `/chatstats reset <player\|all> [confirm]` | Reset stats | `chatsync.chatstats.reset` |
| `/broadcast <msg\|preset\|-h\|hide>` | Announcement | `chatsync.broadcast` |
| `/broadcast preset ...` | Manage presets | `chatsync.broadcast.preset` |
| `/playtime [player]` | Playtime | `chatsync.playtime` |
| `/playtimetop` | Playtime leaderboard | `chatsync.playtimetop` |
| `/lastseen <player>` | Last online | `chatsync.lastseen` |
| `/team create\|invite\|accept\|deny\|leave\|kick\|disband\|chat\|name\|color\|symbol\|info\|transfer\|promote\|demote` | Team system | `chatsync.team` / `chatsync.team.create` |
| `/chatsync info` | Plugin info (version, author) | everyone |
| `/chatsync reload` | Reload config & languages | `chatsync.admin` |

Aliases: `/m`, `/tell`, `/w`, `/r`, `/bc`, `/announce`, `/csync`, `/party`, …

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `chatsync.admin` | `/chatsync reload` | op |
| `chatsync.spy` | `/socialspy` | op |
| `chatsync.color` | Use `&` / hex colors in chat | op |
| `chatsync.me` | `/me` | true |
| `chatsync.clear` | `/clear` | op |
| `chatsync.chatstats` | View stats | true |
| `chatsync.chatstats.others` | View others' stats | op |
| `chatsync.chatstats.reset` | Reset stats | op |
| `chatsync.broadcast` | `/broadcast` | op |
| `chatsync.broadcast.preset` | Manage presets | op |
| `chatsync.playtime` | `/playtime` | true |
| `chatsync.playtimetop` | `/playtimetop` | true |
| `chatsync.lastseen` | `/lastseen` | true |
| `chatsync.msg.console` | `/msg console` | op |
| `chatsync.bypass_cooldown` | Bypass chat cooldown | op |
| `chatsync.spam.notify` | Spam staff alerts | op |
| `chatsync.spam.bypass` | Bypass spam detection | op |
| `chatsync.team` | Use teams | true |
| `chatsync.team.create` | Create a team | true |
| `chatsync.team.admin` | Team admin bypass | op |
| `chatsync.vanish.see` | See vanished as online in `/lastseen` | — |

### Placeholders (PlaceholderAPI)

| Placeholder | Description |
|-------------|-------------|
| `%chatsync_version%` | Plugin version |
| `%chatsync_author%` | Author (Mutya660) |
| `%chatsync_playtime%` | Formatted playtime |
| `%chatsync_playtime_seconds%` | Playtime in seconds |
| `%chatsync_messages_total%` | Total messages |
| `%chatsync_messages_global%` | Global messages |
| `%chatsync_messages_local%` | Local messages |
| `%chatsync_messages_pm%` | Private messages |
| `%chatsync_messages_me%` | `/me` count |
| `%chatsync_messages_broadcast%` | Broadcasts sent |
| `%chatsync_team%` | Team name |
| `%chatsync_team_symbol%` | Team chat symbol |
| `%chatsync_team_color%` | Team color codes |
| `%chatsync_team_owner%` | Owner name |
| `%chatsync_team_size%` | Member count |
| `%chatsync_team_members%` | Member list |
| `%chatsync_in_team%` | `yes` / `no` |
| `%chatsync_team_is_owner%` | `yes` / `no` |
| `%chatsync_team_is_leader%` | Owner or co-owner |

Chat format tokens: `%player%` `%message%` `%head%` `%head_self%` `%head_other%` `%luckperms_prefix%` `%luckperms_suffix%` `{username-color}`

### Configuration

Main file: `plugins/ChatSync/config.yml`  
Languages: `plugins/ChatSync/lang/en.yml` (ru, de, fr)  
SocialSpy state: `plugins/ChatSync/spy.yml` (auto)

<details>
<summary><b>Open example config (important keys)</b></summary>

```yaml
language: "en"
auto_language: true

chat:
  heads:
    enabled: true
    force_first: true
    gap: " "
    debug: false
    fallback: ""
  global:
    require_symbol: true
    symbol: "!"
    format: "%head%&8[&aG&8] %luckperms_prefix% {username-color}%player% %luckperms_suffix%&7 » &f%message%"
    cooldown: 3
  local:
    radius: 100.0
    cooldown: 2
    format: "%head%&8[&fL&8] %luckperms_prefix% {username-color}%player% %luckperms_suffix%&7 » &f%message%"
  me:
    format: "%head%&7* %player% %message%"
    radius: -1

clickable_names:
  enabled: true
  click_command: "/msg %player% "
  click_action: "SUGGEST_COMMAND"   # SUGGEST_COMMAND | RUN_COMMAND | COPY_TO_CLIPBOARD
  heads_in_commands: true

ignore:
  click_to_unignore: true
  click_on_added_message: true
  allow_offline: true
  max_ignored: 0

console_pm:
  enabled: true
  console_format: "&8[&eChatSync&8] &7PM from &f%player%&7: &f%message%"
  log_to_file: true
  count_stats: true

socialspy:
  permission: "chatsync.spy"
  local_chat: true
  private_messages: true
  team_chat: true
  me: true

vanish:
  hide_join_quit: true
  hide_lastseen_online: true
  see_permissions:
    - "sv.see"
    - "pv.see"
    - "essentials.vanish.see"
    - "chatsync.vanish.see"

teams:
  enabled: true
  max_teams: 50
  max_members: 8
  max_co_owners: 3
  default_symbol: "#"
  symbol_pool: "#$~@%^*"
  format: "%head%&8[%color%%team%&8] &f%player%&7: &f%message%"

hover:
  enabled: true
  show_playtime: true

advanced:
  strip_colors_without_permission: true
  color_permission: "chatsync.color"
  debug: false
```

PM formats live in `lang/*.yml`:

```yaml
pm:
  format_sender:   "%head_self%&eYou &e→ %head_other%&e%receiver%&e: &e%message%"
  format_receiver: "%head_other%&e%sender% &e→ %head_self%&eYou&e: &e%message%"
```

</details>

### Build

```bash
mvn clean package
# → target/chatsync-1.7.2.jar
```

Requires **JDK 21**. Compiles against Paper API **1.21.4**. Runs on **1.21.x – 26.2**.

### Support

Bugs / questions: [Discord](https://discord.com/invite/zQevSujnbe)

---

## Русский

### Возможности

| Функция | Описание |
|--------|----------|
| **Глобальный / локальный чат** | `!` — глобал; локал с радиусом, кулдауном, slowmode |
| **Форматы** | `%head%`, `{username-color}`, префиксы LuckPerms, hex `&#RRGGBB` |
| **ЛС** | `/msg`, `/reply`, `/msg console` |
| **Игнор** | `/ignore`, `/ignorelist` — клик по нику снимает игнор |
| **SocialSpy** | ЛС, локал, team, `/me` — **сохраняется после перезахода** |
| **Команды (teams)** | Одна команда на игрока, символ чата, инвайты, совладельцы |
| **Головы** | Нативные (клиент 1.21.9+) + SkinsRestorer |
| **Playtime** | `/playtime`, `/playtimetop`, `/lastseen` (учёт ваниша) |
| **Объявления** | Пресеты, скрытие автора |
| **Статистика** | `/chatstats` + сброс |
| **Смерти / вход-выход** | Перевод смертей, кликабельные ники, скрытие ваниша |
| **Языки** | en, ru, de, fr + `auto_language` |
| **Интеграции** | LuckPerms, PlaceholderAPI, DiscordSRV, LiteBans, CoreProtect, SuperVanish / PremiumVanish / Essentials, SkinsRestorer |

### Команды

| Команда | Описание | Право |
|---------|----------|-------|
| `/msg <игрок\|console> <текст>` | ЛС или сообщение в консоль | все / `chatsync.msg.console` |
| `/reply <текст>` | Ответ на последнее ЛС | все |
| `/ignore <игрок>` | Вкл/выкл игнор | все |
| `/ignorelist` | Список игнора (клик = снять) | все |
| `/socialspy` | Режим слежки (сохраняется) | `chatsync.spy` |
| `/me <действие>` | RP-сообщение | `chatsync.me` |
| `/clear [игрок] [confirm]` | Очистка чата | `chatsync.clear` |
| `/chatstats [игрок]` | Статистика чата | `chatsync.chatstats` |
| `/chatstats reset <игрок\|all> [confirm]` | Сброс статистики | `chatsync.chatstats.reset` |
| `/broadcast <текст\|пресет\|-h>` | Объявление | `chatsync.broadcast` |
| `/playtime [игрок]` | Время игры | `chatsync.playtime` |
| `/playtimetop` | Топ playtime | `chatsync.playtimetop` |
| `/lastseen <игрок>` | Когда был онлайн | `chatsync.lastseen` |
| `/team ...` | Система команд | `chatsync.team` |
| `/chatsync info` | Инфо о плагине | все |
| `/chatsync reload` | Перезагрузка конфига | `chatsync.admin` |

### Права

См. таблицу **Permissions** в английской части (те же ноды).

### Плейсхолдеры

См. таблицу **Placeholders** выше.  
В форматах чата: `%player%` `%message%` `%head%` `%head_self%` `%head_other%` `%luckperms_prefix%` `%luckperms_suffix%` `{username-color}`

### Конфигурация

`plugins/ChatSync/config.yml` — основной конфиг  
`plugins/ChatSync/lang/` — тексты сообщений  
`plugins/ChatSync/spy.yml` — сохранённый SocialSpy  

Пример ключевых настроек — в блоке **Open example config** выше.

### Сборка

```bash
mvn clean package
# → target/chatsync-1.7.2.jar
```

**JDK 21**. Paper API **1.21.4**. Работает на **1.21.x – 26.2**.

### Поддержка

Баги и вопросы: [Discord](https://discord.com/invite/zQevSujnbe)

---

### Links / Ссылки

| | |
| :--- | :--- |
| **GitHub** | https://github.com/Mutya660/ChatSync |
| **Modrinth** | https://modrinth.com/plugin/chatsync |
| **SpigotMC** | https://www.spigotmc.org/resources/chatsync.137778/ |
| **CurseForge** | https://www.curseforge.com/minecraft/bukkit-plugins/chatsync |
| **Discord** | https://discord.com/invite/zQevSujnbe |
| **Boosty** | https://boosty.to/mutya660/donate |

*ChatSync v1.7.2*

<sub>This plugin was developed with the help of AI. / Плагин сделан с помощью ИИ.</sub>
