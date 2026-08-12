# ChatSync

**Multifunctional chat plugin for Minecraft (Paper 1.21.x – 26.2)** · **v1.7.2**

Global & local chat · private messages · ignore · SocialSpy · teams · playtime · broadcasts · player heads · multi-language (**en / ru / de / fr**)

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
| **Global / local chat** | Prefix `!` for global; local with radius, cooldown, slowmode |
| **Formats** | `%head%`, `{username-color}`, LuckPerms prefix/suffix, hex `&#RRGGBB` |
| **Private messages** | `/msg`, `/reply`, player ↔ player, player → console, **console → player** |
| **Ignore** | `/ignore`, `/ignorelist` — click name to unignore |
| **SocialSpy** | PM, local, team, `/me` — **persists after rejoin** (`spy.yml`) |
| **Teams** | One team per player, chat symbol, invites with buttons, co-owners, transfer |
| **Heads** | Native heads (client 1.21.9+), dual heads in PM, console head, death/advancement heads |
| **Playtime** | `/playtime`, `/playtimetop`, `/lastseen` (vanish-aware) |
| **Broadcasts** | Presets, hide author for staff |
| **Stats** | `/chatstats` + reset |
| **Death / join-quit** | Translated deaths (RU pack), clickable names, vanish hide |
| **Languages** | en, ru, de, fr + `auto_language` from client locale |
| **Anti-spam** | Staff notifications (flood / same message / caps) |

### Recommended plugins (optional)

| Plugin | Why |
|--------|-----|
| **[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)** | `%chatsync_*%` placeholders |
| **[LuckPerms](https://luckperms.net/)** | Prefix / suffix / username color in chat |
| **[SkinsRestorer](https://www.spigotmc.org/resources/skinsrestorer.2124/)** | Correct heads when using custom / cracked skins |
| **[SuperVanish](https://www.spigotmc.org/resources/supervanish-be-invisible.1331/)** / PremiumVanish | Hide join/quit & lastseen while vanished |
| **[LiteBans](https://www.spigotmc.org/resources/litebans.3715/)** | Mute support in chat / PM |
| **[DiscordSRV](https://www.spigotmc.org/resources/discordsrv.18494/)** | Bridge chat to Discord (if enabled in config) |
| **[CoreProtect](https://www.spigotmc.org/resources/coreprotect.8631/)** | Soft integration hook |

Paper / Purpur **1.21.x – 26.2**, **Java 21+**. No hard depends — all integrations are soft.

### Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/msg <player\|console> <message>` | PM to player or to server console | everyone / `chatsync.msg.console` |
| `/msg <player> <message>` *(from console)* | Console → player PM | console |
| `/reply <message>` | Reply to last PM | everyone |
| `/ignore <player>` | Toggle ignore | everyone |
| `/ignorelist` | Ignored list (click name to unignore) | everyone |
| `/socialspy` | Toggle SocialSpy (saved) | `chatsync.spy` |
| `/me <action>` | Roleplay action | `chatsync.me` |
| `/clear [player] [confirm]` | Clear chat | `chatsync.clear` |
| `/chatstats [player]` | Chat statistics | `chatsync.chatstats` |
| `/chatstats reset <player\|all> [confirm]` | Reset stats | `chatsync.chatstats.reset` |
| `/broadcast <msg\|preset\|-h\|hide>` | Server announcement | `chatsync.broadcast` |
| `/broadcast` presets management | Manage presets | `chatsync.broadcast.preset` |
| `/playtime [player]` | View playtime | `chatsync.playtime` |
| `/playtimetop` | Playtime top | `chatsync.playtimetop` |
| `/lastseen <player>` | Last online time | `chatsync.lastseen` |
| `/team create\|invite\|accept\|deny\|leave\|kick\|disband\|chat\|name\|color\|symbol\|info\|transfer\|promote\|demote` | Team system | `chatsync.team` / `chatsync.team.create` |
| `/chatsync info` | Plugin version & author | everyone |
| `/chatsync reload` | Reload config & languages | `chatsync.admin` |

Aliases (examples): `/m`, `/tell`, `/w`, `/r`, `/bc`, `/csync`, …

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `chatsync.admin` | `/chatsync reload` | op |
| `chatsync.spy` | `/socialspy` | op |
| `chatsync.color` | Use `&` and hex colors in chat | op |
| `chatsync.me` | `/me` | true |
| `chatsync.clear` | `/clear` | op |
| `chatsync.chatstats` | View own/others stats (see also `.others`) | true |
| `chatsync.chatstats.others` | View other players' stats | op |
| `chatsync.chatstats.reset` | Reset statistics | op |
| `chatsync.broadcast` | `/broadcast` | op |
| `chatsync.broadcast.preset` | Manage broadcast presets | op |
| `chatsync.playtime` | `/playtime` | true |
| `chatsync.playtimetop` | `/playtimetop` | true |
| `chatsync.lastseen` | `/lastseen` | true |
| `chatsync.msg.console` | `/msg console` | op |
| `chatsync.bypass_cooldown` | Bypass chat cooldown | op |
| `chatsync.spam.notify` | Receive spam alerts | op |
| `chatsync.spam.bypass` | Bypass spam checks | op |
| `chatsync.team` | Use team commands | true |
| `chatsync.team.create` | Create a team | true |
| `chatsync.team.admin` | Team admin bypass | op |
| `chatsync.vanish.see` | See vanished players as online in `/lastseen` | — |
| `sv.see` / `pv.see` / `essentials.vanish.see` | Same, via vanish plugins | — |

### Placeholders (PlaceholderAPI)

| Placeholder | Description |
|-------------|-------------|
| `%chatsync_version%` | Plugin version |
| `%chatsync_author%` | Author (`Mutya660`) |
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
| `%chatsync_team_color%` | Team color |
| `%chatsync_team_owner%` | Owner name |
| `%chatsync_team_size%` | Member count |
| `%chatsync_team_members%` | Member list |
| `%chatsync_in_team%` | `yes` / `no` |
| `%chatsync_team_is_owner%` | `yes` / `no` |
| `%chatsync_team_is_leader%` | Owner or co-owner |

**Chat format tokens:** `%player%` `%message%` `%head%` `%head_self%` `%head_other%` `%head_console%` `%luckperms_prefix%` `%luckperms_suffix%` `{username-color}`

### Configuration

| File | Purpose |
|------|---------|
| `plugins/ChatSync/config.yml` | Main settings |
| `plugins/ChatSync/lang/en.yml` (ru, de, fr) | All player-facing messages |
| `plugins/ChatSync/spy.yml` | Saved SocialSpy list (auto) |
| `plugins/ChatSync/teams.yml` | Teams data (auto) |
| `plugins/ChatSync/playtime.yml` / stats | Playtime & stats (auto) |

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
  click_action: "SUGGEST_COMMAND"  # SUGGEST_COMMAND | RUN_COMMAND | COPY_TO_CLIPBOARD
  heads_in_commands: true

ignore:
  click_to_unignore: true
  click_on_added_message: true
  allow_offline: true
  max_ignored: 0

console_pm:
  enabled: true
  show_head: true
  head_name: "Console"
  head_uuid: "00000000-0000-0000-0000-0000000000c0"
  # Base64 from minecraft-heads.com / mineskin.org
  head_texture: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGExNjAzOGJjOGU2NTE4YWZhOTE0OThkYWI3Njc1YzAxY2IzMWExMjVkMjFjNDliODYxMjk0ZDM5ZTFjNTYwYyJ9fX0="
  head_signature: ""
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
  max_members: 8
  max_co_owners: 3
  default_symbol: "#"
  format: "%head%&8[%color%%team%&8] &f%player%&7: &f%message%"

hover:
  enabled: true
  show_playtime: true

advanced:
  color_permission: "chatsync.color"
  debug: false
```

PM formats in `lang/*.yml`:

```yaml
pm:
  format_sender:   "%head_self%&eYou &e→ %head_other%&e%receiver%&e: &e%message%"
  format_receiver: "%head_other%&e%sender% &e→ %head_self%&eYou&e: &e%message%"
  format_console_sender: "%head_self%&eYou &e→ %head_console%&eConsole&e: &e%message%"
  format_from_console: "%head_console%&e%console% &e→ &eYou&e: &e%message%"
```

</details>



**JDK 21+**. Paper API **1.21.4**. Same JAR runs on **1.21.x – 26.2**.

### Support

Bugs / questions: [Discord](https://discord.com/invite/zQevSujnbe) (`mutya660`)

---

## Русский

### Возможности

| Функция | Описание |
|--------|----------|
| **Глобальный / локальный чат** | `!` — глобал; локал с радиусом, кулдауном, slowmode |
| **Форматы** | `%head%`, `{username-color}`, LuckPerms, hex `&#RRGGBB` |
| **ЛС** | `/msg`, `/reply`, игрок ↔ игрок, игрок → консоль, **консоль → игрок** |
| **Игнор** | `/ignore`, `/ignorelist` — клик по нику снимает игнор |
| **SocialSpy** | ЛС, локал, team, `/me` — **сохраняется после перезахода** |
| **Команды (teams)** | Одна команда на игрока, символ, инвайты с кнопками, совладельцы |
| **Головы** | Нативные головы, две в ЛС, голова консоли, смерти и достижения |
| **Playtime** | `/playtime`, `/playtimetop`, `/lastseen` (учёт ваниша) |
| **Объявления / статистика** | Пресеты broadcast, `/chatstats` |
| **Языки** | en, ru, de, fr + `auto_language` |
| **Антиспам** | Уведомления персоналу |

### Рекомендуемые плагины (по желанию)

| Плагин | Зачем |
|--------|-------|
| **PlaceholderAPI** | Плейсхолдеры `%chatsync_*%` |
| **LuckPerms** | Префикс / суффикс / цвет ника |
| **SkinsRestorer** | Правильные головы при кастомных скинах |
| **SuperVanish / PremiumVanish** | Скрытие входа/выхода и lastseen в ванише |
| **LiteBans** | Мут в чате и ЛС |
| **DiscordSRV** | Мост чата в Discord |
| **CoreProtect** | Софт-хук |

### Команды

| Команда | Описание | Право |
|---------|----------|-------|
| `/msg <игрок\|console> <текст>` | ЛС игроку или в консоль | все / `chatsync.msg.console` |
| `/msg <игрок> <текст>` *(из консоли)* | ЛС с консоли игроку | консоль |
| `/reply <текст>` | Ответ на последнее ЛС | все |
| `/ignore <игрок>` | Вкл/выкл игнор | все |
| `/ignorelist` | Список игнора (клик = снять) | все |
| `/socialspy` | Режим слежки (сохраняется) | `chatsync.spy` |
| `/me <действие>` | RP-сообщение | `chatsync.me` |
| `/clear [игрок] [confirm]` | Очистка чата | `chatsync.clear` |
| `/chatstats [игрок]` | Статистика | `chatsync.chatstats` |
| `/chatstats reset ...` | Сброс статистики | `chatsync.chatstats.reset` |
| `/broadcast ...` | Объявление | `chatsync.broadcast` |
| `/playtime [игрок]` | Время игры | `chatsync.playtime` |
| `/playtimetop` | Топ времени | `chatsync.playtimetop` |
| `/lastseen <игрок>` | Когда был онлайн | `chatsync.lastseen` |
| `/team ...` | Система команд | `chatsync.team` |
| `/chatsync info` | Инфо о плагине | все |
| `/chatsync reload` | Перезагрузка | `chatsync.admin` |

### Права и плейсхолдеры

См. таблицы **Permissions** и **Placeholders** в английской части (те же ноды и ключи).

В форматах чата: `%player%` `%message%` `%head%` `%head_self%` `%head_other%` `%head_console%` `%luckperms_prefix%` `%luckperms_suffix%` `{username-color}`

### Конфигурация

| Файл | Назначение |
|------|------------|
| `config.yml` | Основные настройки |
| `lang/*.yml` | Все тексты сообщений |
| `spy.yml` | Сохранённый SocialSpy |
| Данные teams / playtime / stats | Создаются автоматически |

Пример ключевых настроек — в блоке **Open example config** выше.

Скин головы консоли: `console_pm.head_texture` (base64 с [minecraft-heads.com](https://minecraft-heads.com) / [mineskin.org](https://mineskin.org)).



**JDK 21+**. Paper API **1.21.4**. Работает на **1.21.x – 26.2**.

### Поддержка

Баги и вопросы: [Discord](https://discord.com/invite/zQevSujnbe) (`mutya660`)

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
