package chatsync;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;
import java.util.Locale;

public class ChatSync extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    /** &-codes + hex &#RRGGBB / &#RGB */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    // ──────────────────────────────────────────────────────────────
    //  State
    // ──────────────────────────────────────────────────────────────

    private final Map<UUID, UUID>      lastMessaged  = new HashMap<>();
    private final Map<UUID, Set<UUID>> ignoreList    = new HashMap<>();
    private final Set<UUID>            socialSpy     = new HashSet<>();
    /** UUID → timestamp последнего глобального сообщения (для кулдауна) */
    private final Map<UUID, Long>      globalCooldown = new HashMap<>();
    /** UUID → timestamp последнего локального сообщения (slowmode) */
    private final Map<UUID, Long>      localCooldown  = new HashMap<>();
    private final Map<String, YamlConfiguration> langConfigs = new HashMap<>();

    /** Ожидающие подтверждения запросы /clear: ключ отправителя → цель + время истечения. */
    private final Map<UUID, PendingClear> pendingClears = new HashMap<>();
    private static final UUID CONSOLE_UUID = new UUID(0L, 0L);

    private ChatStatsManager statsManager;
    private ChatLogger       chatLogger;
    private LuckPermsHook    luckPermsHook;
    private CoreProtectHook  coreProtectHook;
    private PlaytimeManager  playtimeManager;
    private LiteBansHook     liteBansHook;
    private VanishHook       vanishHook;
    private TeamManager      teamManager;

    /** Ожидающие подтверждения сброса статистики: ключ отправителя → время истечения. */
    private final Map<UUID, Long> pendingStatsResets = new HashMap<>();

    /** Анти-спам: UUID → последние сообщения (текст + timestamp). */
    private final Map<UUID, java.util.Deque<SpamEntry>> recentMessages = new java.util.concurrent.ConcurrentHashMap<>();

    /** Админы, у которых скрыт автор в /broadcast (персональный toggle). */
    private final Set<UUID> broadcastHideAuthor = new HashSet<>();

    private record SpamEntry(String text, long time, String channel) {}

    public ChatStatsManager getStatsManager() { return statsManager; }
    public PlaytimeManager getPlaytimeManager() { return playtimeManager; }

    public TeamManager getTeamManager() { return teamManager; }

    private static final List<String> SUPPORTED_LANGS = List.of("en", "ru", "de", "fr");

    private static final Map<String, String> LOCALE_MAP = Map.ofEntries(
        Map.entry("en_us", "en"), Map.entry("en_gb", "en"),
        Map.entry("en_au", "en"), Map.entry("en_ca", "en"), Map.entry("en_nz", "en"),
        Map.entry("ru_ru", "ru"),
        Map.entry("de_de", "de"), Map.entry("de_at", "de"), Map.entry("de_ch", "de"),
        Map.entry("fr_fr", "fr"), Map.entry("fr_ca", "fr"),
        Map.entry("fr_be", "fr"), Map.entry("fr_ch", "fr")
    );

    /** Ожидающая подтверждения заявка на очистку чата. target == null означает "очистить всем". */
    private record PendingClear(UUID target, long expiresAt) {}

    // ──────────────────────────────────────────────────────────────
    //  Lifecycle
    // ──────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLangFiles();

        this.statsManager    = new ChatStatsManager(this);
        this.chatLogger      = new ChatLogger(this,
                getConfig().getString("logging.folder", "logs"),
                getConfig().getBoolean("logging.async", true));
        this.luckPermsHook   = new LuckPermsHook(this);
        this.coreProtectHook = new CoreProtectHook(this);
        this.playtimeManager = new PlaytimeManager(this);
        this.liteBansHook    = new LiteBansHook(this);
        this.vanishHook      = new VanishHook(this);
        this.teamManager     = new TeamManager(this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new DeathMessageTranslator(this), this);
        getServer().getPluginManager().registerEvents(new AdvancementMessageTranslator(this), this);

        registerCmd("msg",         this);
        registerCmd("reply",       this);
        registerCmd("ignore",      this);
        registerCmd("ignorelist",  this);
        registerCmd("socialspy",   this);
        registerCmd("chatsync",    this);
        registerCmd("me",          this);
        registerCmd("clear",       this);
        registerCmd("chatstats",   this);
        registerCmd("broadcast",   this);
        registerCmd("playtime",    this);
        registerCmd("playtimetop", this);
        registerCmd("lastseen",    this);
        registerCmd("team",        this);

        if (getConfig().getBoolean("stats.enabled", true)) {
            long intervalTicks = 20L * Math.max(30, getConfig().getInt("stats.save_interval", 300));
            Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                    () -> statsManager.saveIfDirty(), intervalTicks, intervalTicks);
        }

        if (getConfig().getBoolean("playtime.enabled", true)) {
            long ptTicks = 20L * Math.max(30, getConfig().getInt("playtime.save_interval", 300));
            Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                    () -> playtimeManager.saveIfDirty(), ptTicks, ptTicks);
            // Уже онлайн на момент включения плагина (reload / late enable)
            for (Player online : Bukkit.getOnlinePlayers()) {
                playtimeManager.onJoin(online.getUniqueId(), online.getName());
            }
        }

        // Периодическая очистка просроченных заявок /clear, чтобы карта не росла бесконечно.
        Bukkit.getScheduler().runTaskTimer(this, this::purgeExpiredClears, 20L * 60, 20L * 60);

        // PlaceholderAPI expansion (reverse direction — own placeholders for TAB/scoreboard)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new ChatSyncExpansion(this).register();
                getLogger().info("PlaceholderAPI expansion registered (%chatsync_*%).");
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        }

        getLogger().info("ChatSync v" + getDescription().getVersion() + " enabled!");
        if (luckPermsHook.isAvailable()) getLogger().info("LuckPerms detected: direct API fallback enabled.");
        if (coreProtectHook.isAvailable()) getLogger().info("CoreProtect detected: /clear will be logged for /co lookup.");
        if (liteBansHook.isAvailable()) getLogger().info("LiteBans detected: muted players blocked in chat/PM/me.");
        if (getConfig().getBoolean("playtime.enabled", true)) getLogger().info("Playtime tracking enabled.");
    }

    @Override
    public void onDisable() {
        if (statsManager != null) statsManager.save();
        if (playtimeManager != null && getConfig().getBoolean("playtime.enabled", true)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                playtimeManager.onQuit(online.getUniqueId(), online.getName());
            }
            playtimeManager.save();
        }
        if (chatLogger != null) chatLogger.flushNow();
    }

    private void registerCmd(String name, CommandExecutor exec) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) return;
        cmd.setExecutor(exec);
        cmd.setTabCompleter(this);
    }

    // ──────────────────────────────────────────────────────────────
    //  Lang system
    // ──────────────────────────────────────────────────────────────

    private void loadLangFiles() {
        langConfigs.clear();
        for (String lang : SUPPORTED_LANGS) {
            String resource = "lang/" + lang + ".yml";
            File file = new File(getDataFolder(), resource);
            if (!file.exists()) saveResource(resource, false);
            langConfigs.put(lang, YamlConfiguration.loadConfiguration(file));
        }
        getLogger().info("Loaded " + langConfigs.size() + " language(s): " + String.join(", ", langConfigs.keySet()));
    }

    private String getLang(Player player) {
        // auto_language (default true): follow client locale; else force config language
        if (!getConfig().getBoolean("auto_language", true)) {
            return getConfig().getString("language", "en");
        }
        String locale = player.locale().toString().toLowerCase().replace("-", "_");
        String mapped = LOCALE_MAP.get(locale);
        if (mapped != null) return mapped;
        if (locale.length() >= 2) {
            String prefix = locale.substring(0, 2);
            if (langConfigs.containsKey(prefix)) return prefix;
        }
        return getConfig().getString("language", "en");
    }

    String t(Player player, String key) { return t(getLang(player), key); }

    private String t(String lang, String key) {
        YamlConfiguration cfg = langConfigs.get(lang);
        if (cfg != null && cfg.contains(key)) return cfg.getString(key, key);
        YamlConfiguration en = langConfigs.get("en");
        if (en != null && en.contains(key)) return en.getString(key, key);
        return key;
    }

    /** Список строк из lang (например broadcast.lines). Fallback → en → config. */
    private java.util.List<String> tList(String lang, String key) {
        YamlConfiguration cfg = langConfigs.get(lang);
        if (cfg != null) {
            java.util.List<String> list = cfg.getStringList(key);
            if (list != null && !list.isEmpty()) return list;
        }
        if (!"en".equals(lang)) {
            YamlConfiguration en = langConfigs.get("en");
            if (en != null) {
                java.util.List<String> list = en.getStringList(key);
                if (list != null && !list.isEmpty()) return list;
            }
        }
        // config.yml fallback (legacy)
        java.util.List<String> fromCfg = getConfig().getStringList(key);
        return fromCfg != null ? fromCfg : java.util.List.of();
    }

    private java.util.List<String> tList(Player player, String key) {
        return tList(getLang(player), key);
    }

    private java.util.List<String> tListDefault(String key) {
        return tList(getConfig().getString("language", "en"), key);
    }

    private String tDefault(String key) {
        return t(getConfig().getString("language", "en"), key);
    }

    /** Возвращает локализованную строку для любого CommandSender (игрок или консоль). */
    private String tAny(CommandSender sender, String key) {
        return sender instanceof Player p ? t(p, key) : tDefault(key);
    }

    // ──────────────────────────────────────────────────────────────
    //  Join / Quit
    // ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (playtimeManager != null && getConfig().getBoolean("playtime.enabled", true)) {
            playtimeManager.onJoin(player.getUniqueId(), player.getName());
        }
        if (liteBansHook != null) liteBansHook.onJoin(player);
        if (!tog("join_message") || (vanishHook != null && vanishHook.shouldHideJoinQuit(player))) {
            event.joinMessage(null);
            return;
        }
        event.joinMessage(buildJoinQuitMessage(
                getConfig().getString("messages.join", "&a+ &f%player%"), player));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (playtimeManager != null && getConfig().getBoolean("playtime.enabled", true)) {
            playtimeManager.onQuit(player.getUniqueId(), player.getName());
        }
        if (!tog("quit_message") || (vanishHook != null && vanishHook.shouldHideJoinQuit(player))) {
            event.quitMessage(null);
        } else {
            event.quitMessage(buildJoinQuitMessage(
                    getConfig().getString("messages.quit", "&c- &f%player%"), player));
        }
        if (teamManager != null) teamManager.onQuit(player.getUniqueId());
        lastMessaged.remove(player.getUniqueId());
        socialSpy.remove(player.getUniqueId());
        globalCooldown.remove(player.getUniqueId());
        pendingClears.remove(player.getUniqueId());
        pendingStatsResets.remove(player.getUniqueId());
        recentMessages.remove(player.getUniqueId());
        broadcastHideAuthor.remove(player.getUniqueId());
        localCooldown.remove(player.getUniqueId());
        if (liteBansHook != null) liteBansHook.onQuit(player.getUniqueId());
    }

    private Component buildJoinQuitMessage(String template, Player player) {
        // hover строится внутри buildNameComponent через clickableName
        return buildNameComponent(template, player, null);
    }

    // ──────────────────────────────────────────────────────────────
    //  Chat
    // ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);

        Player sender     = event.getPlayer();
        String rawMessage = LEGACY.serialize(event.message());

        if (!sender.hasPermission("chatsync.color")) rawMessage = stripColorCodes(rawMessage);

        boolean requireSymbol = getConfig().getBoolean("chat.global.require_symbol", true);
        String  globalSymbol  = getConfig().getString("chat.global.symbol", "!");
        boolean isGlobal;
        String  formatStr;

        if (requireSymbol) {
            isGlobal = rawMessage.startsWith(globalSymbol);
            formatStr = isGlobal
                    ? getConfig().getString("chat.global.format")
                    : getConfig().getString("chat.local.format");
            if (isGlobal) rawMessage = rawMessage.substring(globalSymbol.length()).trim();
        } else {
            isGlobal  = true;
            formatStr = getConfig().getString("chat.global.format");
        }

        if (rawMessage.isEmpty()) return;

        // Team chat: message starts with a team's own symbol (e.g. # $ ~)
        if (teamManager != null && teamManager.enabled()) {
            TeamManager.Team bySym = null;
            String matchedSym = null;
            // longest symbol first (pool may have multi-char fallbacks)
            java.util.List<TeamManager.Team> ordered = new java.util.ArrayList<>(teamManager.allTeams());
            ordered.sort((a, b) -> Integer.compare(
                    b.symbol == null ? 0 : b.symbol.length(),
                    a.symbol == null ? 0 : a.symbol.length()));
            for (TeamManager.Team t : ordered) {
                if (t.symbol == null || t.symbol.isEmpty()) continue;
                if (rawMessage.startsWith(t.symbol) && t.members.contains(sender.getUniqueId())) {
                    bySym = t;
                    matchedSym = t.symbol;
                    break;
                }
            }
            if (bySym != null) {
                String teamMsg = rawMessage.substring(matchedSym.length()).trim();
                if (!teamMsg.isEmpty()) sendTeamChat(sender, bySym, teamMsg);
                return;
            }
        }

        // LiteBans mute — ChatSync сам обрабатывает чат, поэтому проверяем явно
        if (liteBansHook != null && liteBansHook.isMuted(sender)) {
            sender.sendMessage(color(t(sender, "chat.muted")));
            return;
        }

        // Анти-спам уведомление стаффу
        checkAndNotifySpam(sender, rawMessage, isGlobal ? "global" : "local");

        // ── Кулдаун / slowmode ─────────────────────────────────────
        if (!sender.hasPermission("chatsync.bypass_cooldown")) {
            if (isGlobal) {
                int cooldownSec = getConfig().getInt("chat.global.cooldown", 0);
                if (cooldownSec > 0) {
                    long lastTime = globalCooldown.getOrDefault(sender.getUniqueId(), 0L);
                    long elapsed  = System.currentTimeMillis() - lastTime;
                    long remaining = (cooldownSec * 1000L) - elapsed;
                    if (remaining > 0) {
                        String sec = String.valueOf((int) Math.ceil(remaining / 1000.0));
                        sender.sendMessage(color(t(sender, "chat.cooldown").replace("%seconds%", sec)));
                        return;
                    }
                }
                globalCooldown.put(sender.getUniqueId(), System.currentTimeMillis());
            } else {
                // Slowmode локального чата
                int cooldownSec = getConfig().getInt("chat.local.cooldown", 0);
                if (cooldownSec > 0) {
                    long lastTime = localCooldown.getOrDefault(sender.getUniqueId(), 0L);
                    long elapsed  = System.currentTimeMillis() - lastTime;
                    long remaining = (cooldownSec * 1000L) - elapsed;
                    if (remaining > 0) {
                        String sec = String.valueOf((int) Math.ceil(remaining / 1000.0));
                        sender.sendMessage(color(t(sender, "chat.local_cooldown").replace("%seconds%", sec)));
                        return;
                    }
                }
                localCooldown.put(sender.getUniqueId(), System.currentTimeMillis());
            }
        }

        if (isGlobal) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && isIgnoring(p, sender)) continue;
                p.sendMessage(buildChatComponent(formatStr, sender, rawMessage, p));
            }
            logToConsole("[GlobalChat] " + sender.getName() + ": " + rawMessage);
            if (getConfig().getBoolean("stats.enabled", true)) {
                statsManager.record(sender.getUniqueId(), sender.getName(), ChatStatsManager.MessageType.GLOBAL);
            }
            logChat("[GLOBAL] " + sender.getName() + ": " + stripColorCodes(rawMessage));
            sendToDiscord(sender, rawMessage, "global");
        } else {
            double radius     = getConfig().getDouble("chat.local.radius", 100.0);
            int    recipients = 0;
            for (Player r : Bukkit.getOnlinePlayers()) {
                if (!r.getWorld().equals(sender.getWorld())) continue;
                if (r.getLocation().distance(sender.getLocation()) > radius) continue;
                if (!r.equals(sender) && isIgnoring(r, sender)) continue;
                r.sendMessage(buildChatComponent(formatStr, sender, rawMessage, r));
                recipients++;
            }
            if (recipients == 1 && tog("local_noone")) sender.sendMessage(color(t(sender, "chat.local_noone")));
            if (tog("local_console_log") && getConfig().getBoolean("chat.local.log_to_console", true)) {
                logToConsole(getConfig()
                        .getString("chat.local.console_format", "[LocalChat] %player%: %message%")
                        .replace("%player%", sender.getName())
                        .replace("%message%", rawMessage));
            }
            // SocialSpy — показываем локальный чат тем, кто вне радиуса
            if (tog("socialspy")) {
                String spyLocalFmt = getConfig().getString("pm.format_spy_local",
                        "&8[SPY-L] &7%player%&8: &7%message%")
                        .replace("%message%", rawMessage);
                for (UUID uid : socialSpy) {
                    Player spy = Bukkit.getPlayer(uid);
                    if (spy == null || spy.equals(sender)) continue;
                    // Не дублируем тем, кто уже видел сообщение (был в радиусе)
                    if (spy.getWorld().equals(sender.getWorld()) &&
                            spy.getLocation().distance(sender.getLocation()) <= radius) continue;
                    spy.sendMessage(buildClickableNameLine(spyLocalFmt, sender.getName(), spy));
                }
            }
            if (getConfig().getBoolean("stats.enabled", true)) {
                statsManager.record(sender.getUniqueId(), sender.getName(), ChatStatsManager.MessageType.LOCAL);
            }
            logChat("[LOCAL] " + sender.getName() + ": " + stripColorCodes(rawMessage));
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Commands
    // ──────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "chatsync"    -> { return cmdChatSync(sender, args); }
            case "msg"         -> { return cmdMsg(sender, args); }
            case "reply"       -> { return cmdReply(sender, args); }
            case "ignore"      -> { return cmdIgnore(sender, args); }
            case "ignorelist"  -> { return cmdIgnoreList(sender); }
            case "socialspy"   -> { return cmdSocialSpy(sender, args); }
            case "me"          -> { return cmdMe(sender, args); }
            case "clear"       -> { return cmdClear(sender, args); }
            case "chatstats"   -> { return cmdChatStats(sender, args); }
            case "broadcast"   -> { return cmdBroadcast(sender, args); }
            case "playtime"    -> { return cmdPlaytime(sender, args); }
            case "playtimetop" -> { return cmdPlaytimeTop(sender, args); }
            case "lastseen"    -> { return cmdLastSeen(sender, args); }
            case "team"        -> { return cmdTeam(sender, args); }
        }
        return false;
    }

    private boolean cmdChatSync(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getConfig().getString("commands.reload.permission", "chatsync.admin"))) {
            sender.sendMessage(color(tAny(sender, "commands.reload.no_permission")));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadLangFiles();
            sender.sendMessage(color(tAny(sender, "commands.reload.success")));
        } else {
            sender.sendMessage(color(tAny(sender, "commands.reload.usage")));
        }
        return true;
    }

    private boolean cmdMsg(CommandSender sender, String[] args) {
        if (!(sender instanceof Player pSender)) { sender.sendMessage("Players only."); return true; }
        if (args.length < 2) { pSender.sendMessage(color(t(pSender, "commands.msg.usage"))); return true; }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            pSender.sendMessage(color(t(pSender, "pm.player_not_found").replace("%player%", args[0])));
            return true;
        }
        if (target.equals(pSender)) { pSender.sendMessage(color(t(pSender, "commands.msg.self"))); return true; }
        if (isIgnoring(target, pSender)) {
            if (tog("ignore_notify_sender"))
                pSender.sendMessage(color(t(pSender, "commands.ignore.ignores_you").replace("%player%", target.getName())));
            return true;
        }
        sendPM(pSender, target, joinArgs(args, 1));
        return true;
    }

    private boolean cmdReply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player pSender)) return true;
        if (args.length < 1) { pSender.sendMessage(color(t(pSender, "commands.reply.usage"))); return true; }

        UUID lastUUID = lastMessaged.get(pSender.getUniqueId());
        if (lastUUID == null) { pSender.sendMessage(color(t(pSender, "commands.reply.no_target"))); return true; }

        Player target = Bukkit.getPlayer(lastUUID);
        if (target == null || !target.isOnline()) {
            lastMessaged.remove(pSender.getUniqueId()); // чистим устаревшую запись
            pSender.sendMessage(color(t(pSender, "commands.reply.offline")));
            return true;
        }
        sendPM(pSender, target, joinArgs(args, 0));
        return true;
    }

    private boolean cmdIgnore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player pSender)) return true;
        if (args.length < 1) { pSender.sendMessage(color(t(pSender, "commands.ignore.usage"))); return true; }

        // Нельзя игнорить себя
        if (args[0].equalsIgnoreCase(pSender.getName())) {
            pSender.sendMessage(color(t(pSender, "commands.ignore.self")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            pSender.sendMessage(color(t(pSender, "pm.player_not_found").replace("%player%", args[0])));
            return true;
        }

        Set<UUID> ignored = ignoreList.computeIfAbsent(pSender.getUniqueId(), k -> new HashSet<>());
        if (ignored.contains(target.getUniqueId())) {
            ignored.remove(target.getUniqueId());
            pSender.sendMessage(color(t(pSender, "commands.ignore.removed").replace("%player%", target.getName())));
            if (tog("ignore_notify_target"))
                target.sendMessage(color(t(target, "commands.ignore.target_removed").replace("%player%", pSender.getName())));
        } else {
            ignored.add(target.getUniqueId());
            pSender.sendMessage(color(t(pSender, "commands.ignore.added").replace("%player%", target.getName())));
            if (tog("ignore_notify_target"))
                target.sendMessage(color(t(target, "commands.ignore.target_added").replace("%player%", pSender.getName())));
        }
        return true;
    }

    private boolean cmdIgnoreList(CommandSender sender) {
        if (!(sender instanceof Player pSender)) return true;
        Set<UUID> ignored = ignoreList.get(pSender.getUniqueId());
        if (ignored == null || ignored.isEmpty()) {
            pSender.sendMessage(color(t(pSender, "commands.ignorelist.empty")));
            return true;
        }
        // Собираем имена (через OfflinePlayer для тех, кто оффлайн)
        List<String> names = new ArrayList<>();
        for (UUID uid : ignored) {
            Player online = Bukkit.getPlayer(uid);
            if (online != null) {
                names.add(online.getName());
            } else {
                @SuppressWarnings("deprecation")
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uid);
                names.add(op.getName() != null ? op.getName() : uid.toString());
            }
        }
        String header = t(pSender, "commands.ignorelist.header").replace("%count%", String.valueOf(names.size()));
        pSender.sendMessage(color(header));
        pSender.sendMessage(color(t(pSender, "commands.ignorelist.entry_prefix") + String.join("&7, &f", names)));
        return true;
    }

    private boolean cmdSocialSpy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player pSender)) return true;
        if (!pSender.hasPermission("chatsync.spy")) {
            pSender.sendMessage(color(t(pSender, "commands.socialspy.no_permission")));
            return true;
        }
        UUID uuid = pSender.getUniqueId();
        if (socialSpy.remove(uuid)) {
            pSender.sendMessage(color(t(pSender, "commands.socialspy.disabled")));
        } else {
            socialSpy.add(uuid);
            pSender.sendMessage(color(t(pSender, "commands.socialspy.enabled")));
        }
        return true;
    }

    // ── /me ──────────────────────────────────────────────────────

    private boolean cmdMe(CommandSender sender, String[] args) {
        if (!(sender instanceof Player pSender)) { sender.sendMessage("Players only."); return true; }

        String permission = getConfig().getString("commands.me.permission", "chatsync.me");
        if (!pSender.hasPermission(permission)) {
            pSender.sendMessage(color(t(pSender, "me.no_permission")));
            return true;
        }
        if (args.length < 1) { pSender.sendMessage(color(t(pSender, "me.usage"))); return true; }

        if (liteBansHook != null && liteBansHook.isMuted(pSender)) {
            pSender.sendMessage(color(t(pSender, "chat.muted")));
            return true;
        }

        String rawMessage = joinArgs(args, 0);
        if (!pSender.hasPermission("chatsync.color")) rawMessage = stripColorCodes(rawMessage);

        checkAndNotifySpam(pSender, rawMessage, "me");

        String format = getConfig().getString("chat.me.format", "&7* %player% %message%");
        double radius = getConfig().getDouble("chat.me.radius", -1);

        if (radius < 0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(pSender) && isIgnoring(p, pSender)) continue;
                p.sendMessage(buildChatComponent(format, pSender, rawMessage, p));
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getWorld().equals(pSender.getWorld())) continue;
                if (p.getLocation().distance(pSender.getLocation()) > radius) continue;
                if (!p.equals(pSender) && isIgnoring(p, pSender)) continue;
                p.sendMessage(buildChatComponent(format, pSender, rawMessage, p));
            }
        }

        logToConsole("[Me] " + pSender.getName() + " " + rawMessage);
        logChat("[ME] " + pSender.getName() + " " + stripColorCodes(rawMessage));
        if (getConfig().getBoolean("stats.enabled", true)) {
            statsManager.record(pSender.getUniqueId(), pSender.getName(), ChatStatsManager.MessageType.ME);
        }
        sendToDiscord(pSender, "* " + pSender.getName() + " " + rawMessage, "me");
        return true;
    }

    // ── /clear ───────────────────────────────────────────────────

    private boolean cmdClear(CommandSender sender, String[] args) {
        String permission = getConfig().getString("commands.clear.permission", "chatsync.clear");
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(color(tAny(sender, "clear.no_permission")));
            return true;
        }

        int  lines     = getConfig().getInt("clear.lines", 150);
        long timeoutMs = Math.max(5, getConfig().getInt("clear.confirm_timeout", 15)) * 1000L;

        // /clear confirm   или   /clear <player> confirm
        if (args.length >= 1 && args[args.length - 1].equalsIgnoreCase("confirm")) {
            UUID key = senderKey(sender);
            PendingClear pending = pendingClears.get(key);
            if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
                pendingClears.remove(key);
                sender.sendMessage(color(tAny(sender, "clear.expired")));
                return true;
            }
            pendingClears.remove(key);
            performClear(sender, pending.target(), lines);
            return true;
        }

        // Первый запуск — запрашиваем подтверждение
        UUID   targetUuid = null;
        String targetName = null;
        if (args.length >= 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(color(tAny(sender, "pm.player_not_found").replace("%player%", args[0])));
                return true;
            }
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        }

        pendingClears.put(senderKey(sender), new PendingClear(targetUuid, System.currentTimeMillis() + timeoutMs));

        String confirmCommand = targetName != null ? "/clear " + targetName + " confirm" : "/clear confirm";
        String hintKey        = targetName != null ? "clear.confirm_hint_player" : "clear.confirm_hint";
        String hintText       = tAny(sender, hintKey).replace("%target%", targetName != null ? targetName : "");

        Component hint = color(hintText).clickEvent(ClickEvent.runCommand(confirmCommand));
        sender.sendMessage(hint);
        return true;
    }

    private void performClear(CommandSender sender, UUID target, int lines) {
        Component blank        = Component.text(" ");
        String    executorName = sender instanceof Player p ? p.getName() : "Console";

        if (target == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                for (int i = 0; i < lines; i++) p.sendMessage(blank);
            }
            String msg = tAny(sender, "clear.done_all").replace("%player%", executorName);
            if (getConfig().getBoolean("clear.broadcast_notice", true)) {
                for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(color(t(p, "clear.done_all").replace("%player%", executorName)));
            } else {
                sender.sendMessage(color(msg));
            }
            if (sender instanceof Player p2) coreProtectHook.logClear(p2, "all");
            logToConsole("[Clear] " + executorName + " cleared the chat for everyone.");
        } else {
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer == null) {
                sender.sendMessage(color(tAny(sender, "clear.expired")));
                return;
            }
            for (int i = 0; i < lines; i++) targetPlayer.sendMessage(blank);
            String msg = tAny(sender, "clear.done_player")
                    .replace("%target%", targetPlayer.getName())
                    .replace("%player%", executorName);
            sender.sendMessage(color(msg));
            if (sender instanceof Player p2) coreProtectHook.logClear(p2, targetPlayer.getName());
            logToConsole("[Clear] " + executorName + " cleared the chat for " + targetPlayer.getName() + ".");
        }
    }

    private void purgeExpiredClears() {
        long now = System.currentTimeMillis();
        pendingClears.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    private UUID senderKey(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId() : CONSOLE_UUID;
    }

    // ── /chatstats ───────────────────────────────────────────────

    private boolean cmdChatStats(CommandSender sender, String[] args) {
        String permission = getConfig().getString("commands.chatstats.permission", "chatsync.chatstats");
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(color(tAny(sender, "chatstats.no_permission")));
            return true;
        }

        // /chatstats reset <player|all> [confirm]
        if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
            return cmdChatStatsReset(sender, args);
        }

        if (args.length == 0) {
            int topSize = getConfig().getInt("stats.top_size", 10);
            List<Map.Entry<UUID, ChatStatsManager.PlayerStats>> top = statsManager.top(topSize);
            if (top.isEmpty()) {
                sender.sendMessage(color(tAny(sender, "chatstats.empty")));
                return true;
            }
            sender.sendMessage(color(tAny(sender, "chatstats.top_header")
                    .replace("%count%", String.valueOf(top.size()))));
            int rank = 1;
            for (Map.Entry<UUID, ChatStatsManager.PlayerStats> entry : top) {
                String name = statsManager.nameOf(entry.getKey());
                // если в кэше UUID — попробуем онлайн-имя
                Player online = Bukkit.getPlayer(entry.getKey());
                if (online != null) name = online.getName();
                String line = tAny(sender, "chatstats.top_entry")
                        .replace("%rank%", String.valueOf(rank++))
                        .replace("%total%", String.valueOf(entry.getValue().total()));
                sender.sendMessage(buildClickableNameLine(line, name, sender));
            }
            return true;
        }

        String targetName = args[0];
        boolean isSelf = sender instanceof Player p0 && p0.getName().equalsIgnoreCase(targetName);
        if (!isSelf) {
            String othersPermission = getConfig().getString("commands.chatstats.permission_others", "chatsync.chatstats.others");
            if (!sender.hasPermission(othersPermission)) {
                sender.sendMessage(color(tAny(sender, "chatstats.no_permission_others")));
                return true;
            }
        }

        ResolvedPlayer resolved = resolvePlayer(targetName);
        if (resolved == null) {
            sender.sendMessage(color(tAny(sender, "chatstats.no_data").replace("%player%", targetName)));
            return true;
        }
        ChatStatsManager.PlayerStats stats = statsManager.get(resolved.uuid());
        if (stats == null) {
            // Нет записей — показываем нули с кликабельным ником (игрок известен)
            stats = new ChatStatsManager.PlayerStats();
        }

        String displayName = resolved.name();
        Player online = Bukkit.getPlayer(resolved.uuid());
        if (online != null) displayName = online.getName();

        sender.sendMessage(buildClickableNameLine(
                tAny(sender, "chatstats.player_header"), displayName, sender));
        sender.sendMessage(color(tAny(sender, "chatstats.player_line")
                .replace("%global%", String.valueOf(stats.global))
                .replace("%local%", String.valueOf(stats.local))
                .replace("%pm%", String.valueOf(stats.pm))
                .replace("%me%", String.valueOf(stats.me))
                .replace("%broadcast%", String.valueOf(stats.broadcast))
                .replace("%total%", String.valueOf(stats.total()))));
        return true;
    }

    private boolean cmdChatStatsReset(CommandSender sender, String[] args) {
        String resetPerm = getConfig().getString("commands.chatstats.permission_reset", "chatsync.chatstats.reset");
        if (!sender.hasPermission(resetPerm)) {
            sender.sendMessage(color(tAny(sender, "chatstats.reset_no_permission")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(color(tAny(sender, "chatstats.reset_usage")));
            return true;
        }

        long timeoutMs = Math.max(5, getConfig().getInt("stats.reset_confirm_timeout", 15)) * 1000L;
        UUID key = senderKey(sender);

        // /chatstats reset all confirm
        if (args[1].equalsIgnoreCase("all")) {
            boolean confirm = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
            if (!confirm) {
                pendingStatsResets.put(key, System.currentTimeMillis() + timeoutMs);
                Component hint = color(tAny(sender, "chatstats.reset_all_confirm"))
                        .clickEvent(ClickEvent.runCommand("/chatstats reset all confirm"));
                sender.sendMessage(hint);
                return true;
            }
            Long expires = pendingStatsResets.remove(key);
            if (expires == null || expires < System.currentTimeMillis()) {
                sender.sendMessage(color(tAny(sender, "chatstats.reset_expired")));
                return true;
            }
            statsManager.resetAll();
            sender.sendMessage(color(tAny(sender, "chatstats.reset_all_done")));
            logToConsole("[ChatStats] " + (sender instanceof Player p ? p.getName() : "Console") + " reset ALL chat statistics.");
            return true;
        }

        // /chatstats reset <player>
        String targetName = args[1];
        ResolvedPlayer resolved = resolvePlayer(targetName);
        if (resolved == null) {
            // try offline by name cache
            UUID uuid = statsManager.findUuidByName(targetName);
            if (uuid == null) {
                sender.sendMessage(color(tAny(sender, "chatstats.no_data").replace("%player%", targetName)));
                return true;
            }
            resolved = new ResolvedPlayer(uuid, statsManager.nameOf(uuid));
        }

        boolean removed = statsManager.resetPlayer(resolved.uuid());
        if (removed) {
            sender.sendMessage(color(tAny(sender, "chatstats.reset_player_done").replace("%player%", resolved.name())));
            logToConsole("[ChatStats] " + (sender instanceof Player p ? p.getName() : "Console")
                    + " reset chat statistics for " + resolved.name() + ".");
        } else {
            sender.sendMessage(color(tAny(sender, "chatstats.no_data").replace("%player%", resolved.name())));
        }
        return true;
    }

    // ── /broadcast ───────────────────────────────────────────────

    private boolean cmdBroadcast(CommandSender sender, String[] args) {
        String permission = getConfig().getString("commands.broadcast.permission", "chatsync.broadcast");
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(color(tAny(sender, "broadcast.no_permission")));
            return true;
        }

        // /broadcast hide  — toggle «скрывать автора»
        if (args.length == 1 && args[0].equalsIgnoreCase("hide")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(color("&cТолько для игроков."));
                return true;
            }
            if (broadcastHideAuthor.remove(p.getUniqueId())) {
                p.sendMessage(color(tAny(sender, "broadcast.hide_off")));
            } else {
                broadcastHideAuthor.add(p.getUniqueId());
                p.sendMessage(color(tAny(sender, "broadcast.hide_on")));
            }
            return true;
        }

        // /broadcast preset list|set|remove ...
        if (args.length >= 1 && args[0].equalsIgnoreCase("preset")) {
            return cmdBroadcastPreset(sender, args);
        }

        if (args.length < 1) {
            sender.sendMessage(color(tAny(sender, "broadcast.usage")));
            var presets = getConfig().getConfigurationSection("broadcast.presets");
            if (presets != null && !presets.getKeys(false).isEmpty()) {
                sender.sendMessage(color("&7Пресеты: &f" + String.join("&7, &f", presets.getKeys(false))));
            }
            return true;
        }

        boolean forceHide = false;
        int msgStart = 0;
        // one-shot: /broadcast -h <msg|preset>  или  /broadcast hide <msg|preset>
        if (args[0].equalsIgnoreCase("-h") || args[0].equalsIgnoreCase("-hide")
                || args[0].equalsIgnoreCase("hide")) {
            forceHide = true;
            msgStart = 1;
            if (args.length < 2) {
                // /broadcast -h  → подсказка + список пресетов
                sender.sendMessage(color(tAny(sender, "broadcast.usage_hide")));
                var presetsHelp = getConfig().getConfigurationSection("broadcast.presets");
                if (presetsHelp != null && !presetsHelp.getKeys(false).isEmpty()) {
                    sender.sendMessage(color("&7Пресеты: &f" + String.join("&7, &f", presetsHelp.getKeys(false))));
                }
                return true;
            }
        }

        String rawMessage;
        var presets = getConfig().getConfigurationSection("broadcast.presets");
        // Пресет: /broadcast <key>  или  /broadcast -h <key>
        String maybeKey = args[msgStart];
        boolean isPreset = presets != null
                && args.length == msgStart + 1
                && presets.contains(maybeKey);
        if (isPreset) {
            rawMessage = presets.getString(maybeKey, "");
            if (rawMessage == null || rawMessage.isEmpty()) {
                sender.sendMessage(color(tAny(sender, "broadcast.preset_empty").replace("%preset%", maybeKey)));
                return true;
            }
        } else {
            rawMessage = joinArgs(args, msgStart);
        }

        if (rawMessage.isBlank()) {
            sender.sendMessage(color(tAny(sender, "broadcast.usage")));
            return true;
        }

        if (sender instanceof Player pSender && !pSender.hasPermission("chatsync.color")) {
            rawMessage = stripColorCodes(rawMessage);
        }

        String executorName = sender instanceof Player p ? p.getName() : "Console";
        boolean hideAuthor = forceHide
                || (sender instanceof Player p2 && broadcastHideAuthor.contains(p2.getUniqueId()))
                || !getConfig().getBoolean("broadcast.show_sender", true);

        // Оформление из lang/<locale>.yml (broadcast.lines / lines_hidden)
        boolean actionbar   = getConfig().getBoolean("broadcast.actionbar", false);
        boolean titleEnable = getConfig().getBoolean("broadcast.title.enable", false);
        String  titleText   = getConfig().getString("broadcast.title.text", "%message%").replace("%message%", rawMessage);
        String  subtitle    = getConfig().getString("broadcast.title.subtitle", "").replace("%message%", rawMessage);
        Component plainMsg  = color("&f" + rawMessage);

        for (Player p : Bukkit.getOnlinePlayers()) {
            java.util.List<String> lines = hideAuthor
                    ? tList(p, "broadcast.lines_hidden")
                    : tList(p, "broadcast.lines");
            if (lines == null || lines.isEmpty()) {
                String formatTpl = hideAuthor
                        ? getConfig().getString("broadcast.format_hidden", "&eAnnouncement")
                        : getConfig().getString("broadcast.format", "&eAnnouncement &ffrom &e%sender%");
                lines = java.util.List.of(" ", formatTpl, "&f%message%", " ");
            }
            for (String line : lines) {
                if (line == null) line = "";
                String withMsg = line
                        .replace("%message%", rawMessage)
                        .replace("%text%", rawMessage);
                if (!hideAuthor && (withMsg.contains("%sender%") || withMsg.contains("%player%"))) {
                    p.sendMessage(buildClickableNameLine(
                            withMsg.replace("%sender%", "%player%"),
                            executorName,
                            p));
                } else {
                    p.sendMessage(color(withMsg
                            .replace("%sender%", "")
                            .replace("%player%", "")));
                }
            }
            if (actionbar) p.sendActionBar(plainMsg);
            if (titleEnable) p.showTitle(Title.title(color(titleText), color(subtitle)));
            playCustomSound(p, "broadcast.sound");
        }

        logToConsole("[Broadcast] " + executorName + (hideAuthor ? " [hidden]" : "") + ": " + rawMessage);
        logChat("[BROADCAST] " + executorName + ": " + stripColorCodes(rawMessage));
        if (sender instanceof Player p) {
            if (getConfig().getBoolean("stats.enabled", true)) {
                statsManager.record(p.getUniqueId(), p.getName(), ChatStatsManager.MessageType.BROADCAST);
            }
            sendToDiscord(p, rawMessage, "broadcast");
        }
        return true;
    }

    /** /broadcast preset list|set <key> <text>|remove <key> */
    private boolean cmdBroadcastPreset(CommandSender sender, String[] args) {
        String presetPerm = getConfig().getString("commands.broadcast.permission_preset", "chatsync.broadcast.preset");
        if (!sender.hasPermission(presetPerm) && !sender.hasPermission(
                getConfig().getString("commands.broadcast.permission", "chatsync.broadcast"))) {
            sender.sendMessage(color(tAny(sender, "broadcast.no_permission")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(color(tAny(sender, "broadcast.preset_usage")));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                var section = getConfig().getConfigurationSection("broadcast.presets");
                if (section == null || section.getKeys(false).isEmpty()) {
                    sender.sendMessage(color(tAny(sender, "broadcast.preset_empty_list")));
                    return true;
                }
                sender.sendMessage(color(tAny(sender, "broadcast.preset_list_header")));
                for (String key : section.getKeys(false)) {
                    String text = section.getString(key, "");
                    sender.sendMessage(color("&8• &e" + key + " &8→ &f" + text));
                }
                return true;
            }
            case "set", "add", "create" -> {
                if (args.length < 4) {
                    sender.sendMessage(color(tAny(sender, "broadcast.preset_usage")));
                    return true;
                }
                String key = args[2].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
                if (key.isEmpty()) {
                    sender.sendMessage(color(tAny(sender, "broadcast.preset_invalid_key")));
                    return true;
                }
                String text = joinArgs(args, 3);
                getConfig().set("broadcast.presets." + key, text);
                saveConfig();
                sender.sendMessage(color(tAny(sender, "broadcast.preset_saved")
                        .replace("%preset%", key)
                        .replace("%message%", text)));
                return true;
            }
            case "remove", "delete", "del" -> {
                if (args.length < 3) {
                    sender.sendMessage(color(tAny(sender, "broadcast.preset_usage")));
                    return true;
                }
                String key = args[2];
                if (!getConfig().contains("broadcast.presets." + key)) {
                    sender.sendMessage(color(tAny(sender, "broadcast.preset_not_found").replace("%preset%", key)));
                    return true;
                }
                getConfig().set("broadcast.presets." + key, null);
                saveConfig();
                sender.sendMessage(color(tAny(sender, "broadcast.preset_removed").replace("%preset%", key)));
                return true;
            }
            default -> {
                sender.sendMessage(color(tAny(sender, "broadcast.preset_usage")));
                return true;
            }
        }
    }


    // ── /playtime ────────────────────────────────────────────────

    private boolean cmdPlaytime(CommandSender sender, String[] args) {
        if (!getConfig().getBoolean("playtime.enabled", true)) {
            sender.sendMessage(color(tAny(sender, "playtime.disabled")));
            return true;
        }
        String permission = getConfig().getString("commands.playtime.permission", "chatsync.playtime");
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(color(tAny(sender, "playtime.no_permission")));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player pSender)) {
                sender.sendMessage(color(tAny(sender, "playtime.usage")));
                return true;
            }
            long seconds = playtimeManager.getPlaytimeSeconds(pSender.getUniqueId());
            sender.sendMessage(color(tAny(sender, "playtime.self")
                    .replace("%time%", formatDuration(seconds, sender))));
            return true;
        }

        ResolvedPlayer resolved = resolvePlayer(args[0]);
        if (resolved == null) {
            // ник неизвестен — без клика
            sender.sendMessage(color(tAny(sender, "playtime.no_data").replace("%player%", args[0])));
            return true;
        }
        // Игрок заходил на сервер (OfflinePlayer) или есть в кэше плагина —
        // всегда показываем время (0, если плагин ещё не видел выход) с кликабельным ником.
        long seconds = playtimeManager.getPlaytimeSeconds(resolved.uuid());
        // Если в кэше пусто, но игрок когда-то заходил — подтянем имя в кэш для будущих запросов
        if (!playtimeManager.hasData(resolved.uuid())) {
            playtimeManager.rememberName(resolved.uuid(), resolved.name());
        }
        String line = tAny(sender, "playtime.other")
                .replace("%time%", formatDuration(seconds, sender));
        sender.sendMessage(buildClickableNameLine(line, resolved.name(), sender));
        return true;
    }

    private boolean cmdPlaytimeTop(CommandSender sender, String[] args) {
        if (!getConfig().getBoolean("playtime.enabled", true)) {
            sender.sendMessage(color(tAny(sender, "playtime.disabled")));
            return true;
        }
        String permission = getConfig().getString("commands.playtime.playtimetop.permission", "chatsync.playtimetop");
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(color(tAny(sender, "playtime.top_no_permission")));
            return true;
        }
        int topSize = getConfig().getInt("playtime.top_size", 10);
        List<Map.Entry<UUID, Long>> top = playtimeManager.top(topSize);
        sender.sendMessage(color(tAny(sender, "playtime.top_header").replace("%count%", String.valueOf(top.size()))));
        int rank = 1;
        for (Map.Entry<UUID, Long> entry : top) {
            String name = playtimeManager.nameOf(entry.getKey());
            String line = tAny(sender, "playtime.top_entry")
                    .replace("%rank%", String.valueOf(rank++))
                    .replace("%time%", formatDuration(entry.getValue(), sender));
            sender.sendMessage(buildClickableNameLine(line, name, sender));
        }
        return true;
    }

    private boolean cmdLastSeen(CommandSender sender, String[] args) {
        if (!getConfig().getBoolean("playtime.enabled", true)) {
            sender.sendMessage(color(tAny(sender, "playtime.disabled")));
            return true;
        }
        String permission = getConfig().getString("commands.playtime.lastseen.permission", "chatsync.lastseen");
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(color(tAny(sender, "playtime.lastseen_no_permission")));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(color(tAny(sender, "playtime.lastseen_usage")));
            return true;
        }

        ResolvedPlayer resolved = resolvePlayer(args[0]);
        if (resolved == null) {
            sender.sendMessage(color(tAny(sender, "playtime.no_data").replace("%player%", args[0])));
            return true;
        }

        Player online = Bukkit.getPlayer(resolved.uuid());
        if (online != null && online.isOnline()) {
            sender.sendMessage(buildClickableNameLine(
                    tAny(sender, "playtime.lastseen_online"), resolved.name(), sender));
            return true;
        }

        Long logout = playtimeManager.getLastLogout(resolved.uuid());
        Long login  = playtimeManager.getLastLogin(resolved.uuid());
        if (logout == null && login == null) {
            // Игрок известен (hasPlayedBefore), но плагин не видел вход/выход —
            // всё равно кликабельный ник + «неизвестно»
            String line = tAny(sender, "playtime.lastseen")
                    .replace("%when%", tAny(sender, "playtime.unknown_time"));
            sender.sendMessage(buildClickableNameLine(line, resolved.name(), sender));
            return true;
        }

        long when = logout != null ? logout : login;
        String line = tAny(sender, "playtime.lastseen")
                .replace("%when%", formatTimestamp(when, sender));
        sender.sendMessage(buildClickableNameLine(line, resolved.name(), sender));
        return true;
    }

    /** UUID + отображаемое имя, найденные по нику (онлайн → кэш → OfflinePlayer). */
    private record ResolvedPlayer(UUID uuid, String name) {}

    private ResolvedPlayer resolvePlayer(String input) {
        if (input == null || input.isBlank()) return null;

        // 1) онлайн (точное имя)
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return new ResolvedPlayer(online.getUniqueId(), online.getName());

        // 2) онлайн (частичное / без учёта регистра)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(input)) {
                return new ResolvedPlayer(p.getUniqueId(), p.getName());
            }
        }

        // 3) кэш chatstats
        if (statsManager != null) {
            UUID uuid = statsManager.findUuidByName(input);
            if (uuid != null) return new ResolvedPlayer(uuid, statsManager.nameOf(uuid));
        }

        // 4) кэш playtime (по имени, без тяжёлого top())
        if (playtimeManager != null) {
            UUID uuid = playtimeManager.findUuidByName(input);
            if (uuid != null) return new ResolvedPlayer(uuid, playtimeManager.nameOf(uuid));
        }

        // 5) OfflinePlayer (только если реально заходил на сервер)
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(input);
        if (off.hasPlayedBefore() || off.isOnline()) {
            String name = off.getName() != null ? off.getName() : input;
            // кэшируем ник, чтобы дальше ник был кликабельным и находился быстрее
            if (playtimeManager != null) playtimeManager.rememberName(off.getUniqueId(), name);
            return new ResolvedPlayer(off.getUniqueId(), name);
        }
        return null;
    }

    /**
     * Формат как у TAB / %statistic_hours_played%, но с минутами:
     * 12ч 34м  ·  при &lt; 1ч — 45м  ·  при днях — 2д 5ч 12м
     * (секунды только если меньше минуты)
     */
    private String formatDuration(long totalSeconds, CommandSender sender) {
        if (totalSeconds < 0) totalSeconds = 0;
        long days = totalSeconds / 86400;
        long hoursTotal = totalSeconds / 3600;          // всего часов (как hours_played)
        long hours = (totalSeconds % 86400) / 3600;     // часов в текущих сутках
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        String d = tAny(sender, "duration.days");
        String h = tAny(sender, "duration.hours");
        String m = tAny(sender, "duration.minutes");
        String s = tAny(sender, "duration.seconds");

        if (totalSeconds < 60) {
            return seconds + s;
        }
        if (days > 0) {
            return days + d + " " + hours + h + " " + minutes + m;
        }
        if (hoursTotal > 0) {
            // 12ч 34м — как TAB hours + минуты
            return hoursTotal + h + " " + minutes + m;
        }
        return minutes + m;
    }

    private String formatTimestamp(long epochMs, CommandSender sender) {
        if (epochMs <= 0) return tAny(sender, "playtime.unknown_time");
        java.time.Instant instant = java.time.Instant.ofEpochMilli(epochMs);
        java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").format(zdt);
    }

    /**
     * Собирает строку с кликабельным ником: в template должен остаться плейсхолдер %player%.
     * Клик подставляет /msg <ник> в чат. Hover — настраиваемый (см. hover.* в config).
     */
    private Component buildClickableNameLine(String template, String playerName, CommandSender viewer) {
        final String PH = "%player%";
        int idx = template.indexOf(PH);
        if (idx < 0) {
            idx = template.indexOf(playerName);
            if (idx < 0) return color(template);
            String before = template.substring(0, idx);
            String after  = template.substring(idx + playerName.length());
            Component nameComp = clickableName(extractTrailingColor(before) + playerName, playerName, null, viewer);
            return Component.text()
                    .append(color(before))
                    .append(nameComp)
                    .append(color(after))
                    .build();
        }
        String before = template.substring(0, idx);
        String after  = template.substring(idx + PH.length());
        Component nameComp = clickableName(extractTrailingColor(before) + playerName, playerName, null, viewer);
        return Component.text()
                .append(color(before))
                .append(nameComp)
                .append(color(after))
                .build();
    }

    /** Кликабельный ник с hover (playtime опционально). */
    Component clickableName(String coloredName, String playerName, UUID uuid, CommandSender viewer) {
        Component nameComp = color(coloredName)
                .clickEvent(ClickEvent.suggestCommand("/msg " + playerName + " "));
        if (getConfig().getBoolean("hover.enabled", true)) {
            nameComp = nameComp.hoverEvent(HoverEvent.showText(buildHoverComponent(viewer, playerName, uuid)));
        }
        return nameComp;
    }

    /**
     * Текст hover при наведении на ник.
     * Плейсхолдеры: %player%, %playtime%, %playtime_seconds%
     * Многострочность: \\n в lang-строке.
     */
    Component buildHoverComponent(CommandSender viewer, String playerName, UUID uuid) {
        boolean showPt = getConfig().getBoolean("hover.show_playtime", true)
                && getConfig().getBoolean("playtime.enabled", true)
                && playtimeManager != null;

        String key = showPt ? "messages.join_hover_playtime" : "messages.join_hover";
        String raw = tAny(viewer, key).replace("%player%", playerName);

        if (showPt) {
            if (uuid == null) {
                Player online = Bukkit.getPlayerExact(playerName);
                if (online != null) {
                    uuid = online.getUniqueId();
                } else {
                    uuid = playtimeManager.findUuidByName(playerName);
                }
            }
            long sec = uuid != null ? playtimeManager.getPlaytimeSeconds(uuid) : 0L;
            raw = raw
                    .replace("%playtime%", formatDuration(sec, viewer))
                    .replace("%playtime_seconds%", String.valueOf(sec));
        } else {
            raw = raw.replace("%playtime%", "—").replace("%playtime_seconds%", "0");
        }

        // Многострочный hover: разбиваем по \n
        String[] parts = raw.split("\\\\n|\\n");
        if (parts.length == 1) return color(parts[0]);
        net.kyori.adventure.text.TextComponent.Builder b = Component.text();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) b.append(Component.newline());
            b.append(color(parts[i]));
        }
        return b.build();
    }

    // ──────────────────────────────────────────────────────────────
    //  Tab complete
    // ──────────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        if (args.length == 1 && (name.equals("msg") || name.equals("ignore") || name.equals("clear")
                || name.equals("chatstats") || name.equals("playtime") || name.equals("lastseen"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase()))
                    names.add(p.getName());
            if (name.equals("clear")) names.add("confirm");
            if (name.equals("chatstats") && "reset".startsWith(args[0].toLowerCase())) names.add("reset");
            return names;
        }
        if (name.equals("clear") && args.length == 2) return List.of("confirm");
        if (name.equals("team")) {
            if (args.length == 1) {
                List<String> subs = List.of("create", "invite", "accept", "deny", "leave", "kick", "disband", "chat", "name", "color", "symbol", "info", "transfer", "promote", "demote");
                List<String> out = new ArrayList<>();
                for (String s : subs)
                    if (s.startsWith(args[0].toLowerCase())) out.add(s);
                return out;
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick")
                    || args[0].equalsIgnoreCase("transfer") || args[0].equalsIgnoreCase("promote") || args[0].equalsIgnoreCase("demote"))) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers())
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase()))
                        names.add(p.getName());
                return names;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("color")) {
                List<String> codes = List.of(
                        "0","1","2","3","4","5","6","7","8","9",
                        "a","b","c","d","e","f",
                        "k","l","m","n","o","r",
                        "c&l","a&l","e&l","b&l","6&l");
                List<String> out = new ArrayList<>();
                String pref = args[1].toLowerCase();
                for (String s : codes) if (s.startsWith(pref)) out.add(s);
                return out;
            }
        }
        if (name.equals("chatsync") && args.length == 1) return List.of("reload");
        if (name.equals("chatstats") && args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            List<String> list = new ArrayList<>();
            list.add("all");
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase()))
                    list.add(p.getName());
            return list;
        }
        if (name.equals("chatstats") && args.length == 3
                && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("all")) {
            return List.of("confirm");
        }
        if (name.equals("broadcast")) {
            if (args.length == 1) {
                List<String> keys = new ArrayList<>();
                String pref = args[0].toLowerCase(Locale.ROOT);
                for (String s : List.of("hide", "preset", "-h")) {
                    if (s.startsWith(pref)) keys.add(s);
                }
                var presets = getConfig().getConfigurationSection("broadcast.presets");
                if (presets != null) {
                    for (String k : presets.getKeys(false)) {
                        if (k.toLowerCase(Locale.ROOT).startsWith(pref)) keys.add(k);
                    }
                }
                return keys;
            }
            // /broadcast -h <preset> — tab по пресетам
            if (args.length == 2 && (args[0].equalsIgnoreCase("-h")
                    || args[0].equalsIgnoreCase("-hide") || args[0].equalsIgnoreCase("hide"))) {
                var presets = getConfig().getConfigurationSection("broadcast.presets");
                if (presets != null) {
                    List<String> keys = new ArrayList<>();
                    String pref = args[1].toLowerCase(Locale.ROOT);
                    for (String k : presets.getKeys(false)) {
                        if (k.toLowerCase(Locale.ROOT).startsWith(pref)) keys.add(k);
                    }
                    return keys;
                }
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("preset")) {
                List<String> acts = new ArrayList<>();
                String pref = args[1].toLowerCase(Locale.ROOT);
                for (String s : List.of("list", "set", "remove")) {
                    if (s.startsWith(pref)) acts.add(s);
                }
                return acts;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("preset")
                    && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("set"))) {
                var presets = getConfig().getConfigurationSection("broadcast.presets");
                if (presets != null) {
                    List<String> keys = new ArrayList<>();
                    String pref = args[2].toLowerCase(Locale.ROOT);
                    for (String k : presets.getKeys(false)) {
                        if (k.toLowerCase(Locale.ROOT).startsWith(pref)) keys.add(k);
                    }
                    return keys;
                }
            }
        }
        return List.of();
    }

    // ──────────────────────────────────────────────────────────────
    //  PM
    // ──────────────────────────────────────────────────────────────

    private void sendPM(Player from, Player to, String message) {
        if (liteBansHook != null && liteBansHook.isMuted(from)) {
            from.sendMessage(color(t(from, "chat.muted")));
            return;
        }

        checkAndNotifySpam(from, message, "pm");

        String senderFmt   = t(from, "pm.format_sender").replace("%message%", message);
        String receiverFmt = t(to,   "pm.format_receiver").replace("%message%", message);
        String hoverFrom   = t(from, "messages.join_hover").replace("%player%", to.getName());
        String hoverTo     = t(to,   "messages.join_hover").replace("%player%", from.getName());

        from.sendMessage(formatPM(senderFmt, from, to,   hoverFrom));
        to.sendMessage(formatPM(receiverFmt, to,   from, hoverTo));

        lastMessaged.put(from.getUniqueId(), to.getUniqueId());
        lastMessaged.put(to.getUniqueId(), from.getUniqueId());

        playCustomSound(to, "pm.sound");

        // SocialSpy
        if (tog("socialspy")) {
            String spyFmt = getConfig().getString("pm.format_spy",
                    "&8[SPY] &7%sender% &8→ &7%receiver%&8: &7%message%")
                    .replace("%receiver%", to.getName())
                    .replace("%message%",  message)
                    .replace("%sender%", "%player%");
            for (UUID uid : socialSpy) {
                Player spy = Bukkit.getPlayer(uid);
                if (spy != null && !spy.equals(from) && !spy.equals(to))
                    spy.sendMessage(buildClickableNameLine(spyFmt, from.getName(), spy));
            }
        }
        logToConsole("[PM] " + from.getName() + " → " + to.getName() + ": " + message);
        if (getConfig().getBoolean("stats.enabled", true)) {
            statsManager.record(from.getUniqueId(), from.getName(), ChatStatsManager.MessageType.PM);
        }
        logChat("[PM] " + from.getName() + " -> " + to.getName() + ": " + stripColorCodes(message));
    }

    // ──────────────────────────────────────────────────────────────
    //  Component builders
    // ──────────────────────────────────────────────────────────────

    private Component buildChatComponent(String format, Player sender, String rawMessage, Player viewer) {
        if (format == null) format = "&7%player%&7: &f%message%";
        // {username-color} / %username-color% — цвет ника из конфига
        String userColor = getConfig().getString("chat.username_color", "&7");
        if (userColor == null || userColor.isEmpty()) userColor = "&7";
        format = format.replace("{username-color}", userColor).replace("%username-color%", userColor);

        // Собираем компонент по сегментам, чтобы цвет %message% и %player% не терялся
        // при раздельной десериализации (стиль Adventure не переносится между append).
        java.util.List<String> tokens = new java.util.ArrayList<>();
        String[] markers = {"%head%", "%player%", "%message%"};
        String rest = format;
        while (true) {
            int best = -1;
            String bestM = null;
            for (String m : markers) {
                int i = rest.indexOf(m);
                if (i >= 0 && (best < 0 || i < best)) {
                    best = i;
                    bestM = m;
                }
            }
            if (bestM == null) {
                if (!rest.isEmpty()) tokens.add(rest);
                break;
            }
            if (best > 0) tokens.add(rest.substring(0, best));
            tokens.add(bestM);
            rest = rest.substring(best + bestM.length());
        }

        Component result = Component.empty();
        StringBuilder literalSoFar = new StringBuilder();
        for (String tok : tokens) {
            if (tok.equals("%head%")) {
                result = result.append(buildHeadComponent(sender));
            } else if (tok.equals("%player%")) {
                String col = extractTrailingColor(literalSoFar.toString());
                if (col.isEmpty()) col = userColor;
                Component nameComp = clickableName(
                        col + sender.getName(),
                        sender.getName(),
                        sender.getUniqueId(),
                        viewer);
                result = result.append(nameComp);
                literalSoFar.append(col).append(sender.getName());
            } else if (tok.equals("%message%")) {
                String col = extractTrailingColor(literalSoFar.toString());
                // Если в самом сообщении уже есть цветовые коды — оставляем;
                // иначе применяем цвет из формата (перед %message%).
                String msg = rawMessage == null ? "" : rawMessage;
                if (!msg.isEmpty() && msg.charAt(0) != '&' && msg.charAt(0) != '§') {
                    msg = col + msg;
                }
                result = result.append(LEGACY.deserialize(
                        resolvePlaceholders(msg, sender)));
            } else {
                String lit = resolvePlaceholders(tok, sender);
                literalSoFar.append(lit);
                if (!lit.isEmpty()) {
                    result = result.append(LEGACY.deserialize(lit));
                }
            }
        }
        return result;
    }

    /**
     * Голова игрока в чате (если клиент/Paper поддерживает object component).
     * Иначе — fallback-текст из конфига (можно оставить пустым).
     */
    private Component buildHeadComponent(Player player) {
        if (!getConfig().getBoolean("chat.heads.enabled", false)) {
            return Component.empty();
        }
        try {
            // Adventure PlayerHead object (newer Paper / 1.21.6+)
            Class<?> phClass = Class.forName("net.kyori.adventure.text.object.PlayerHeadObjectContents");
            Object builder = phClass.getMethod("playerHead").invoke(null);
            try {
                builder.getClass().getMethod("name", String.class).invoke(builder, player.getName());
            } catch (NoSuchMethodException ignored) {}
            try {
                builder.getClass().getMethod("id", java.util.UUID.class).invoke(builder, player.getUniqueId());
            } catch (NoSuchMethodException ignored) {}
            Object contents = builder.getClass().getMethod("build").invoke(builder);
            java.lang.reflect.Method objectMethod = Component.class.getMethod("object",
                    Class.forName("net.kyori.adventure.text.object.ObjectContents"));
            return (Component) objectMethod.invoke(null, contents);
        } catch (Throwable t) {
            String fb = getConfig().getString("chat.heads.fallback", "");
            if (fb == null || fb.isEmpty()) return Component.empty();
            return LEGACY.deserialize(fb);
        }
    }


    private Component buildNameComponent(String template, Player player, String ignoredHover) {
        final String PH = "%player%";
        int idx = template.indexOf(PH);
        if (idx == -1)
            return LEGACY.deserialize(resolvePlaceholders(template, player));

        String before = resolvePlaceholders(template.substring(0, idx), player);
        String after  = resolvePlaceholders(template.substring(idx + PH.length()), player);

        Component nameComp = clickableName(
                extractTrailingColor(before) + player.getName(),
                player.getName(),
                player.getUniqueId(),
                player);

        return Component.text()
                .append(LEGACY.deserialize(before))
                .append(nameComp)
                .append(LEGACY.deserialize(after))
                .build();
    }

    private Component formatPM(String format, Player target, Player other, String ignoredHover) {
        int idx = format.indexOf("%sender%");
        String ph = "%sender%";
        if (idx == -1) { idx = format.indexOf("%receiver%"); ph = "%receiver%"; }
        if (idx == -1)
            return LEGACY.deserialize(resolvePlaceholders(format, target));

        String before = format.substring(0, idx);
        String after  = format.substring(idx + ph.length());

        Component nameComp = clickableName(
                extractTrailingColor(before) + other.getName(),
                other.getName(),
                other.getUniqueId(),
                target);

        return Component.text()
                .append(LEGACY.deserialize(resolvePlaceholders(before, target)))
                .append(nameComp)
                .append(LEGACY.deserialize(resolvePlaceholders(after, target)))
                .build();
    }

    // ──────────────────────────────────────────────────────────────
    //  Discord
    // ──────────────────────────────────────────────────────────────

    private void sendToDiscord(Player sender, String message, String channel) {
        if (!getConfig().getBoolean("discord.enabled", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) return;
        final String clean = stripColorCodes(message);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Class<?> dsrv   = Class.forName("github.scarsz.discordsrv.DiscordSRV");
                Object   plugin = dsrv.getMethod("getPlugin").invoke(null);
                dsrv.getMethod("processChatMessage", Player.class, String.class, String.class, boolean.class)
                        .invoke(plugin, sender, clean, channel, false);
            } catch (Exception e) {
                getLogger().warning("DiscordSRV hook failed: " + e.getMessage());
            }
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────

    private String resolvePlaceholders(String text, Player player) {
        if (text == null) return "";
        text = text.replace("%player%", player.getName());

        boolean papiHandled = false;
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method m = papi.getMethod("setPlaceholders", Player.class, String.class);
                text = (String) m.invoke(null, player, text);
                papiHandled = true;
            } catch (Exception ignored) {}
        }

        // Фолбэк на прямой LuckPerms API, если PlaceholderAPI не установлен
        if (!papiHandled
                && getConfig().getBoolean("integrations.luckperms.use_api_directly", true)
                && luckPermsHook != null && luckPermsHook.isAvailable()) {
            text = text.replace("%luckperms_prefix%", luckPermsHook.getPrefix(player));
            text = text.replace("%luckperms_suffix%", luckPermsHook.getSuffix(player));
        }
        return text;
    }

    private void playCustomSound(Player player, String path) {
        if (!getConfig().getBoolean(path + ".enable", false)) return;
        try {
            Sound sound  = Sound.valueOf(getConfig().getString(path + ".name", "").toUpperCase());
            float volume = (float) getConfig().getDouble(path + ".volume", 1.0);
            float pitch  = (float) getConfig().getDouble(path + ".pitch",  1.0);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid sound in config: " + path + ".name");
        }
    }

    /**
     * Проверяет сообщение на спам (повтор одного текста, CAPS, флуд)
     * и уведомляет админов с правом chatsync.spam.notify.
     * Не блокирует сообщение — только алерт стаффу.
     */
    private void checkAndNotifySpam(Player player, String message, String channel) {
        if (!getConfig().getBoolean("spam.notify.enabled", true)) return;
        if (player.hasPermission("chatsync.spam.bypass")) return;

        String plain = stripColorCodes(message).trim();
        if (plain.isEmpty()) return;

        long now = System.currentTimeMillis();
        int windowSec = getConfig().getInt("spam.notify.window_seconds", 10);
        int sameLimit = getConfig().getInt("spam.notify.same_message_limit", 3);
        int floodLimit = getConfig().getInt("spam.notify.flood_limit", 6);
        double capsRatio = getConfig().getDouble("spam.notify.caps_ratio", 0.7);
        int minCapsLen = getConfig().getInt("spam.notify.caps_min_length", 6);
        long windowMs = windowSec * 1000L;

        java.util.Deque<SpamEntry> deque = recentMessages.computeIfAbsent(
                player.getUniqueId(), k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        deque.addLast(new SpamEntry(plain.toLowerCase(Locale.ROOT), now, channel));
        // prune old
        while (!deque.isEmpty() && now - deque.peekFirst().time() > windowMs) {
            deque.pollFirst();
        }

        String reason = null;

        // 1) Same message spam
        long sameCount = deque.stream()
                .filter(e -> e.text().equals(plain.toLowerCase(Locale.ROOT)))
                .count();
        if (sameCount >= sameLimit) {
            reason = "same";
        }

        // 2) Caps spam ("капсом")
        if (reason == null && plain.length() >= minCapsLen) {
            int letters = 0, upper = 0;
            for (char c : plain.toCharArray()) {
                if (Character.isLetter(c)) {
                    letters++;
                    if (Character.isUpperCase(c)) upper++;
                }
            }
            if (letters > 0 && (double) upper / letters >= capsRatio) {
                reason = "caps";
            }
        }

        // 3) General flood
        if (reason == null && deque.size() >= floodLimit) {
            reason = "flood";
        }

        if (reason == null) return;

        String channelLabel = switch (channel) {
            case "global" -> "глобальный чат";
            case "local"  -> "локальный чат";
            case "me"     -> "/me";
            case "pm"     -> "ЛС";
            case "broadcast" -> "объявление";
            default -> channel;
        };

        String reasonLabel = switch (reason) {
            case "same"  -> "повторяет одно сообщение";
            case "caps"  -> "пишет КАПСОМ";
            case "flood" -> "флудит";
            default -> "спамит";
        };

        // Используем только разрешённые цвета: &a &c &7 &8 &f &e и &l
        String alert = "&8[&c&lSPAM&8] &e" + player.getName()
                + " &7" + reasonLabel
                + " &8(&f" + channelLabel + "&8)&7: &f"
                + (plain.length() > 40 ? plain.substring(0, 40) + "…" : plain);

        String perm = getConfig().getString("spam.notify.permission", "chatsync.spam.notify");
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(perm) && !staff.equals(player)) {
                staff.sendMessage(color(alert));
            }
        }
        logToConsole("[SPAM] " + player.getName() + " (" + reason + "/" + channel + "): " + plain);
    }

    private void logToConsole(String message) {
        Bukkit.getConsoleSender().sendMessage(
                LEGACY.deserialize(message));
    }

    /** Пишет строку в асинхронный файловый лог чата, если это включено в config.yml. */
    private void logChat(String line) {
        if (chatLogger != null && getConfig().getBoolean("logging.enabled", true)) {
            chatLogger.log(line);
        }
    }

    /** Last legacy color/format code in text (&x or §x), skipping trailing spaces. */
    private String extractTrailingColor(String text) {
        if (text == null || text.isEmpty()) return "&f";
        String s = text.replace('§', '&');
        // walk from end, skip spaces
        int i = s.length() - 1;
        while (i >= 0 && s.charAt(i) == ' ') i--;
        // collect consecutive &x codes at the end (e.g. &c&l)
        StringBuilder codes = new StringBuilder();
        while (i >= 1) {
            if (s.charAt(i - 1) == '&') {
                char c = Character.toLowerCase(s.charAt(i));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || "klmnor".indexOf(c) >= 0) {
                    codes.insert(0, "&" + c);
                    i -= 2;
                    while (i >= 0 && s.charAt(i) == ' ') i--;
                    continue;
                }
            }
            break;
        }
        return codes.length() > 0 ? codes.toString() : "&f";
    }

    private boolean isIgnoring(Player who, Player whom) {
        Set<UUID> list = ignoreList.get(who.getUniqueId());
        return list != null && list.contains(whom.getUniqueId());
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) { if (i > from) sb.append(' '); sb.append(args[i]); }
        return sb.toString();
    }

    private String stripColorCodes(String text) {
        if (text == null) return "";
        // legacy &x and hex &#RRGGBB / &#RGB (also §)
        String s = text.replace('§', '&');
        s = s.replaceAll("(?i)&#[0-9a-f]{6}", "");
        s = s.replaceAll("(?i)&#[0-9a-f]{3}", "");
        s = s.replaceAll("(?i)&x(&[0-9a-f]){6}", "");
        s = s.replaceAll("(?i)&[0-9a-fk-or]", "");
        return s;
    }

    /** Читает toggles.<key> из config.yml, по умолчанию true */
    private boolean tog(String key) {
        return getConfig().getBoolean("toggles." + key, true);
    }

    Component color(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return LEGACY.deserialize(text);
    }

    // ── Team / party ──────────────────────────────────────────

        private void sendTeamChat(Player sender, String message) {
        if (teamManager == null) return;
        TeamManager.Team team = teamManager.getTeamOf(sender.getUniqueId());
        if (team == null) {
            sender.sendMessage(color(t(sender, "team.no_team")));
            return;
        }
        sendTeamChat(sender, team, message);
    }

    private void sendTeamChat(Player sender, TeamManager.Team team, String message) {
        if (teamManager == null || !teamManager.enabled()) {
            sender.sendMessage(color(t(sender, "team.disabled")));
            return;
        }
        if (team == null || !team.members.contains(sender.getUniqueId())) {
            sender.sendMessage(color(t(sender, "team.no_team")));
            return;
        }
        if (liteBansHook != null && liteBansHook.isMuted(sender)) {
            sender.sendMessage(color(t(sender, "chat.muted")));
            return;
        }
        if (!sender.hasPermission("chatsync.color")) message = stripColorCodes(message);

        String format = getConfig().getString("teams.format",
                "&8[%color%%team%&8] &f%player%&7: &f%message%");
        if (format == null || !format.contains("%player%")) {
            format = "&8[%color%%team%&8] &f%player%&7: &f%message%";
        }
        String out = format
                .replace("%color%", team.color != null ? team.color : "&b")
                .replace("%team%", team.name != null ? team.name : "team")
                .replace("%symbol%", team.symbol != null ? team.symbol : "")
                .replace("%message%", message);
        // ensure placeholder for clickable name
        if (!out.contains("%player%")) {
            out = "&8[%team%] &f%player%&7: &f%message%"
                    .replace("%team%", team.name)
                    .replace("%message%", message);
        }

        for (java.util.UUID id : team.members) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            Component line = buildClickableNameLine(out, sender.getName(), p);
            p.sendMessage(line);
        }
        if (chatLogger != null) {
            chatLogger.log("[TEAM:" + team.name + "] " + sender.getName() + ": " + message);
        }
    }


    private boolean cmdTeam(CommandSender sender, String[] args) {
        if (teamManager == null || !teamManager.enabled()) {
            sender.sendMessage(color(tAny(sender, "team.disabled")));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(tAny(sender, "team.players_only")));
            return true;
        }
        if (!player.hasPermission(getConfig().getString("teams.permission", "chatsync.team"))) {
            player.sendMessage(color(t(player, "team.no_permission")));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(color(t(player, "team.usage")));
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> {
                if (!player.hasPermission(getConfig().getString("teams.permission_create", "chatsync.team.create"))) {
                    player.sendMessage(color(t(player, "team.no_permission")));
                    return true;
                }
                if (args.length < 2) { player.sendMessage(color(t(player, "team.usage_create"))); return true; }
                String name = args[1];
                String r = teamManager.create(player, name);
                switch (r) {
                    case "ok" -> {
                        TeamManager.Team nt = teamManager.getTeamOf(player.getUniqueId());
                        String sym = nt != null && nt.symbol != null ? nt.symbol : "#";
                        player.sendMessage(color(t(player, "team.created")
                                .replace("%team%", name)
                                .replace("%symbol%", sym)));
                    }
                    case "already_in" -> player.sendMessage(color(t(player, "team.already_in")));
                    case "max_teams" -> player.sendMessage(color(t(player, "team.max_teams")));
                    case "name_taken" -> player.sendMessage(color(t(player, "team.name_taken")));
                    case "name_long", "bad_name" -> player.sendMessage(color(t(player, "team.bad_name")));
                    default -> player.sendMessage(color(t(player, "team.disabled")));
                }
            }
            case "invite" -> {
                if (args.length < 2) { player.sendMessage(color(t(player, "team.usage_invite"))); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage(color(t(player, "pm.player_not_found").replace("%player%", args[1]))); return true; }
                String r = teamManager.invite(player, target);
                switch (r) {
                    case "ok" -> {
                        TeamManager.Team team = teamManager.getTeamOf(player.getUniqueId());
                        player.sendMessage(color(t(player, "team.invite_sent")
                                .replace("%player%", target.getName())
                                .replace("%team%", team != null ? team.name : "")));
                        // Clickable accept / deny
                        String invMsg = t(target, "team.invite_received")
                                .replace("%player%", player.getName())
                                .replace("%team%", team != null ? team.name : "");
                        Component base = color(invMsg + " ");
                        Component accept = color(t(target, "team.btn_accept"))
                                .clickEvent(ClickEvent.runCommand("/team accept"))
                                .hoverEvent(HoverEvent.showText(color(t(target, "team.btn_accept_hover"))));
                        Component deny = color(" " + t(target, "team.btn_deny"))
                                .clickEvent(ClickEvent.runCommand("/team deny"))
                                .hoverEvent(HoverEvent.showText(color(t(target, "team.btn_deny_hover"))));
                        target.sendMessage(base.append(accept).append(deny));
                    }
                    case "no_team" -> player.sendMessage(color(t(player, "team.no_team")));
                    case "not_owner" -> player.sendMessage(color(t(player, "team.not_owner")));
                    case "target_in_team" -> player.sendMessage(color(t(player, "team.target_in_team")));
                    case "full" -> player.sendMessage(color(t(player, "team.full")));
                    case "self" -> player.sendMessage(color(t(player, "team.cannot_self")));
                    default -> player.sendMessage(color(t(player, "team.no_team")));
                }
            }
            case "accept" -> {
                String r = teamManager.accept(player);
                switch (r) {
                    case "ok" -> {
                        TeamManager.Team team = teamManager.getTeamOf(player.getUniqueId());
                        player.sendMessage(color(t(player, "team.joined").replace("%team%", team != null ? team.name : "")));
                        if (team != null) {
                            for (java.util.UUID id : team.members) {
                                Player p = Bukkit.getPlayer(id);
                                if (p != null && !p.equals(player))
                                    p.sendMessage(color(t(p, "team.member_joined")
                                            .replace("%player%", player.getName())
                                            .replace("%team%", team.name)));
                            }
                        }
                    }
                    case "no_invite" -> player.sendMessage(color(t(player, "team.no_invite")));
                    case "already_in" -> player.sendMessage(color(t(player, "team.already_in")));
                    case "team_gone" -> player.sendMessage(color(t(player, "team.team_gone")));
                    case "full" -> player.sendMessage(color(t(player, "team.full")));
                    default -> player.sendMessage(color(t(player, "team.no_invite")));
                }
            }
            case "deny" -> {
                String r = teamManager.deny(player);
                player.sendMessage(color(t(player, r.equals("ok") ? "team.invite_denied" : "team.no_invite")));
            }
            case "leave" -> {
                TeamManager.Team before = teamManager.getTeamOf(player.getUniqueId());
                String r = teamManager.leave(player);
                if (r.startsWith("ok")) {
                    player.sendMessage(color(t(player, "team.left")));
                    if (before != null) {
                        for (java.util.UUID id : before.members) {
                            Player p = Bukkit.getPlayer(id);
                            if (p != null)
                                p.sendMessage(color(t(p, "team.member_left").replace("%player%", player.getName())));
                        }
                    }
                } else player.sendMessage(color(t(player, "team.no_team")));
            }
            case "kick" -> {
                if (args.length < 2) { player.sendMessage(color(t(player, "team.usage_kick"))); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                java.util.UUID tid = target != null ? target.getUniqueId() : null;
                if (tid == null) {
                    // offline by name not supported simply
                    player.sendMessage(color(t(player, "pm.player_not_found").replace("%player%", args[1])));
                    return true;
                }
                String r = teamManager.kick(player, tid);
                switch (r) {
                    case "ok" -> {
                        player.sendMessage(color(t(player, "team.kicked").replace("%player%", target.getName())));
                        target.sendMessage(color(t(target, "team.you_kicked")));
                    }
                    case "no_team" -> player.sendMessage(color(t(player, "team.no_team")));
                    case "not_owner" -> player.sendMessage(color(t(player, "team.not_owner")));
                    case "not_member" -> player.sendMessage(color(t(player, "team.not_member")));
                    case "self" -> player.sendMessage(color(t(player, "team.cannot_self")));
                    case "cannot_kick_owner" -> player.sendMessage(color(t(player, "team.cannot_kick_owner")));
                    default -> player.sendMessage(color(t(player, "team.no_team")));
                }
            }
            case "disband" -> {
                TeamManager.Team team = teamManager.getTeamOf(player.getUniqueId());
                if (team == null) { player.sendMessage(color(t(player, "team.no_team"))); return true; }
                String teamName = team.name;
                java.util.List<java.util.UUID> members = new java.util.ArrayList<>(team.members);
                String r = teamManager.disband(player);
                if (r.equals("ok")) {
                    for (java.util.UUID id : members) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) p.sendMessage(color(t(p, "team.disbanded").replace("%team%", teamName)));
                    }
                } else if (r.equals("not_owner")) player.sendMessage(color(t(player, "team.not_owner")));
                else player.sendMessage(color(t(player, "team.no_team")));
            }
            case "chat", "c" -> {
                if (args.length < 2) { player.sendMessage(color(t(player, "team.usage_chat"))); return true; }
                String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                sendTeamChat(player, msg);
            }
            case "name", "rename" -> {
                if (args.length < 2) { player.sendMessage(color(t(player, "team.usage_name"))); return true; }
                String r = teamManager.rename(player, args[1]);
                switch (r) {
                    case "ok" -> player.sendMessage(color(t(player, "team.renamed").replace("%team%", args[1])));
                    case "not_owner" -> player.sendMessage(color(t(player, "team.not_owner")));
                    case "name_taken" -> player.sendMessage(color(t(player, "team.name_taken")));
                    case "bad_name" -> player.sendMessage(color(t(player, "team.bad_name")));
                    default -> player.sendMessage(color(t(player, "team.no_team")));
                }
            }
            case "color" -> {
                if (args.length < 2) { player.sendMessage(color(t(player, "team.usage_color"))); return true; }
                String r = teamManager.setColor(player, args[1]);
                switch (r) {
                    case "ok" -> {
                        TeamManager.Team tcol = teamManager.getTeamOf(player.getUniqueId());
                        String shown = (tcol != null && tcol.color != null) ? tcol.color : args[1];
                        // preview square uses the actual team color codes
                        player.sendMessage(color(t(player, "team.color_set").replace("%color%", shown)));
                    }
                    case "not_owner" -> player.sendMessage(color(t(player, "team.not_owner")));
                    case "bad_color" -> player.sendMessage(color(t(player, "team.bad_color")));
                    case "disabled" -> player.sendMessage(color(t(player, "team.color_disabled")));
                    default -> player.sendMessage(color(t(player, "team.no_team")));
                }
            }
            case "info" -> {
                TeamManager.Team team = teamManager.getTeamOf(player.getUniqueId());
                if (team == null) { player.sendMessage(color(t(player, "team.no_team"))); return true; }
                player.sendMessage(color(t(player, "team.info_header").replace("%team%", team.name).replace("%color%", team.color).replace("%symbol%", team.symbol != null ? team.symbol : "")));
                StringBuilder members = new StringBuilder();
                for (java.util.UUID id : team.members) {
                    Player p = Bukkit.getPlayer(id);
                    String n = p != null ? p.getName() : Bukkit.getOfflinePlayer(id).getName();
                    if (n == null) n = id.toString().substring(0, 8);
                    if (team.isOwner(id)) n = n + "*";
                    else if (team.coOwners.contains(id)) n = n + "+";
                    if (members.length() > 0) members.append("&7, &f");
                    members.append(n);
                }
                player.sendMessage(color(t(player, "team.info_members").replace("%members%", members.toString())));
                player.sendMessage(color(t(player, "team.info_legend")));
            }
            case "transfer" -> {
                if (args.length < 2) { player.sendMessage(color(t(player, "team.usage_transfer"))); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage(color(t(player, "pm.player_not_found").replace("%player%", args[1]))); return true; }
                String r = teamManager.transfer(player, target.getUniqueId());
                switch (r) {
                    case "ok" -> {
                        player.sendMessage(color(t(player, "team.transferred").replace("%player%", target.getName())));
                        target.sendMessage(color(t(target, "team.you_owner")));
                    }
                    case "not_owner" -> player.sendMessage(color(t(player, "team.not_primary_owner")));
                    case "not_member" -> player.sendMessage(color(t(player, "team.not_member")));
                    case "self" -> player.sendMessage(color(t(player, "team.cannot_self")));
                    default -> player.sendMessage(color(t(player, "team.no_team")));
                }
            }
            case "promote" -> {
                if (args.length < 2) { player.sendMessage(color(t(player, "team.usage_promote"))); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage(color(t(player, "pm.player_not_found").replace("%player%", args[1]))); return true; }
                String r = teamManager.promote(player, target.getUniqueId());
                switch (r) {
                    case "ok" -> {
                        player.sendMessage(color(t(player, "team.promoted").replace("%player%", target.getName())));
                        target.sendMessage(color(t(target, "team.you_co_owner")));
                    }
                    case "not_owner" -> player.sendMessage(color(t(player, "team.not_primary_owner")));
                    case "not_member" -> player.sendMessage(color(t(player, "team.not_member")));
                    case "already_co" -> player.sendMessage(color(t(player, "team.already_co")));
                    case "max_co" -> player.sendMessage(color(t(player, "team.max_co")));
                    case "self" -> player.sendMessage(color(t(player, "team.cannot_self")));
                    default -> player.sendMessage(color(t(player, "team.no_team")));
                }
            }
            case "demote" -> {
                if (args.length < 2) { player.sendMessage(color(t(player, "team.usage_demote"))); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage(color(t(player, "pm.player_not_found").replace("%player%", args[1]))); return true; }
                String r = teamManager.demote(player, target.getUniqueId());
                switch (r) {
                    case "ok" -> {
                        player.sendMessage(color(t(player, "team.demoted").replace("%player%", target.getName())));
                        target.sendMessage(color(t(target, "team.you_demoted")));
                    }
                    case "not_owner" -> player.sendMessage(color(t(player, "team.not_primary_owner")));
                    case "not_co" -> player.sendMessage(color(t(player, "team.not_co")));
                    default -> player.sendMessage(color(t(player, "team.no_team")));
                }
            }
            case "symbol" -> {
                if (args.length < 2) { player.sendMessage(color(t(player, "team.usage_symbol"))); return true; }
                String r = teamManager.setSymbol(player, args[1]);
                switch (r) {
                    case "ok" -> player.sendMessage(color(t(player, "team.symbol_set").replace("%symbol%", args[1])));
                    case "not_owner" -> player.sendMessage(color(t(player, "team.not_owner")));
                    case "taken" -> player.sendMessage(color(t(player, "team.symbol_taken")));
                    case "clash_global" -> player.sendMessage(color(t(player, "team.symbol_clash")));
                    case "bad_symbol" -> player.sendMessage(color(t(player, "team.bad_symbol")));
                    default -> player.sendMessage(color(t(player, "team.no_team")));
                }
            }
            default -> player.sendMessage(color(t(player, "team.usage")));
        }
        return true;
    }


}
