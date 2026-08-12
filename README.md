# ChatSync

**Multifunctional chat plugin for Minecraft (Paper 1.21.x – 26.2)** · **v1.7.2**

Global & local chat, private messages, ignore, socialspy, `/me`, clear chat, statistics, playtime, broadcasts, teams, death-message translation, clickable names, player heads, vanish-aware join/quit, full localization (**en / ru / de / fr**).

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
| :--- | :--- |
| **Global / local chat** | Prefix `!` → global; otherwise local with radius. Cooldown & slowmode. Clickable names. `{username-color}`, `%head%`, hex `&#RRGGBB`. |
| **Localization** | `en`, `ru`, `de`, `fr`. `auto_language: true` follows the client language. |
| **Private messages** | `/msg`, `/reply`, `/msg console` (staff → server console). |
| **Ignore** | `/ignore`, `/ignorelist` — click a name to unignore. |
| **Plugin info** | `/chatsync info` — version & author. |
| **Roleplay** | `/me`. |
| **Clear chat** | `/clear` with confirmation. |
| **Chat stats** | `/chatstats`, reset. |
| **Playtime** | `/playtime`, `/playtimetop`, `/lastseen` (vanish-aware). |
| **Broadcasts** | `/broadcast`, presets, hide author. |
| **Teams** | One team per player, chat symbol, invites, co-owners. |
| **Heads** | Native heads (1.21.9+) + SkinsRestorer textures. |
| **Vanish** | Hide join/quit; `/lastseen` hides vanished as online. |
| **Anti-spam** | Staff alerts. |
| **Integrations** | LuckPerms, CoreProtect, PlaceholderAPI, DiscordSRV, LiteBans, SuperVanish / PremiumVanish / Essentials, SkinsRestorer. |

### Screenshots

<p align="center">
  <img src="screenshots/en-chat.png" alt="">
  <img src="screenshots/en-pm.png" alt="">
  <img src="screenshots/en-broadcast.png" alt="">
  <img src="screenshots/en-stats.png" alt="">
</p>
<p align="center">
  <img src="screenshots/ru-chat.png" alt="">
  <img src="screenshots/ru-pm.png" alt="">
  <img src="screenshots/ru-broadcast.png" alt="">
  <img src="screenshots/ru-stats.png" alt="">
</p>

### Commands

| Command | Description |
| :--- | :--- |
| `/msg <player\|console> <message>` | Private message (or to console) |
| `/reply <message>` | Reply to last PM |
| `/ignore <player>` | Toggle ignore |
| `/ignorelist` | List ignored (click name to unignore) |
| `/socialspy` | Spy on PMs / local chat |
| `/me <action>` | Roleplay message |
| `/clear [player] [confirm]` | Clear chat |
| `/chatstats [player]` / `reset` | Statistics |
| `/broadcast <msg\|preset\|-h>` | Announcement |
| `/playtime [player]` | Playtime |
| `/playtimetop` | Playtime top |
| `/lastseen <player>` | Last online |
| `/team ...` | Team system |
| `/chatsync info\|reload` | Info / reload config |

### Placeholders (PlaceholderAPI)

`%chatsync_version%` `%chatsync_author%` `%chatsync_playtime%` `%chatsync_playtime_seconds%`  
`%chatsync_messages_total%` `%chatsync_messages_global%` `%chatsync_messages_local%` `%chatsync_messages_pm%` `%chatsync_messages_me%` `%chatsync_messages_broadcast%`  
`%chatsync_team%` `%chatsync_team_symbol%` `%chatsync_team_color%` `%chatsync_team_owner%` `%chatsync_team_size%` `%chatsync_in_team%`

### Quick setup

```yaml
language: "en"
auto_language: true

chat:
  heads:
    enabled: true
    force_first: true
    gap: " "
  global:
    format: "%head%&8[&aG&8] %luckperms_prefix% {username-color}%player% %luckperms_suffix%&7 » &f%message%"

clickable_names:
  enabled: true
  click_command: "/msg %player% "
  click_action: "SUGGEST_COMMAND"

ignore:
  click_to_unignore: true

vanish:
  hide_join_quit: true
  hide_lastseen_online: true

teams:
  enabled: true
  default_symbol: "#"
```

### Build

```bash
mvn clean package
# → target/chatsync-1.7.2.jar
```

JDK **21+**. Compiles against Paper API **1.21.4**. Same JAR runs on **1.21.x – 26.2**.

### Support

Bugs / issues: Discord [discord.com/invite/zQevSujnbe](https://discord.com/invite/zQevSujnbe)

---

## Русский

### Возможности

| Функция | Описание |
| :--- | :--- |
| **Глобальный / локальный чат** | Префикс `!` — глобал; иначе локал с радиусом. Кулдаун, slowmode, кликабельные ники, `{username-color}`, `%head%`, hex. |
| **Локализация** | `en`, `ru`, `de`, `fr`. `auto_language` — язык клиента. |
| **ЛС** | `/msg`, `/reply`, `/msg console`. |
| **Игнор** | `/ignore`, `/ignorelist` — клик по нику снимает игнор. |
| **Инфо** | `/chatsync info` — версия и автор. |
| **Команды** | `/me`, `/clear`, `/chatstats`, `/broadcast`, `/playtime`, `/lastseen`, `/team`. |
| **Головы** | Нативные (1.21.9+) + SkinsRestorer. |
| **Ваниш** | Скрытие входа/выхода; `/lastseen` не показывает ваниш как онлайн. |

### Сборка

```bash
mvn clean package
# → target/chatsync-1.7.2.jar
```

### Поддержка

Баги и ошибки: Discord [discord.com/invite/zQevSujnbe](https://discord.com/invite/zQevSujnbe)

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
