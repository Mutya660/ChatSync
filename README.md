# ChatSync

**Multifunctional chat plugin for Minecraft (Paper 1.21.x – 26.2)** · **v1.7.3**

Global & local chat · private messages · ignore · SocialSpy · teams · playtime · broadcasts · player heads · GUI · 4 languages (**en / ru / de / fr**)

**Links:** [Modrinth](https://modrinth.com/plugin/chatsync) · [SpigotMC](https://www.spigotmc.org/resources/chatsync.137778/) · [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/chatsync) · [GitHub](https://github.com/Mutya660/ChatSync) · [Discord](https://discord.com/invite/zQevSujnbe) · [Boosty](https://boosty.to/mutya660/donate)

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
| **Stats** | `/chatstats`, `/chatstatstop` + reset |
| **GUI** | `/chatsync gui` — chat top, playtime top, ignore list |
| **Death / join-quit** | Translated deaths (RU pack), clickable names, vanish hide |
| **Languages** | `en`, `ru`, `de`, `fr` — set in `config.yml` (`language:`) for everyone |
| **Anti-spam** | Staff notifications (flood / same message / caps) |

### Builds

| File | Use on |
|------|--------|
| `chatsync-1.7.3.jar` | **Paper, Purpur, Leaf, Pufferfish** |
| `chatsync-1.7.3-arclight.jar` | **Arclight**, Mohist, Magma, Spigot (Adventure bundled) |

Do not put both JARs in `plugins/` at once.

Arclight is compatibility mode: chat, commands and data work; 2D object-heads and clickable/hover names need Paper.

### Recommended plugins (optional)

| Plugin | Why |
|--------|-----|
| **[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)** | `%chatsync_*%` placeholders |
| **[LuckPerms](https://luckperms.net/)** | Prefix / suffix / username color in chat |
| **[SkinsRestorer](https://www.spigotmc.org/resources/skinsrestorer.2124/)** | Correct heads when using custom / cracked skins |
| **[SuperVanish](https://www.spigotmc.org/resources/supervanish-be-invisible.1331/)** / PremiumVanish | Hide join/quit & lastseen while vanished |
| **[LiteBans](https://www.spigotmc.org/resources/litebans.3715/)** | Mute support in chat / PM |
| **[DiscordSRV](https://www.spigotmc.org/resources/discordsrv.18494/)** | Bridge chat / deaths to Discord |
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
| `/chatstatstop` | Chat leaderboard (paginated) | `chatsync.chatstatstop` |
| `/broadcast <message>` / presets | Broadcast | `chatsync.broadcast` |
| `/playtime [player]` | Playtime | `chatsync.playtime` |
| `/playtimetop` | Playtime top | `chatsync.playtimetop` |
| `/lastseen <player>` | Last seen | `chatsync.lastseen` |
| `/team ...` | Teams system | `chatsync.team` |
| `/chatsync info` | Plugin info | everyone |
| `/chatsync gui` | GUI menu | `chatsync.gui` |
| `/chatsync reload` | Reload | `chatsync.admin` |

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `chatsync.admin` | `/chatsync reload` | op |
| `chatsync.spy` | `/socialspy` | op |
| `chatsync.color` | Use `&` and hex colors in chat | op |
| `chatsync.me` | `/me` | true |
| `chatsync.clear` | `/clear` | op |
| `chatsync.chatstats` | View own stats | true |
| `chatsync.chatstats.others` | View other players' stats | op |
| `chatsync.chatstats.reset` | Reset statistics | op |
| `chatsync.chatstatstop` | Chat leaderboard | true |
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
| `chatsync.gui` | `/chatsync gui` | true |
| `chatsync.gui.unmute` | Unignore from GUI | true |
| `chatsync.vanish.see` | See vanished players as online in `/lastseen` | op |

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
| `config.yml` | Main settings, including `language: "en"` |
| `lang/en.yml`, `ru.yml`, `de.yml`, `fr.yml` | All texts |
| `spy.yml` / teams / playtime / stats | Created automatically |

Language is **server-wide** — key `language` in `config.yml`. No per-client auto-detection.

### Build

```bash
mvn clean package
# → target/chatsync-1.7.3.jar
# → target/chatsync-1.7.3-arclight.jar
```

**JDK 21+**. Paper API **1.21.4**.

### Support

Bugs and questions: [Discord](https://discord.com/invite/zQevSujnbe) (`mutya660`)

---

## Русский

### Возможности

| Функция | Описание |
|---------|----------|
| **Глобальный / локальный чат** | Префикс `!` для глобального; локальный с радиусом, кулдауном, slowmode |
| **Форматы** | `%head%`, `{username-color}`, префикс/суффикс LuckPerms, hex `&#RRGGBB` |
| **Личные сообщения** | `/msg`, `/reply`, игрок ↔ игрок, игрок → консоль, **консоль → игрок** |
| **Игнор** | `/ignore`, `/ignorelist` — клик по нику, чтобы убрать |
| **SocialSpy** | ЛС, локал, команда, `/me` — **сохраняется после перезахода** (`spy.yml`) |
| **Команды (teams)** | Одна команда на игрока, символ чата, приглашения с кнопками, совладельцы, передача |
| **Головы** | Нативные головы (клиент 1.21.9+), две головы в ЛС, голова консоли, смерти/ачивки |
| **Время игры** | `/playtime`, `/playtimetop`, `/lastseen` (с учётом vanish) |
| **Объявления** | Пресеты, скрытие автора для персонала |
| **Статистика** | `/chatstats`, `/chatstatstop` + сброс |
| **GUI** | `/chatsync gui` — топ чата, топ времени, список игнора |
| **Смерти / вход-выход** | Переведённые смерти (RU-пак), кликабельные ники, скрытие vanish |
| **Языки** | `en`, `ru`, `de`, `fr` — задаётся в `config.yml` (`language:`) на весь сервер |
| **Антиспам** | Уведомления персоналу (флуд / одинаковые сообщения / капс) |

### Сборки

| Файл | Куда |
|------|------|
| `chatsync-1.7.3.jar` | **Paper, Purpur, Leaf, Pufferfish** |
| `chatsync-1.7.3-arclight.jar` | **Arclight** / Spigot / Mohist / Magma |

На Arclight полный набор Paper-фич (головы object-component, кликабельные ники) недоступен — это режим совместимости.

### Команды

| Команда | Описание | Право |
|---------|----------|-------|
| `/msg <игрок\|console> <текст>` | ЛС игроку или в консоль | все / `chatsync.msg.console` |
| `/reply <текст>` | Ответ на последнее ЛС | все |
| `/ignore <игрок>` | Вкл/выкл игнор | все |
| `/ignorelist` | Список игнора | все |
| `/socialspy` | Режим слежки | `chatsync.spy` |
| `/me <действие>` | RP-сообщение | `chatsync.me` |
| `/clear [игрок] [confirm]` | Очистка чата | `chatsync.clear` |
| `/chatstats [игрок]` | Статистика | `chatsync.chatstats` |
| `/chatstatstop` | Топ сообщений | `chatsync.chatstatstop` |
| `/broadcast ...` | Объявление | `chatsync.broadcast` |
| `/playtime [игрок]` | Время игры | `chatsync.playtime` |
| `/playtimetop` | Топ времени | `chatsync.playtimetop` |
| `/lastseen <игрок>` | Когда был онлайн | `chatsync.lastseen` |
| `/team ...` | Система команд | `chatsync.team` |
| `/chatsync info` | Инфо о плагине | все |
| `/chatsync gui` | Меню | `chatsync.gui` |
| `/chatsync reload` | Перезагрузка | `chatsync.admin` |

Права и плейсхолдеры — те же ключи, что в английской части.

### Конфигурация

| Файл | Назначение |
|------|------------|
| `config.yml` | Основные настройки, в т.ч. `language: "ru"` |
| `lang/en.yml`, `ru.yml`, `de.yml`, `fr.yml` | Все тексты |
| `spy.yml` / teams / playtime / stats | Создаются автоматически |

Язык **один на весь сервер** — ключ `language` в `config.yml`. Автоопределение по клиенту отключено.

### Сборка

```bash
mvn clean package
# → target/chatsync-1.7.3.jar
# → target/chatsync-1.7.3-arclight.jar
```

**JDK 21+**. Paper API **1.21.4**.

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

*ChatSync v1.7.3*

<sub>This plugin was developed with the help of AI. / Плагин сделан с помощью ИИ.</sub>
