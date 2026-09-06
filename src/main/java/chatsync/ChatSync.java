package chatsync;

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

    private final Map<UUID, UUID>      lastMessaged  = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> ignoreList    = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID>            socialSpy     = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** UUID → timestamp последнего глобального сообщения (для кулдауна) */
    private final Map<UUID, Long>      globalCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    /** UUID → timestamp последнего локального сообщения (slowmode) */
    private final Map<UUID, Long>      localCooldown  = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, YamlConfiguration> langConfigs = new HashMap<>();

    /** Ожидающие подтверждения запросы /clear: ключ отправителя → цель + время истечения. */
    private final Map<UUID, PendingClear> pendingClears = new java.util.concurrent.ConcurrentHashMap<>();
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
    private final Map<UUID, Long> pendingStatsResets = new java.util.concurrent.ConcurrentHashMap<>();

    /** Анти-спам: UUID → последние сообщения (текст + timestamp). */
    private final Map<UUID, java.util.Deque<SpamEntry>> recentMessages = new java.util.concurrent.ConcurrentHashMap<>();
    /** UUID → {value, signature?} for GUI skulls — filled without network I/O. */
    private final java.util.concurrent.ConcurrentHashMap<UUID, String[]> skinTextureCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<org.bukkit.scheduler.BukkitTask> scheduledTasks = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Админы, у которых скрыт автор в /broadcast (персональный toggle). */
    private final Set<UUID> broadcastHideAuthor = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private record SpamEntry(String text, long time, String channel) {}

    public ChatStatsManager getStatsManager() { return statsManager; }
    public PlaytimeManager getPlaytimeManager() { return playtimeManager; }

    public java.util.Set<UUID> getIgnoredUuids(UUID player) {
        java.util.Set<UUID> set = ignoreList.get(player);
        return set == null ? java.util.Set.of() : java.util.Set.copyOf(set);
    }

    public void unignorePlayer(Player viewer, UUID target) {
        if (viewer == null || target == null) return;
        java.util.Set<UUID> set = ignoreList.get(viewer.getUniqueId());
        if (set != null) {
            set.remove(target);
            if (set.isEmpty()) ignoreList.remove(viewer.getUniqueId());
        }
        saveIgnoreList();
    }

    private ChatSyncGui gui;

    public TeamManager getTeamManager() { return teamManager; }

    private static final List<String> SUPPORTED_LANGS = List.of(
            "en", "ru", "de", "fr", "es", "pt", "pl", "it", "uk", "zh", "ja", "tr", "nl", "cs"
    );

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

    /**
     * Merge missing keys from the JAR default config into the live config.yml
     * without overwriting user values. Bumps config-version when done.
     */
    private void mergeConfigDefaults() {
        final int CURRENT = 4;
        int ver = getConfig().getInt("config-version", 0);
        java.io.InputStream in = getResource("config.yml");
        if (in == null) return;
        YamlConfiguration def;
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            def = YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            getLogger().warning("Could not read default config.yml from JAR: " + e.getMessage());
            return;
        }
        boolean changed = false;
        for (String key : def.getKeys(true)) {
            if (def.isConfigurationSection(key)) continue;
            if (!getConfig().isSet(key)) {
                getConfig().set(key, def.get(key));
                changed = true;
            }
        }
        if (ver < CURRENT) {
            getConfig().set("config-version", CURRENT);
            changed = true;
        }
        if (changed) {
            try {
                saveConfig();
                getLogger().info("Config merged to version " + CURRENT + " (new keys added, existing values kept).");
            } catch (Exception e) {
                getLogger().warning("Could not save merged config: " + e.getMessage());
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeConfigDefaults();
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
        loadSocialSpy();
        loadIgnoreList();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new DeathMessageTranslator(this), this);
        getServer().getPluginManager().registerEvents(new AdvancementMessageTranslator(this), this);
        this.gui = new ChatSyncGui(this);
        getServer().getPluginManager().registerEvents(gui, this);

        // Chat listener: Paper AsyncChatEvent when available, else Spigot/Arclight legacy
        registerChatListener();

        registerCmd("msg",         this);
        registerCmd("reply",       this);
        registerCmd("ignore",      this);
        registerCmd("ignorelist",  this);
        registerCmd("socialspy",   this);
        registerCmd("chatsync",    this);
        registerCmd("me",          this);
        registerCmd("clear",       this);
        registerCmd("chatstats",   this);
        registerCmd("chatstatstop", this);
        registerCmd("broadcast",   this);
        registerCmd("playtime",    this);
        registerCmd("playtimetop", this);
        registerCmd("lastseen",    this);
        registerCmd("team",        this);

        if (getConfig().getBoolean("stats.enabled", true)) {
            long intervalTicks = 20L * Math.max(30, getConfig().getInt("stats.save_interval", 300));
            scheduledTasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                    () -> statsManager.saveIfDirty(), intervalTicks, intervalTicks));
        }

        if (getConfig().getBoolean("playtime.enabled", true)) {
            long ptTicks = 20L * Math.max(30, getConfig().getInt("playtime.save_interval", 300));
            // Main-thread: sync vanilla playtime into cache, then async save
            scheduledTasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (playtimeManager != null) playtimeManager.syncOnlinePlayers();
            }, ptTicks, ptTicks));
            scheduledTasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                    () -> playtimeManager.saveIfDirty(), ptTicks + 20L, ptTicks));
            // Уже онлайн на момент включения плагина (reload / late enable)
            for (Player online : Bukkit.getOnlinePlayers()) {
                playtimeManager.onJoin(online.getUniqueId(), online.getName());
            }
        }

        // Периодическая очистка просроченных заявок /clear, чтобы карта не росла бесконечно.
        scheduledTasks.add(Bukkit.getScheduler().runTaskTimer(this, this::purgeExpiredClears, 20L * 60, 20L * 60));

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
        for (org.bukkit.scheduler.BukkitTask task : scheduledTasks) {
            try { if (task != null) task.cancel(); } catch (Throwable ignored) {}
        }
        scheduledTasks.clear();
        if (statsManager != null) statsManager.save();
        if (playtimeManager != null && getConfig().getBoolean("playtime.enabled", true)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                playtimeManager.onQuit(online.getUniqueId(), online.getName());
            }
            playtimeManager.save();
        }
        if (teamManager != null) teamManager.save();
        if (chatLogger != null) chatLogger.flushNow();
        saveSocialSpy();
        saveIgnoreList();
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
            if (!file.exists()) {
                saveResource(resource, false);
            } else {
                // Re-extract from JAR if file is broken / missing critical keys
                YamlConfiguration probe = YamlConfiguration.loadConfiguration(file);
                if (probe.getString("commands.reload.success") == null
                        || probe.getString("playtime.other") == null
                        || probe.getString("pm.player_not_found") == null) {
                    getLogger().warning("Language file " + resource + " is outdated or broken — restoring from JAR.");
                    saveResource(resource, true);
                } else {
                    // Дописать новые ключи (gui и т.п.) без затирания кастомных переводов
                    mergeLangFromJar(resource, file);
                }
            }
            langConfigs.put(lang, YamlConfiguration.loadConfiguration(file));
        }
        getLogger().info("Loaded " + langConfigs.size() + " language(s): " + String.join(", ", langConfigs.keySet()));
    }

    /** Дописывает отсутствующие ключи из JAR в существующий lang-файл. */
    private void mergeLangFromJar(String resource, File file) {
        try {
            java.io.InputStream in = getResource(resource);
            if (in == null) return;
            YamlConfiguration jar = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(file);
            boolean changed = false;
            for (String key : jar.getKeys(true)) {
                if (jar.isConfigurationSection(key)) continue;
                if (!disk.contains(key)) {
                    disk.set(key, jar.get(key));
                    changed = true;
                }
            }
            if (changed) {
                disk.save(file);
                getLogger().info("Merged new lang keys into " + resource);
            }
        } catch (Throwable t) {
            getLogger().warning("Could not merge lang " + resource + ": " + t.getMessage());
        }
    }

    private String getLang(Player player) {
        // Language from config.yml only (see SUPPORTED_LANGS)
        String lang = getConfig().getString("language", "en");
        if (lang == null || lang.isEmpty()) lang = "en";
        lang = lang.toLowerCase(java.util.Locale.ROOT).trim();
        if (langConfigs.containsKey(lang)) return lang;
        if (lang.length() >= 2) {
            String prefix = lang.substring(0, 2);
            if (langConfigs.containsKey(prefix)) return prefix;
        }
        return langConfigs.containsKey("en") ? "en" : lang;
    }

    String t(Player player, String key) { return t(getLang(player), key); }

    private String t(String lang, String key) {
        YamlConfiguration cfg = langConfigs.get(lang);
        if (cfg != null) {
            String v = cfg.getString(key);
            if (v != null && !v.isEmpty()) return v;
        }
        if (!"en".equals(lang)) {
            YamlConfiguration en = langConfigs.get("en");
            if (en != null) {
                String v = en.getString(key);
                if (v != null && !v.isEmpty()) return v;
            }
        }
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
        // ObjectComponent в event.joinMessage → консоль пишет "[name head]".
        // Обнуляем event, шлём игрокам с головой, в консоль — plain-текст.
        Component withHead = buildJoinQuitMessage(
                getConfig().getString("messages.join", "&a+ &f%player%"), player);
        event.joinMessage(null);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(withHead);
        }
        logToConsole(plainComponent(withHead));
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
            Component withHead = buildJoinQuitMessage(
                    getConfig().getString("messages.quit", "&c- &f%player%"), player);
            event.quitMessage(null);
            final Component msg = withHead;
            final UUID quitter = player.getUniqueId();
            // На quit игрок ещё online — шлём сразу; консоль — plain
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getUniqueId().equals(quitter)) continue;
                p.sendMessage(msg);
            }
            logToConsole(plainComponent(msg));
        }
        if (teamManager != null) teamManager.onQuit(player.getUniqueId());
        lastMessaged.remove(player.getUniqueId());
        // socialSpy persists across rejoin (saved to spy.yml)
        globalCooldown.remove(player.getUniqueId());
        pendingClears.remove(player.getUniqueId());
        pendingStatsResets.remove(player.getUniqueId());
        recentMessages.remove(player.getUniqueId());
        broadcastHideAuthor.remove(player.getUniqueId());
        localCooldown.remove(player.getUniqueId());
        if (liteBansHook != null) liteBansHook.onQuit(player.getUniqueId());
    }

    private Component buildJoinQuitMessage(String template, Player player) {
        return buildNameComponentPublic(template, player, "join_quit");
    }

    /**
     * Убирает Adventure ObjectComponent (player heads), чтобы консоль/Discord
     * не печатали сырой "[name head]" / NBT.
     */
    Component stripObjectComponents(Component component) {
        if (component == null) return Component.empty();
        try {
            String className = component.getClass().getName();
            if (className.contains("ObjectComponent") || className.contains("object")) {
                return Component.empty();
            }
        } catch (Throwable ignored) {}
        java.util.List<Component> children = component.children();
        if (children == null || children.isEmpty()) return component;
        java.util.List<Component> cleaned = new java.util.ArrayList<>(children.size());
        boolean changed = false;
        for (Component child : children) {
            Component c = stripObjectComponents(child);
            if (c != child) changed = true;
            if (c.equals(Component.empty()) && child != c) {
                changed = true;
                continue;
            }
            cleaned.add(c);
        }
        return changed ? component.children(cleaned) : component;
    }


    /**
     * Whether 2D head icons are enabled for a context.
     * Contexts: chat, death, advancement, join_quit, commands, pm, team, me, broadcast.
     * Falls back to legacy keys for older configs.
     */

    /** Cached offline textures for GUI (main-thread safe). */
    public String[] getCachedSkinTextures(UUID uuid) {
        if (uuid == null) return null;
        return skinTextureCache.get(uuid);
    }

    public void putCachedSkinTextures(UUID uuid, String[] tex) {
        if (uuid == null || tex == null || tex[0] == null) return;
        if (!getConfig().getBoolean("gui.skin_cache", true)) return;
        int max = getConfig().getInt("gui.skin_cache_max", 500);
        if (max > 0 && skinTextureCache.size() >= max && !skinTextureCache.containsKey(uuid)) {
            UUID first = skinTextureCache.keySet().stream().findFirst().orElse(null);
            if (first != null) skinTextureCache.remove(first);
        }
        skinTextureCache.put(uuid, tex);
    }

    public boolean isHeadsEnabled(String context) {
        if (context == null) context = "chat";
        // Master switch for hybrid servers (Arclight): heads.arclight = false disables all heads
        if (ServerCompat.isArclight() && !getConfig().getBoolean("heads.arclight", true)) {
            return false;
        }
        String key = "heads." + context;
        if (getConfig().isSet(key)) {
            return getConfig().getBoolean(key, true);
        }
        // Legacy fallbacks
        return switch (context) {
            case "chat", "pm", "team", "me" -> getConfig().getBoolean("chat.heads.enabled", true);
            case "death" -> getConfig().getBoolean("death_messages.show_heads", true)
                    && getConfig().getBoolean("chat.heads.enabled", true);
            case "advancement" -> getConfig().getBoolean("advancement_messages.show_heads", true)
                    && getConfig().getBoolean("chat.heads.enabled", true);
            case "join_quit" -> getConfig().getBoolean("chat.heads.enabled", true);
            case "commands" -> getConfig().getBoolean("clickable_names.heads_in_commands", true)
                    && getConfig().getBoolean("chat.heads.enabled", true);
            case "broadcast" -> false;
            default -> getConfig().getBoolean("chat.heads.enabled", true);
        };
    }

    /**
     * Whether player names are clickable for a context.
     * Contexts: chat, death, advancement, join_quit, commands, pm, team, me, ignore, broadcast.
     */
    public boolean isClickableEnabled(String context) {
        if (context == null) context = "chat";
        String key = "clickable_names." + context;
        if (getConfig().isSet(key)) {
            return getConfig().getBoolean(key, true);
        }
        return switch (context) {
            case "death" -> getConfig().getBoolean("toggles.clickable_death_name", true);
            case "advancement" -> getConfig().getBoolean("toggles.clickable_advancement_name", true);
            default -> getConfig().getBoolean("clickable_names.enabled", true);
        };
    }

    public boolean isHeadsForceFirst() {
        if (getConfig().isSet("heads.force_first")) {
            return getConfig().getBoolean("heads.force_first", true);
        }
        return getConfig().getBoolean("chat.heads.force_first", true);
    }

    public String headsGap() {
        if (getConfig().isSet("heads.gap")) {
            return getConfig().getString("heads.gap", " ");
        }
        return getConfig().getString("chat.heads.gap", " ");
    }

    /** Plain text from Component without "[name head]" object noise. */
    String plainComponent(Component component) {
        if (component == null) return "";
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stripObjectComponents(component))
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ──────────────────────────────────────────────────────────────
    //  Chat
    // ──────────────────────────────────────────────────────────────

    /**
     * Shared chat pipeline for Paper AsyncChatEvent and Spigot/Arclight AsyncPlayerChatEvent.
     * Called from PaperChatListener / LegacyChatListener after the source event is cancelled.
     */

    private void registerChatListener() {
        if (getConfig().getBoolean("compatibility.log_platform", true)) {
            getLogger().info("Server platform: " + ServerCompat.describe());
        }
        if (ServerCompat.hasPaperAsyncChat()) {
            try {
                getServer().getPluginManager().registerEvents(new PaperChatListener(this), this);
                getLogger().info("Chat pipeline: Paper AsyncChatEvent"
                        + (ServerCompat.isArclight() ? " (Arclight)" : ""));
                return;
            } catch (Throwable t) {
                getLogger().warning("Paper AsyncChatEvent present but failed to register: " + t.getMessage());
            }
        }
        getServer().getPluginManager().registerEvents(new LegacyChatListener(this), this);
        getLogger().info("Chat pipeline: AsyncPlayerChatEvent (Spigot/Arclight compatibility mode)"
                + (ServerCompat.isArclight() ? " — hybrid mod+plugin server detected" : ""));
        if (ServerCompat.isArclight()) {
            getLogger().info("Arclight notes: object heads (1.21.9+ clients) and some Paper-only APIs "
                    + "may be limited; core chat/PM/teams/stats use portable Bukkit APIs.");
        }
    }

    public void processChatMessage(Player sender, String rawMessage) {
        if (sender == null || rawMessage == null) return;

        if (!sender.hasPermission(getConfig().getString("advanced.color_permission", "chatsync.color"))) rawMessage = stripColorCodes(rawMessage);

        boolean globalEnabled = getConfig().getBoolean("chat.global.enabled", true);
        boolean localEnabled  = getConfig().getBoolean("chat.local.enabled", true);
        // Global prefix (e.g. "!"). Empty = no dedicated global prefix.
        String globalSymbol = getConfig().getString("chat.global.symbol", "!");
        if (globalSymbol == null) globalSymbol = "";
        // Local prefix (e.g. "@"). Empty = no dedicated local prefix.
        String localSymbol = getConfig().getString("chat.local.symbol", "");
        if (localSymbol == null) localSymbol = "";
        // Legacy: require_symbol only forced global via prefix; still supported.
        boolean requireGlobalSymbol = getConfig().getBoolean("chat.global.require_symbol", true);

        String defaultCh = getConfig().getString("chat.default_channel", "local");
        if (defaultCh == null) defaultCh = "local";
        defaultCh = defaultCh.toLowerCase(java.util.Locale.ROOT).trim();
        boolean defaultGlobal = defaultCh.equals("global") || defaultCh.equals("g");

        boolean isGlobal;
        String  formatStr;

        if (!globalEnabled && !localEnabled) {
            // Fallback: treat as local format, still deliver somehow
            isGlobal = false;
            formatStr = getConfig().getString("chat.local.format");
        } else if (!globalEnabled) {
            // Local only — strip optional local/global symbols if present
            isGlobal = false;
            if (!localSymbol.isEmpty() && rawMessage.startsWith(localSymbol)) {
                rawMessage = rawMessage.substring(localSymbol.length()).trim();
            } else if (!globalSymbol.isEmpty() && rawMessage.startsWith(globalSymbol)) {
                rawMessage = rawMessage.substring(globalSymbol.length()).trim();
            }
            formatStr = getConfig().getString("chat.local.format");
        } else if (!localEnabled) {
            // Global only — no "!" required; optional symbol is stripped if typed
            isGlobal = true;
            if (!globalSymbol.isEmpty() && rawMessage.startsWith(globalSymbol)) {
                rawMessage = rawMessage.substring(globalSymbol.length()).trim();
            }
            formatStr = getConfig().getString("chat.global.format");
        } else {
            // Both channels enabled — decide by explicit prefix, else default_channel
            boolean forcedGlobal = !globalSymbol.isEmpty() && rawMessage.startsWith(globalSymbol);
            boolean forcedLocal  = !localSymbol.isEmpty()  && rawMessage.startsWith(localSymbol);

            // Prefer longer prefix if both match the start (rare)
            if (forcedGlobal && forcedLocal) {
                if (localSymbol.length() > globalSymbol.length()) forcedGlobal = false;
                else forcedLocal = false;
            }

            if (forcedGlobal) {
                isGlobal = true;
                rawMessage = rawMessage.substring(globalSymbol.length()).trim();
            } else if (forcedLocal) {
                isGlobal = false;
                rawMessage = rawMessage.substring(localSymbol.length()).trim();
            } else if (requireGlobalSymbol && defaultGlobal == false && !globalSymbol.isEmpty()) {
                // Classic mode: default local; only "!" sends global
                isGlobal = false;
            } else {
                // No prefix → default_channel
                // When default is global, messages go global WITHOUT needing "!"
                isGlobal = defaultGlobal;
            }
            formatStr = isGlobal
                    ? getConfig().getString("chat.global.format")
                    : getConfig().getString("chat.local.format");
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
            case "chatstatstop" -> { return cmdChatStatsTop(sender, args); }
            case "broadcast"   -> { return cmdBroadcast(sender, args); }
            case "playtime"    -> { return cmdPlaytime(sender, args); }
            case "playtimetop" -> { return cmdPlaytimeTop(sender, args); }
            case "lastseen"    -> { return cmdLastSeen(sender, args); }
            case "team"        -> { return cmdTeam(sender, args); }
        }
        return false;
    }

    private boolean cmdChatSync(CommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "info";

        if (sub.equals("reload")) {
            if (!sender.hasPermission(getConfig().getString("commands.reload.permission", "chatsync.admin"))) {
                sender.sendMessage(color(tAny(sender, "commands.reload.no_permission")));
                return true;
            }
            reloadConfig();
            loadLangFiles();
            // Перечитываем данные с диска (перенос stats/playtime/spy/teams/ignore)
            loadSocialSpy();
            loadIgnoreList();
            if (statsManager != null) statsManager.reload();
            if (playtimeManager != null) playtimeManager.reload();
            if (teamManager != null) teamManager.reload();
            sender.sendMessage(color(tAny(sender, "commands.reload.success")));
            return true;
        }

        if (sub.equals("gui") || sub.equals("menu")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (gui == null) {
                sender.sendMessage(color("&cGUI not initialized."));
                return true;
            }
            gui.openMain(p);
            return true;
        }

        // /chatsync info (default) — available to everyone
        if (sub.equals("info") || sub.equals("version") || sub.equals("about") || args.length == 0) {
            sendPluginInfo(sender);
            return true;
        }

        sender.sendMessage(color(tAny(sender, "commands.chatsync.usage")));
        return true;
    }

    private void sendPluginInfo(CommandSender sender) {
        String ver = getDescription().getVersion();
        // Prefer lang lines if present; otherwise built-in layout matching Discord mockup
        java.util.List<String> lines = (sender instanceof Player p)
                ? tList(p, "commands.chatsync.info_lines")
                : tListDefault("commands.chatsync.info_lines");
        if (lines != null && !lines.isEmpty()) {
            for (String line : lines) {
                if (line == null) continue;
                sender.sendMessage(color(line
                        .replace("%version%", ver)
                        .replace("%chatsync_version%", ver)
                        .replace("%author%", "Mutya660")
                        .replace("%developer%", "Mutya660")));
            }
            return;
        }
        sender.sendMessage(color(""));
        sender.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(color("&b&l  ChatSync &8» &7Information"));
        sender.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(color("&7  Multifunctional chat plugin for Minecraft"));
        sender.sendMessage(color("&7  Version &8» &e" + ver));
        sender.sendMessage(color("&7  Author  &8» &aMutya660"));
        sender.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(color(""));
    }

    private boolean cmdMsg(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(tAny(sender, "commands.msg.usage")));
            return true;
        }

        // /msg console <text> — write to server console (tests / alert log watchers)
        if (args[0].equalsIgnoreCase("console")) {
            if (!getConfig().getBoolean("console_pm.enabled", true)) {
                sender.sendMessage(color(tAny(sender, "commands.msg.usage")));
                return true;
            }
            if (!(sender instanceof Player pSender)) {
                sender.sendMessage(color(tAny(sender, "commands.msg.console_players_only")));
                return true;
            }
            if (!pSender.hasPermission(getConfig().getString("commands.msg.console_permission", "chatsync.msg.console"))) {
                pSender.sendMessage(color(t(pSender, "commands.msg.console_no_permission")));
                return true;
            }
            String message = joinArgs(args, 1);
            if (message.isBlank()) {
                pSender.sendMessage(color(t(pSender, "commands.msg.usage")));
                return true;
            }
            if (!pSender.hasPermission(getConfig().getString("advanced.color_permission", "chatsync.color"))) message = stripColorCodes(message);

            String cfmt = getConfig().getString("console_pm.console_format",
                    "&8[&eChatSync&8] &7PM from &f%player%&7: &f%message%");
            if (cfmt == null) cfmt = "&8[&eChatSync&8] &7PM from &f%player%&7: &f%message%";
            getLogger().info("[ChatSync PM → Console] " + pSender.getName() + ": " + message.replace("\u00A7", "&"));
            Bukkit.getConsoleSender().sendMessage(color(
                    cfmt.replace("%player%", pSender.getName()).replace("%message%", message)));

            String senderFmt = t(pSender, "pm.format_console_sender");
            if (senderFmt == null || senderFmt.isEmpty() || senderFmt.equals("pm.format_console_sender")) {
                senderFmt = "%head_self%&eYou &e→ %head_console%&eConsole&e: &e%message%";
            }
            senderFmt = senderFmt.replace("%message%", message);
            net.kyori.adventure.text.TextComponent.Builder out = Component.text();
            String rest = senderFmt;
            boolean anyHeadToken = rest.contains("%head_self%") || rest.contains("%head_console%") || rest.contains("%head%");
            while (!rest.isEmpty()) {
                int iSelf = rest.indexOf("%head_self%");
                int iCons = rest.indexOf("%head_console%");
                int iHead = rest.indexOf("%head%");
                int next = rest.length();
                String tok = null;
                if (iSelf >= 0 && iSelf < next) { next = iSelf; tok = "%head_self%"; }
                if (iCons >= 0 && iCons < next) { next = iCons; tok = "%head_console%"; }
                if (iHead >= 0 && iHead < next) { next = iHead; tok = "%head%"; }
                if (tok == null) {
                    out.append(color(rest));
                    break;
                }
                if (next > 0) out.append(color(rest.substring(0, next)));
                if (tok.equals("%head_console%")) {
                    out.append(buildConsoleHeadComponent());
                } else {
                    out.append(buildHeadComponent(pSender));
                }
                rest = rest.substring(next + tok.length());
            }
            if (!anyHeadToken && getConfig().getBoolean("chat.heads.enabled", true)
                    && isHeadsForceFirst()) {
                pSender.sendMessage(Component.text()
                        .append(buildHeadComponent(pSender))
                        .append(buildConsoleHeadComponent())
                        .append(out.build())
                        .build());
            } else {
                pSender.sendMessage(out.build());
            }

            if (getConfig().getBoolean("console_pm.log_to_file", true) && chatLogger != null) {
                try { chatLogger.log("[PM→Console] " + pSender.getName() + ": " + message); } catch (Throwable ignored) {}
            }
            if (getConfig().getBoolean("console_pm.count_stats", true) && statsManager != null) {
                try {
                    statsManager.record(pSender.getUniqueId(), pSender.getName(),
                            ChatStatsManager.MessageType.PM);
                } catch (Throwable ignored) {}
            }
            return true;
        }

        // Console → player PM
        if (!(sender instanceof Player)) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(color(tAny(sender, "pm.player_not_found").replace("%player%", args[0])));
                return true;
            }
            String message = joinArgs(args, 1);
            if (message.isBlank()) {
                sender.sendMessage(color(tAny(sender, "commands.msg.usage")));
                return true;
            }
            sendPMFromConsole(sender, target, message);
            return true;
        }

        Player pSender = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            String nf = t(pSender, "pm.player_not_found");
            if (!nf.contains("%player%")) nf = nf + " &8(%player%)";
            pSender.sendMessage(buildClickableNameLine(nf, args[0], pSender));
            return true;
        }
        if (target.equals(pSender)) { pSender.sendMessage(color(t(pSender, "commands.msg.self"))); return true; }
        if (isIgnoring(target, pSender)) {
            if (tog("ignore_notify_sender"))
                pSender.sendMessage(buildClickableNameLine(t(pSender, "commands.ignore.ignores_you"), target.getName(), pSender));
            return true;
        }
        sendPM(pSender, target, joinArgs(args, 1));
        return true;
    }

    /** /msg from server console to a player. */
    private void sendPMFromConsole(CommandSender console, Player to, String message) {
        String consoleName = getConfig().getString("console_pm.head_name", "Console");
        if (consoleName == null || consoleName.isEmpty()) consoleName = "Console";

        // Message to player
        String receiverFmt = t(to, "pm.format_from_console");
        if (receiverFmt == null || receiverFmt.isEmpty() || receiverFmt.equals("pm.format_from_console")) {
            receiverFmt = "%head_console%&e%console% &e→ &eYou&e: &e%message%";
        }
        receiverFmt = receiverFmt
                .replace("%console%", consoleName)
                .replace("%sender%", consoleName)
                .replace("%message%", message);

        net.kyori.adventure.text.TextComponent.Builder out = Component.text();
        String rest = receiverFmt;
        while (!rest.isEmpty()) {
            int iCons = rest.indexOf("%head_console%");
            int iHead = rest.indexOf("%head%");
            int next = rest.length();
            String tok = null;
            if (iCons >= 0 && iCons < next) { next = iCons; tok = "%head_console%"; }
            if (iHead >= 0 && iHead < next) { next = iHead; tok = "%head%"; }
            if (tok == null) {
                out.append(color(rest));
                break;
            }
            if (next > 0) out.append(color(rest.substring(0, next)));
            out.append(buildConsoleHeadComponent());
            rest = rest.substring(next + tok.length());
        }
        if (!receiverFmt.contains("%head") && getConfig().getBoolean("chat.heads.enabled", true)
                && isHeadsForceFirst()) {
            to.sendMessage(Component.text().append(buildConsoleHeadComponent()).append(out.build()).build());
        } else {
            to.sendMessage(out.build());
        }

        // Confirmation in console
        String conf = tAny(console, "pm.format_console_to_player");
        if (conf == null || conf.isEmpty() || conf.equals("pm.format_console_to_player")) {
            conf = "&eConsole &e→ &e%player%&e: &e%message%";
        }
        console.sendMessage(color(conf.replace("%player%", to.getName()).replace("%message%", message)));

        // SocialSpy
        if (tog("socialspy") && getConfig().getBoolean("socialspy.private_messages", true)) {
            String spyFmt = getConfig().getString("pm.format_spy",
                    "%head%&8[SPY] &7%sender% &8→ &7%receiver%&8: &7%message%");
            if (spyFmt == null) spyFmt = "&8[SPY] &7%sender% &8→ &7%receiver%&8: &7%message%";
            spyFmt = spyFmt.replace("%sender%", consoleName)
                    .replace("%receiver%", to.getName())
                    .replace("%message%", message)
                    .replace("%player%", consoleName);
            for (UUID uid : socialSpy) {
                Player spy = Bukkit.getPlayer(uid);
                if (spy == null || spy.equals(to)) continue;
                spy.sendMessage(color(spyFmt));
            }
        }

        if (chatLogger != null) {
            try { chatLogger.log("[PM Console→] Console -> " + to.getName() + ": " + message); } catch (Throwable ignored) {}
        }
        logToConsole("[PM] Console → " + to.getName() + ": " + message);
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

        Set<UUID> ignored = ignoreList.computeIfAbsent(pSender.getUniqueId(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        if (ignored.contains(target.getUniqueId())) {
            ignored.remove(target.getUniqueId());
            saveIgnoreList();
            pSender.sendMessage(buildClickableNameLine(t(pSender, "commands.ignore.removed"), target.getName(), pSender));
            if (tog("ignore_notify_target"))
                target.sendMessage(buildClickableNameLine(t(target, "commands.ignore.target_removed"), pSender.getName(), target));
        } else {
            ignored.add(target.getUniqueId());
            saveIgnoreList();
            // Click name to unignore again
            String added = t(pSender, "commands.ignore.added");
            Component addedMsg;
            if (added.contains("%player%")) {
                int idx = added.indexOf("%player%");
                String before = added.substring(0, idx);
                String after = added.substring(idx + "%player%".length());
                String hoverText = t(pSender, "commands.ignorelist.click_unignore");
                if (hoverText == null || hoverText.equals("commands.ignorelist.click_unignore")) {
                    hoverText = "&cClick to unignore &f%player%";
                }
                hoverText = hoverText.replace("%player%", target.getName());
                Component nameComp = color(extractTrailingColor(before) + target.getName())
                        .clickEvent(ClickEvent.runCommand("/ignore " + target.getName()))
                        .hoverEvent(HoverEvent.showText(color(hoverText)));
                addedMsg = Component.text().append(color(before)).append(nameComp).append(color(after)).build();
            } else {
                addedMsg = buildClickableNameLine(added, target.getName(), pSender);
            }
            pSender.sendMessage(addedMsg);
            if (tog("ignore_notify_target"))
                target.sendMessage(buildClickableNameLine(t(target, "commands.ignore.target_added"), pSender.getName(), target));
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

        List<java.util.Map.Entry<UUID, String>> entries = new ArrayList<>();
        for (UUID uid : ignored) {
            Player online = Bukkit.getPlayer(uid);
            String name;
            if (online != null) {
                name = online.getName();
            } else {
                @SuppressWarnings("deprecation")
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uid);
                name = op.getName() != null ? op.getName() : uid.toString();
            }
            entries.add(java.util.Map.entry(uid, name));
        }

        String header = t(pSender, "commands.ignorelist.header").replace("%count%", String.valueOf(entries.size()));
        pSender.sendMessage(color(header));

        // Clickable names: click runs /ignore <name> to remove from ignore list
        net.kyori.adventure.text.TextComponent.Builder line = Component.text();
        String prefix = t(pSender, "commands.ignorelist.entry_prefix");
        if (prefix == null) prefix = "&f";
        String hoverTpl = t(pSender, "commands.ignorelist.click_unignore");
        if (hoverTpl == null || hoverTpl.isEmpty() || hoverTpl.equals("commands.ignorelist.click_unignore")) {
            hoverTpl = "&cClick to unignore &f%player%";
        }
        boolean first = true;
        for (java.util.Map.Entry<UUID, String> e : entries) {
            if (!first) line.append(color("&7, "));
            first = false;
            String name = e.getValue();
            Component nameComp = color(prefix + name);
            if (getConfig().getBoolean("ignore.click_to_unignore", true)) {
                nameComp = nameComp
                        .clickEvent(ClickEvent.runCommand("/ignore " + name))
                        .hoverEvent(HoverEvent.showText(color(hoverTpl.replace("%player%", name))));
            }
            line.append(nameComp);
        }
        pSender.sendMessage(line.build());
        return true;
    }

    private boolean cmdSocialSpy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player pSender)) return true;
        String spyPerm = getConfig().getString("socialspy.permission", "chatsync.spy");
        if (!pSender.hasPermission(spyPerm != null ? spyPerm : "chatsync.spy")) {
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
        saveSocialSpy();
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
        if (!pSender.hasPermission(getConfig().getString("advanced.color_permission", "chatsync.color"))) rawMessage = stripColorCodes(rawMessage);

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

        // SocialSpy for /me
        if (tog("socialspy") && getConfig().getBoolean("socialspy.me", true)) {
            String spyMeFmt = getConfig().getString("pm.format_spy_me",
                    "%head%&8[SPY-ME] &7* %player% &7%message%");
            if (spyMeFmt == null) spyMeFmt = "%head%&8[SPY-ME] &7* %player% &7%message%";
            spyMeFmt = spyMeFmt.replace("%message%", rawMessage);
            for (UUID uid : socialSpy) {
                Player spy = Bukkit.getPlayer(uid);
                if (spy == null || spy.equals(pSender)) continue;
                spy.sendMessage(buildClickableNameLine(spyMeFmt, pSender.getName(), spy));
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

    // ── /chatstatstop ────────────────────────────────────────────

    private boolean cmdChatStatsTop(CommandSender sender, String[] args) {
        String permission = getConfig().getString("commands.chatstatstop.permission",
                getConfig().getString("commands.chatstats.permission", "chatsync.chatstats"));
        if (!sender.hasPermission(permission) && !sender.hasPermission("chatsync.chatstatstop")) {
            sender.sendMessage(color(tAny(sender, "chatstats.no_permission")));
            return true;
        }
        if (statsManager == null) {
            sender.sendMessage(color(tAny(sender, "chatstats.empty")));
            return true;
        }
        int pageSize = Math.max(1, getConfig().getInt("stats.top_size", 10));
        int page = 1;
        if (args.length >= 1) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException ignored) {}
        }
        List<Map.Entry<UUID, ChatStatsManager.PlayerStats>> all =
                statsManager.top(getConfig().getInt("gui.top_limit", 200));
        if (all.isEmpty()) {
            sender.sendMessage(color(tAny(sender, "chatstats.empty")));
            return true;
        }
        int pages = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        if (page > pages) page = pages;
        int from = (page - 1) * pageSize;
        int to = Math.min(all.size(), from + pageSize);

        sender.sendMessage(color(tAny(sender, "chatstats.top_header")
                .replace("%count%", String.valueOf(all.size()))
                .replace("%page%", String.valueOf(page))
                .replace("%pages%", String.valueOf(pages))));
        for (int i = from; i < to; i++) {
            Map.Entry<UUID, ChatStatsManager.PlayerStats> e = all.get(i);
            String name = statsManager.nameOf(e.getKey());
            if (name == null || name.isEmpty()) name = e.getKey().toString().substring(0, 8);
            String line = tAny(sender, "chatstats.top_entry")
                    .replace("%rank%", String.valueOf(i + 1))
                    .replace("%player%", name)
                    .replace("%total%", String.valueOf(e.getValue().total()));
            sender.sendMessage(color(line));
        }
        sendTopPager(sender, "chatstatstop", page, pages);
        return true;
    }


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
                sender.sendMessage(color(tAny(sender, "broadcast.presets_list").replace("%list%", String.join("&7, &f", presets.getKeys(false)))));
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
                    sender.sendMessage(color(tAny(sender, "broadcast.presets_list").replace("%list%", String.join("&7, &f", presetsHelp.getKeys(false)))));
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

        if (sender instanceof Player pSender && !pSender.hasPermission(getConfig().getString("advanced.color_permission", "chatsync.color"))) {
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
        if (!line.contains("%player%")) {
            line = line + " &8(%player%)";
        }
        sender.sendMessage(buildClickableNameLine(line, resolved.name(), sender));
        return true;
    }

    private boolean cmdPlaytimeTop(CommandSender sender, String[] args) {
        String permission = getConfig().getString("commands.playtimetop.permission", "chatsync.playtimetop");
        if (!sender.hasPermission(permission) && !sender.hasPermission("chatsync.playtimetop")) {
            sender.sendMessage(color(tAny(sender, "playtime.top_no_permission")));
            return true;
        }
        if (playtimeManager == null || !getConfig().getBoolean("playtime.enabled", true)) {
            sender.sendMessage(color(tAny(sender, "playtime.disabled")));
            return true;
        }
        int pageSize = Math.max(1, getConfig().getInt("playtime.top_size", 10));
        int page = 1;
        if (args.length >= 1) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException ignored) {}
        }
        List<Map.Entry<UUID, Long>> all = playtimeManager.top(getConfig().getInt("gui.top_limit", 200));
        if (all.isEmpty()) {
            sender.sendMessage(color(tAny(sender, "playtime.no_data").replace("%player%", "—")));
            return true;
        }
        int pages = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        if (page > pages) page = pages;
        int from = (page - 1) * pageSize;
        int to = Math.min(all.size(), from + pageSize);

        sender.sendMessage(color(tAny(sender, "playtime.top_header")
                .replace("%count%", String.valueOf(all.size()))
                .replace("%page%", String.valueOf(page))
                .replace("%pages%", String.valueOf(pages))));
        for (int i = from; i < to; i++) {
            Map.Entry<UUID, Long> e = all.get(i);
            String name = playtimeManager.nameOf(e.getKey());
            if (name == null || name.isEmpty()) name = e.getKey().toString().substring(0, 8);
            String line = tAny(sender, "playtime.top_entry")
                    .replace("%rank%", String.valueOf(i + 1))
                    .replace("%player%", name)
                    .replace("%time%", formatDuration(e.getValue(), sender));
            sender.sendMessage(color(line));
        }
        sendTopPager(sender, "playtimetop", page, pages);
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
        if (isOnlineVisible(sender, online)) {
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

    /** Clickable [prev] page/pages [next] for /chatstatstop and /playtimetop. */
    private void sendTopPager(CommandSender sender, String command, int page, int pages) {
        if (pages <= 1) return;
        net.kyori.adventure.text.TextComponent.Builder b = Component.text();
        if (page > 1) {
            b.append(LEGACY.deserialize("&7[&e←&7] ")
                    .clickEvent(ClickEvent.runCommand("/" + command + " " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(
                            LEGACY.deserialize("&7/" + command + " " + (page - 1)))));
        } else {
            b.append(LEGACY.deserialize("&8[←] "));
        }
        b.append(LEGACY.deserialize("&f" + page + "&8/&f" + pages + " "));
        if (page < pages) {
            b.append(LEGACY.deserialize("&7[&e→&7]")
                    .clickEvent(ClickEvent.runCommand("/" + command + " " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(
                            LEGACY.deserialize("&7/" + command + " " + (page + 1)))));
        } else {
            b.append(LEGACY.deserialize("&8[→]"));
        }
        sender.sendMessage(b.build());
    }

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
        return buildClickableNameLine(template, playerName, viewer, "commands");
    }

    private Component buildClickableNameLine(String template, String playerName, CommandSender viewer, String context) {
        if (template == null) template = "";
        if (context == null) context = "commands";
        Player headPlayer = Bukkit.getPlayerExact(playerName);
        UUID nameUuid = headPlayer != null ? headPlayer.getUniqueId() : null;
        if (nameUuid == null && playtimeManager != null) {
            try { nameUuid = playtimeManager.findUuidByName(playerName); } catch (Throwable ignored) {}
        }
        if (nameUuid == null && statsManager != null) {
            try { nameUuid = statsManager.findUuidByName(playerName); } catch (Throwable ignored) {}
        }
        final boolean wantHead = isHeadsEnabled(context)
                && (template.contains("%head%")
                    || isHeadsForceFirst());
        template = stripHeadPlaceholder(template);

        final String PH = "%player%";
        int idx = template.indexOf(PH);
        Component body;
        if (idx < 0) {
            idx = template.indexOf(playerName);
            if (idx < 0) {
                body = color(template);
            } else {
                String before = template.substring(0, idx);
                String after  = template.substring(idx + playerName.length());
                Component nameComp = clickableName(extractTrailingColor(before) + playerName, playerName, nameUuid, viewer, context);
                body = Component.text()
                        .append(color(before))
                        .append(nameComp)
                        .append(color(after))
                        .build();
            }
        } else {
            String before = template.substring(0, idx);
            String after  = template.substring(idx + PH.length());
            Component nameComp = clickableName(
                    extractTrailingColor(before) + playerName, playerName, nameUuid, viewer, context);
            body = Component.text()
                    .append(color(before))
                    .append(nameComp)
                    .append(color(after))
                    .build();
        }
        if (wantHead && headPlayer != null) {
            return Component.text().append(buildHeadComponent(headPlayer)).append(body).build();
        }
        return body;
    }


    /** Clickable name with hover (default context = chat). */
    Component clickableName(String coloredName, String playerName, UUID uuid, CommandSender viewer) {
        return clickableName(coloredName, playerName, uuid, viewer, "chat");
    }

    /** Clickable name with hover; context selects clickable_names.<context>. */
    Component clickableName(String coloredName, String playerName, UUID uuid, CommandSender viewer, String context) {
        if (!isClickableEnabled(context)) {
            return color(coloredName);
        }
        String cmd = getConfig().getString("clickable_names.click_command", "/msg %player% ");
        if (cmd == null) cmd = "/msg %player% ";
        cmd = cmd.replace("%player%", playerName);
        String action = getConfig().getString("clickable_names.click_action", "SUGGEST_COMMAND");
        if (action == null) action = "SUGGEST_COMMAND";
        ClickEvent click;
        String act = action.toUpperCase(java.util.Locale.ROOT);
        if (act.equals("RUN_COMMAND")) {
            click = ClickEvent.runCommand(cmd.startsWith("/") ? cmd : "/" + cmd);
        } else if (act.equals("COPY_TO_CLIPBOARD")) {
            click = ClickEvent.copyToClipboard(playerName);
        } else {
            click = ClickEvent.suggestCommand(cmd);
        }
        Component nameComp = color(coloredName).clickEvent(click);
        if (getConfig().getBoolean("hover.enabled", true)) {
            nameComp = nameComp.hoverEvent(HoverEvent.showText(buildHoverComponent(viewer, playerName, uuid)));
        }
        return nameComp;
    }

    /**
     * Hover при наведении на ник (как в TAB).
     * Приоритет: hover.lines → hover.format → lang messages.join_hover*
     * Плейсхолдеры: %player% %playtime% %playtime_seconds% %uuid%
     */
    Component buildHoverComponent(CommandSender viewer, String playerName, UUID uuid) {
        if (!getConfig().getBoolean("hover.enabled", true)) {
            return Component.empty();
        }

        boolean showPt = getConfig().getBoolean("hover.show_playtime", true)
                && getConfig().getBoolean("playtime.enabled", true)
                && playtimeManager != null;

        if (uuid == null) {
            Player online = Bukkit.getPlayerExact(playerName);
            if (online != null) uuid = online.getUniqueId();
            else if (playtimeManager != null) uuid = playtimeManager.findUuidByName(playerName);
        }
        long sec = 0L;
        if (showPt && playtimeManager != null && uuid != null) {
            sec = playtimeManager.getPlaytimeSeconds(uuid);
        }
        String ptStr = showPt ? formatDuration(sec, viewer) : "—";
        String uuidStr = uuid != null ? uuid.toString() : "—";

        java.util.List<String> lines = getConfig().getStringList("hover.lines");
        if (lines != null && !lines.isEmpty()) {
            net.kyori.adventure.text.TextComponent.Builder b = Component.text();
            boolean first = true;
            for (String line : lines) {
                if (line == null) continue;
                String filled = applyHoverPlaceholders(line, playerName, ptStr, sec, uuidStr);
                if (!first) b.append(Component.newline());
                first = false;
                b.append(color(filled));
            }
            return b.build();
        }

        String format = getConfig().getString("hover.format", "");
        if (format != null && !format.isEmpty()) {
            String raw = applyHoverPlaceholders(format, playerName, ptStr, sec, uuidStr);
            String[] parts = raw.split("\\n|\n");
            if (parts.length == 1) return color(parts[0]);
            net.kyori.adventure.text.TextComponent.Builder b = Component.text();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) b.append(Component.newline());
                b.append(color(parts[i]));
            }
            return b.build();
        }

        String key = showPt ? "messages.join_hover_playtime" : "messages.join_hover";
        String raw = applyHoverPlaceholders(tAny(viewer, key), playerName, ptStr, sec, uuidStr);
        String[] parts = raw.split("\\n|\n");
        if (parts.length == 1) return color(parts[0]);
        net.kyori.adventure.text.TextComponent.Builder b = Component.text();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) b.append(Component.newline());
            b.append(color(parts[i]));
        }
        return b.build();
    }

    private String applyHoverPlaceholders(String raw, String playerName, String playtime, long sec, String uuid) {
        if (raw == null) return "";
        return raw
                .replace("%player%", playerName != null ? playerName : "")
                .replace("%playtime%", playtime != null ? playtime : "—")
                .replace("%playtime_seconds%", String.valueOf(sec))
                .replace("%uuid%", uuid != null ? uuid : "—");
    }

    // ──────────────────────────────────────────────────────────────
    //  Tab complete
    // ──────────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("chatsync") || name.equals("csync")) {
            if (args.length == 1) {
                List<String> subs = new ArrayList<>();
                for (String s : List.of("info", "reload", "gui", "menu", "version", "about")) {
                    if (s.startsWith(args[0].toLowerCase())) subs.add(s);
                }
                return subs;
            }
            return List.of();
        }
        if (args.length == 1 && (name.equals("msg") || name.equals("ignore") || name.equals("clear")
                || name.equals("chatstats") || name.equals("playtime") || name.equals("lastseen"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase()))
                    names.add(p.getName());
            if (name.equals("msg") && "console".startsWith(args[0].toLowerCase())) names.add("console");
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

    
    /** If format contains %head% (or heads.force_first), prepend head for online player. */
    private Component maybePrefixHead(Player player, String template, Component body) {
        return maybePrefixHead(player, template, body, "chat");
    }

    private Component maybePrefixHead(Player player, String template, Component body, String context) {
        if (player == null || !isHeadsEnabled(context)) {
            return body;
        }
        boolean inFormat = template != null && template.contains("%head%");
        boolean force = isHeadsForceFirst();
        if (!inFormat && !force) return body;
        Component head = buildHeadComponent(player);
        return Component.text().append(head).append(body).build();
    }

    private String stripHeadPlaceholder(String template) {
        if (template == null) return "";
        return template.replace("%head%", "");
    }

private Component buildChatComponent(String format, Player sender, String rawMessage, Player viewer) {
        if (format == null) format = "&7%player%&7: &f%message%";
        // {username-color} — цвет ника: LuckPerms (meta/prefix)
        String userColor = resolveUsernameColor(sender);
        format = format.replace("{username-color}", userColor).replace("%username-color%", userColor);
        // голова всегда первой, если включено
        if (isHeadsEnabled("chat")
                && isHeadsForceFirst()
                && !format.contains("%head%")) {
            format = "%head%" + format;
        }

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
        for (int ti = 0; ti < tokens.size(); ti++) {
            String tok = tokens.get(ti);
            String next = (ti + 1 < tokens.size()) ? tokens.get(ti + 1) : null;
            if (tok.equals("%head%")) {
                result = result.append(buildHeadComponent(sender));
            } else if (tok.equals("%player%")) {
                String col = extractTrailingColor(literalSoFar.toString());
                if ((col == null || col.isEmpty()) && userColor != null && !userColor.isEmpty()) {
                    col = userColor;
                }
                if (col == null) col = "";
                Component nameComp = clickableName(
                        col + sender.getName(),
                        sender.getName(),
                        sender.getUniqueId(),
                        viewer);
                result = result.append(nameComp);
                literalSoFar.append(col).append(sender.getName());
            } else if (tok.equals("%message%")) {
                String col = extractTrailingColor(literalSoFar.toString());
                String msg = rawMessage == null ? "" : rawMessage;
                if (col != null && !col.isEmpty()) {
                    msg = col + msg;
                }
                result = result.append(LEGACY.deserialize(msg));
            } else {
                String lit = resolvePlaceholders(tok, sender);
                literalSoFar.append(lit);
                String visible = lit;
                if (next != null && (next.equals("%player%") || next.equals("%message%"))) {
                    visible = stripTrailingColorCodes(lit);
                }
                if (visible != null && !visible.isEmpty()) {
                    result = result.append(LEGACY.deserialize(visible));
                }
            }
        }
        return result;
    }

    /**
     * Голова игрока в чате (если клиент/Paper поддерживает object component).
     * Иначе — fallback-текст из конфига (можно оставить пустым).
     */
    Component buildHeadComponent(Player player) {
        if (player == null) return Component.empty();

        Component head = null;
        try {
            Object contents = buildPlayerHeadContents(player);
            if (contents != null) {
                head = toObjectComponent(contents);
            }
        } catch (Throwable t) {
            if (getConfig().getBoolean("heads.debug", getConfig().getBoolean("chat.heads.debug", false))) {
                getLogger().warning("[heads] native failed for " + player.getName() + ": " + t.getMessage());
            }
        }

        if (head == null) {
            String fb = getConfig().getString("heads.fallback", getConfig().getString("chat.heads.fallback", ""));
            if (fb == null || fb.isEmpty()) {
                if (getConfig().getBoolean("heads.debug", getConfig().getBoolean("chat.heads.debug", false))) {
                    getLogger().warning("[heads] empty for " + player.getName());
                }
                return Component.empty();
            }
            head = LEGACY.deserialize(fb);
        }

        // Space after head so it doesn't stick to [G]/] / name
        String gap = headsGap();
        if (gap == null) gap = " ";
        if (gap.isEmpty()) return head;
        return Component.text().append(head).append(LEGACY.deserialize(gap)).build();
    }


    /**
     * Head for Console in /msg console (and similar).
     * Set console_pm.head_texture to a base64 skin value (from mineskin / minecraft-heads).
     */
    Component buildConsoleHeadComponent() {
        if (!getConfig().getBoolean("console_pm.show_head", true)) return Component.empty();
        try {
            String name = getConfig().getString("console_pm.head_name", "Console");
            if (name == null || name.isEmpty()) name = "Console";
            UUID id;
            try {
                id = UUID.fromString(getConfig().getString("console_pm.head_uuid", "00000000-0000-0000-0000-0000000000c0"));
            } catch (Exception e) {
                id = UUID.fromString("00000000-0000-0000-0000-0000000000c0");
            }
            // Default texture: light gray face (public texture). Override in config.
            String tex = getConfig().getString("console_pm.head_texture",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGExNjAzOGJjOGU2NTE4YWZhOTE0OThkYWI3Njc1YzAxY2IzMWExMjVkMjFjNDliODYxMjk0ZDM5ZTFjNTYwYyJ9fX0=");
            if (tex != null && tex.isEmpty()) tex = null;
            String sig = getConfig().getString("console_pm.head_signature", "");
            if (sig != null && sig.isEmpty()) sig = null;

            Object contents = buildHeadContentsFromTexture(name, id, tex, sig);
            if (contents == null) return Component.empty();
            Component head = toObjectComponent(contents);
            if (head == null) return Component.empty();
            String gap = headsGap();
            if (gap == null || gap.isEmpty()) return head;
            return Component.text().append(head).append(LEGACY.deserialize(gap)).build();
        } catch (Throwable t) {
            if (getConfig().getBoolean("heads.debug", getConfig().getBoolean("chat.heads.debug", false))) {
                getLogger().warning("[heads] console head failed: " + t.getMessage());
            }
            return Component.empty();
        }
    }

    private Object buildHeadContentsFromTexture(String name, UUID id, String textureValue, String signature) throws Exception {
        Object builder = null;
        Class<?> builderType = null;
        for (String ownerName : new String[]{
                "net.kyori.adventure.text.object.PlayerHeadObjectContents",
                "net.kyori.adventure.text.object.ObjectContents"
        }) {
            try {
                Class<?> owner = Class.forName(ownerName);
                java.lang.reflect.Method factory = owner.getMethod("playerHead");
                builder = factory.invoke(null);
                builderType = factory.getReturnType();
                if (builder != null && builderType != null) break;
            } catch (ReflectiveOperationException ignored) {}
        }
        if (builder == null || builderType == null) return null;
        builder = fluent(builder, builderType, "name", new Class<?>[]{String.class}, name);
        builder = fluent(builder, builderType, "id", new Class<?>[]{java.util.UUID.class}, id);
        builder = fluent(builder, builderType, "hat", new Class<?>[]{boolean.class}, Boolean.TRUE);
        if (textureValue != null && !textureValue.isEmpty()) {
            applyTextureProperty(builder, builderType, textureValue, signature);
        }
        try {
            return builderType.getMethod("build").invoke(builder);
        } catch (NoSuchMethodException e) {
            return builder;
        }
    }


        /**
     * Player head contents with real skin textures (profile / SkinsRestorer).
     * name+id alone often resolves to default Steve/Alex on the client.
     */
    private Object buildPlayerHeadContents(Player player) throws Exception {
        String[] tex = resolveSkinTextures(player); // [value, signature] signature may be null

        Object builder = null;
        Class<?> builderType = null;

        for (String ownerName : new String[]{
                "net.kyori.adventure.text.object.PlayerHeadObjectContents",
                "net.kyori.adventure.text.object.ObjectContents"
        }) {
            try {
                Class<?> owner = Class.forName(ownerName);
                java.lang.reflect.Method factory = owner.getMethod("playerHead");
                builder = factory.invoke(null);
                builderType = factory.getReturnType();
                if (builder != null && builderType != null) break;
            } catch (ReflectiveOperationException ignored) {}
        }

        // Prefer builder + textures (correct skin). Fall back to playerHead(Player).
        if (builder != null && builderType != null) {
            builder = fluent(builder, builderType, "name", new Class<?>[]{String.class}, player.getName());
            builder = fluent(builder, builderType, "id", new Class<?>[]{java.util.UUID.class}, player.getUniqueId());
            builder = fluent(builder, builderType, "hat", new Class<?>[]{boolean.class}, Boolean.TRUE);

            if (tex != null && tex[0] != null && !tex[0].isEmpty()) {
                applyTextureProperty(builder, builderType, tex[0], tex.length > 1 ? tex[1] : null);
            }

            try {
                return builderType.getMethod("build").invoke(builder);
            } catch (NoSuchMethodException e) {
                return builder;
            }
        }

        // Last resort: ObjectContents.playerHead(Player) — may still be Steve/Alex without textures
        try {
            Class<?> oc = Class.forName("net.kyori.adventure.text.object.ObjectContents");
            try {
                return oc.getMethod("playerHead", org.bukkit.entity.Player.class).invoke(null, player);
            } catch (NoSuchMethodException ignored) {}
            try {
                return oc.getMethod("playerHead", org.bukkit.OfflinePlayer.class).invoke(null, player);
            } catch (NoSuchMethodException ignored) {}
        } catch (ClassNotFoundException ignored) {}

        return null;
    }

    /**
     * @return String[]{textureValue, signature} or null
     */
    /**
     * Texture for offline/transferred players: SkinsRestorer → Paper profile cache.
     * Used by GUI skulls so old stats still show real skins.
     */
    /**
     * Offline textures for GUI skulls. NEVER blocks on Mojang/HTTP — main-thread safe.
     * Order: online player → local Paper profile cache only → local SkinsRestorer player storage.
     * Does not call findSkinData/Mojang API (those freeze the server).
     */
    public String[] resolveSkinTexturesOffline(UUID uuid, String name) {
        if (uuid == null && (name == null || name.isEmpty())) return null;
        if (uuid != null) {
            String[] cached = skinTextureCache.get(uuid);
            if (cached != null) return cached;
        }
        // Online first (already in memory)
        if (uuid != null) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                String[] tex = resolveSkinTextures(online);
                if (tex != null) { putCachedSkinTextures(uuid, tex); return tex; }
            }
        }
        if (name != null) {
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                String[] tex = resolveSkinTextures(online);
                if (tex != null) { putCachedSkinTextures(uuid, tex); return tex; }
            }
        }
        // Paper local profile cache only — no network
        try {
            Object profile = null;
            if (uuid != null && name != null) {
                profile = Bukkit.class.getMethod("createProfile", UUID.class, String.class)
                        .invoke(null, uuid, name);
            } else if (uuid != null) {
                profile = Bukkit.class.getMethod("createProfile", UUID.class).invoke(null, uuid);
            } else {
                profile = Bukkit.class.getMethod("createProfile", String.class).invoke(null, name);
            }
            if (profile != null) {
                try {
                    profile.getClass().getMethod("completeFromCache").invoke(profile);
                } catch (NoSuchMethodException ignored) {}
                String[] fromProfile = texturesFromProfile(profile);
                if (fromProfile != null) { putCachedSkinTextures(uuid, fromProfile); return fromProfile; }
            }
        } catch (Throwable ignored) {}
        // SkinsRestorer: local player-skin storage only (no Mojang lookup)
        if (Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer") && uuid != null) {
            try {
                String[] fromSr = texturesFromSkinsRestorerLocalOnly(uuid);
                if (fromSr != null) { putCachedSkinTextures(uuid, fromSr); return fromSr; }
            } catch (Throwable t) {
                if (getConfig().getBoolean("heads.debug", getConfig().getBoolean("chat.heads.debug", false))) {
                    getLogger().warning("[heads] SR local: " + t.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * SkinsRestorer local player storage only — never calls Mojang/findSkinData.
     */
    private String[] texturesFromSkinsRestorerLocalOnly(UUID uuid) throws Exception {
        if (uuid == null) return null;
        Class<?> provider = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
        Object api = provider.getMethod("get").invoke(null);
        if (api == null) return null;
        Object skinData = null;
        try {
            Object playerStorage = api.getClass().getMethod("getPlayerStorage").invoke(api);
            Object opt = playerStorage.getClass()
                    .getMethod("getSkinOfPlayer", UUID.class)
                    .invoke(playerStorage, uuid);
            if (opt instanceof java.util.Optional && ((java.util.Optional<?>) opt).isPresent()) {
                skinData = ((java.util.Optional<?>) opt).get();
            }
        } catch (ReflectiveOperationException ignored) {}
        if (skinData == null) return null;
        return texturesFromSkinDataObject(skinData);
    }

    private String[] texturesFromSkinDataObject(Object skinData) {
        if (skinData == null) return null;
        try {
            // Property / SkinProperty style
            for (String gm : new String[]{"getValue", "getTextureValue", "value", "getPropertyValue"}) {
                try {
                    Object v = skinData.getClass().getMethod(gm).invoke(skinData);
                    if (v instanceof String s && !s.isEmpty()) {
                        String sig = null;
                        for (String sm : new String[]{"getSignature", "signature"}) {
                            try {
                                Object sg = skinData.getClass().getMethod(sm).invoke(skinData);
                                if (sg instanceof String ss) sig = ss;
                            } catch (Throwable ignored) {}
                        }
                        return new String[]{s, sig};
                    }
                } catch (NoSuchMethodException ignored) {}
            }
            // Nested property
            for (String pm : new String[]{"getProperty", "getTexture", "getSkinProperty"}) {
                try {
                    Object prop = skinData.getClass().getMethod(pm).invoke(skinData);
                    if (prop != null) {
                        String[] nested = texturesFromSkinDataObject(prop);
                        if (nested != null) return nested;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String[] resolveSkinTextures(Player player) {
        // 1) Paper PlayerProfile properties
        try {
            Object profile = player.getClass().getMethod("getPlayerProfile").invoke(player);
            String[] fromProfile = texturesFromProfile(profile);
            if (fromProfile != null) return fromProfile;
        } catch (Throwable ignored) {}

        // 2) SkinsRestorer API (soft)
        if (Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer")) {
            try {
                String[] fromSr = texturesFromSkinsRestorer(player);
                if (fromSr != null) return fromSr;
            } catch (Throwable t) {
                if (getConfig().getBoolean("heads.debug", getConfig().getBoolean("chat.heads.debug", false))) {
                    getLogger().warning("[heads] SkinsRestorer: " + t.getMessage());
                }
            }
        }

        // 3) Bukkit GameProfile via reflection (CraftPlayer)
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object gp = null;
            for (String mName : new String[]{"getGameProfile", "gameProfile", "getProfile"}) {
                try {
                    gp = handle.getClass().getMethod(mName).invoke(handle);
                    if (gp != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (gp != null) {
                Object props = gp.getClass().getMethod("getProperties").invoke(gp);
                // PropertyMap: get("textures")
                try {
                    Object coll = props.getClass().getMethod("get", Object.class).invoke(props, "textures");
                    if (coll instanceof java.util.Collection && !((java.util.Collection<?>) coll).isEmpty()) {
                        Object prop = ((java.util.Collection<?>) coll).iterator().next();
                        String value = String.valueOf(prop.getClass().getMethod("getValue").invoke(prop));
                        String sig = null;
                        try {
                            Object s = prop.getClass().getMethod("getSignature").invoke(prop);
                            if (s != null) sig = String.valueOf(s);
                        } catch (Throwable ignored) {}
                        if (value != null && !value.isEmpty() && !value.equals("null")) {
                            return new String[]{value, sig};
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private String[] texturesFromProfile(Object profile) {
        if (profile == null) return null;
        try {
            Object props = profile.getClass().getMethod("getProperties").invoke(profile);
            if (!(props instanceof java.util.Collection)) return null;
            for (Object prop : (java.util.Collection<?>) props) {
                String pname;
                try {
                    pname = String.valueOf(prop.getClass().getMethod("getName").invoke(prop));
                } catch (NoSuchMethodException e) {
                    try {
                        pname = String.valueOf(prop.getClass().getMethod("name").invoke(prop));
                    } catch (NoSuchMethodException e2) {
                        continue;
                    }
                }
                if (!"textures".equalsIgnoreCase(pname)) continue;
                String value = String.valueOf(prop.getClass().getMethod("getValue").invoke(prop));
                String sig = null;
                try {
                    Object s = prop.getClass().getMethod("getSignature").invoke(prop);
                    if (s != null) sig = String.valueOf(s);
                } catch (Throwable ignored) {}
                if (value != null && !value.isEmpty() && !value.equals("null")) {
                    return new String[]{value, sig};
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String[] texturesFromSkinsRestorer(Player player) throws Exception {
        // SkinsRestorer API v15+: SkinsRestorerProvider.get().getPlayerStorage()...
        Class<?> provider = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
        Object api = provider.getMethod("get").invoke(null);
        if (api == null) return null;

        // Try getSkinData / player storage variants across SR versions
        Object skinData = null;
        try {
            Object playerStorage = api.getClass().getMethod("getPlayerStorage").invoke(api);
            Object opt = playerStorage.getClass()
                    .getMethod("getSkinOfPlayer", java.util.UUID.class)
                    .invoke(playerStorage, player.getUniqueId());
            if (opt instanceof java.util.Optional && ((java.util.Optional<?>) opt).isPresent()) {
                skinData = ((java.util.Optional<?>) opt).get();
            }
        } catch (NoSuchMethodException ignored) {}

        if (skinData == null) {
            try {
                Object skinStorage = api.getClass().getMethod("getSkinStorage").invoke(api);
                // getSkinData(name)
                skinData = skinStorage.getClass()
                        .getMethod("getSkinData", String.class)
                        .invoke(skinStorage, player.getName());
                if (skinData instanceof java.util.Optional) {
                    java.util.Optional<?> opt = (java.util.Optional<?>) skinData;
                    skinData = opt.isPresent() ? opt.get() : null;
                }
            } catch (ReflectiveOperationException ignored) {}
        }

        if (skinData == null) return null;

        // Property / SkinProperty getValue getSignature
        try {
            Object prop = skinData;
            // Some versions wrap in SkinData with getTexture / getProperty
            for (String mName : new String[]{"getProperty", "getTexture", "getSkinProperty"}) {
                try {
                    prop = skinData.getClass().getMethod(mName).invoke(skinData);
                    if (prop != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            String value = null;
            String sig = null;
            try {
                value = String.valueOf(prop.getClass().getMethod("getValue").invoke(prop));
            } catch (NoSuchMethodException e) {
                try {
                    value = String.valueOf(prop.getClass().getMethod("value").invoke(prop));
                } catch (NoSuchMethodException e2) {
                    return null;
                }
            }
            try {
                Object s = prop.getClass().getMethod("getSignature").invoke(prop);
                if (s != null) sig = String.valueOf(s);
            } catch (Throwable ignored) {}
            if (value != null && !value.isEmpty() && !value.equals("null")) {
                return new String[]{value, sig};
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void applyTextureProperty(Object builder, Class<?> builderType, String value, String signature) {
        try {
            Class<?> ph = Class.forName("net.kyori.adventure.text.object.PlayerHeadObjectContents");
            Object prop;
            try {
                prop = ph.getMethod("property", String.class, String.class, String.class)
                        .invoke(null, "textures", value, signature);
            } catch (NoSuchMethodException e) {
                prop = ph.getMethod("property", String.class, String.class)
                        .invoke(null, "textures", value);
            }
            // profileProperty / profileProperties / addProperty
            for (String mName : new String[]{"profileProperty", "addProfileProperty", "property"}) {
                try {
                    java.lang.reflect.Method m = builderType.getMethod(mName, prop.getClass().getInterfaces().length > 0
                            ? findPropertyType(ph, prop) : prop.getClass());
                    Object out = m.invoke(builder, prop);
                    if (out != null) { /* fluent */ }
                    return;
                } catch (NoSuchMethodException ignored) {}
            }
            // try interface type from property return
            try {
                Class<?> propType = Class.forName(
                        "net.kyori.adventure.text.object.PlayerHeadObjectContents$ProfileProperty");
                java.lang.reflect.Method m = builderType.getMethod("profileProperty", propType);
                m.invoke(builder, prop);
                return;
            } catch (ReflectiveOperationException ignored) {}
            try {
                Class<?> propType = Class.forName(
                        "net.kyori.adventure.text.object.PlayerHeadObjectContents$ProfileProperty");
                builderType.getMethod("profileProperties", java.util.Collection.class)
                        .invoke(builder, java.util.List.of(prop));
            } catch (ReflectiveOperationException ignored) {}
        } catch (Throwable t) {
            if (getConfig().getBoolean("heads.debug", getConfig().getBoolean("chat.heads.debug", false))) {
                getLogger().warning("[heads] texture property: " + t.getMessage());
            }
        }
    }

    private static Class<?> findPropertyType(Class<?> ph, Object prop) {
        for (Class<?> iface : prop.getClass().getInterfaces()) {
            if (iface.getName().contains("ProfileProperty") || iface.getName().contains("Property")) {
                return iface;
            }
        }
        // nested class
        for (Class<?> c : ph.getClasses()) {
            if (c.getSimpleName().contains("Property")) return c;
        }
        return prop.getClass();
    }

    private static Object fluent(Object target, Class<?> api, String method, Class<?>[] types, Object... args) {
        try {
            java.lang.reflect.Method m = api.getMethod(method, types);
            Object out = m.invoke(target, args);
            return out != null ? out : target;
        } catch (Throwable t) {
            return target;
        }
    }

    private Component toObjectComponent(Object contents) {
        if (contents == null) return null;
        try {
            Class<?> oc = Class.forName("net.kyori.adventure.text.object.ObjectContents");
            try {
                java.lang.reflect.Method m = Component.class.getMethod("object", oc);
                Object head = m.invoke(null, contents);
                if (head instanceof Component) return (Component) head;
            } catch (NoSuchMethodException ignored) {}
            for (java.lang.reflect.Method m : Component.class.getMethods()) {
                if (!"object".equals(m.getName()) || m.getParameterCount() != 1) continue;
                try {
                    Object head = m.invoke(null, contents);
                    if (head instanceof Component) return (Component) head;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    Component buildNameComponentPublic(String template, Player player) {
        return buildNameComponentPublic(template, player, "chat");
    }

    Component buildNameComponentPublic(String template, Player player, String context) {
        if (template == null) template = "";
        if (context == null) context = "chat";
        final boolean wantHead = isHeadsEnabled(context)
                && (template.contains("%head%") || isHeadsForceFirst());
        template = stripHeadPlaceholder(template);

        final String PH = "%player%";
        int idx = template.indexOf(PH);
        Component body;
        if (idx == -1) {
            body = LEGACY.deserialize(resolvePlaceholders(template, player));
        } else {
            String before = resolvePlaceholders(template.substring(0, idx), player);
            String after  = resolvePlaceholders(template.substring(idx + PH.length()), player);
            Component nameComp = clickableName(
                    extractTrailingColor(before) + player.getName(),
                    player.getName(),
                    player.getUniqueId(),
                    player,
                    context);
            body = Component.text()
                    .append(LEGACY.deserialize(before))
                    .append(nameComp)
                    .append(LEGACY.deserialize(after))
                    .build();
        }
        if (wantHead) {
            return Component.text().append(buildHeadComponent(player)).append(body).build();
        }
        return body;
    }


    private Component formatPM(String format, Player self, Player other, String ignoredHover) {
        if (format == null) format = "";
        // Tokens: %head_self% %head_other% %head% %sender% %receiver% %message%
        // %head% legacy = other player's head once at start if force_first
        boolean force = isHeadsForceFirst() && isHeadsEnabled("pm");
        if (force && !format.contains("%head_self%") && !format.contains("%head_other%") && !format.contains("%head%")) {
            format = "%head_self%%head_other%" + format;
        }
        format = format.replace("%head%", "%head_other%"); // legacy

        net.kyori.adventure.text.TextComponent.Builder out = Component.text();
        String rest = format;
        while (!rest.isEmpty()) {
            int next = rest.length();
            String which = null;
            for (String token : new String[]{"%head_self%", "%head_other%", "%sender%", "%receiver%", "%message%"}) {
                int i = rest.indexOf(token);
                if (i >= 0 && i < next) {
                    next = i;
                    which = token;
                }
            }
            if (which == null) {
                out.append(color(rest));
                break;
            }
            if (next > 0) {
                out.append(color(rest.substring(0, next)));
            }
            String afterToken = rest.substring(next + which.length());
            String colorCarry = extractTrailingColor(rest.substring(0, next));
            switch (which) {
                case "%head_self%" -> {
                    if (self != null) out.append(buildHeadComponent(self));
                }
                case "%head_other%" -> {
                    if (other != null) out.append(buildHeadComponent(other));
                }
                case "%sender%", "%receiver%" -> {
                    Player named = other;
                    if (named != null) {
                        out.append(clickableName(
                                colorCarry + named.getName(),
                                named.getName(),
                                named.getUniqueId(),
                                self));
                    }
                }
                case "%message%" -> {
                    // message already substituted into format string usually;
                    // if still present as token, leave empty
                }
                default -> {}
            }
            rest = afterToken;
        }
        return out.build();
    }




    // ──────────────────────────────────────────────────────────────
    //  Discord
    // ──────────────────────────────────────────────────────────────


    /**
     * Send a plain system line to DiscordSRV main chat (no "[name head]" artifacts).
     */
    void relayGameMessageToDiscord(Component message) {
        if (message == null) return;
        if (!getConfig().getBoolean("discord.enabled", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) return;
        final String plain = plainComponent(message);
        if (plain.isEmpty()) return;
        final String preferred = getConfig().getString("discord.system_channel", "deaths");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Class<?> dsrvCls = Class.forName("github.scarsz.discordsrv.DiscordSRV");
                Object dsrvPlugin = dsrvCls.getMethod("getPlugin").invoke(null);
                if (dsrvPlugin == null) {
                    getLogger().warning("[Discord] DiscordSRV.getPlugin() is null");
                    return;
                }
                Class<?> discordUtil = Class.forName("github.scarsz.discordsrv.util.DiscordUtil");
                try {
                    Object jda = discordUtil.getMethod("getJda").invoke(null);
                    if (jda == null) {
                        getLogger().warning("[Discord] JDA not ready — skipped: " + plain);
                        return;
                    }
                } catch (Throwable ignored) {}

                Object channel = resolveDiscordChannel(dsrvPlugin, preferred);
                if (channel == null) {
                    getLogger().warning("[Discord] No channel for system message. "
                            + "Set DiscordSRV Channels {\"global\":\"ID\",\"deaths\":\"ID\"}. Msg: " + plain);
                    return;
                }

                boolean sent = sendDiscordChannelMessage(discordUtil, channel, plain);
                if (!sent) {
                    getLogger().warning("[Discord] Failed to send: " + plain
                            + " (channel class=" + channel.getClass().getName() + ")");
                } else if (getConfig().getBoolean("discord.debug", false)
                        || getConfig().getBoolean("advanced.debug", false)) {
                    getLogger().info("[Discord] Sent: " + plain);
                }
            } catch (Exception e) {
                getLogger().warning("[Discord] relay failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    /** Resolve DiscordSRV text channel without hard JDA class refs (classloader-safe). */
    private Object resolveDiscordChannel(Object dsrvPlugin, String preferred) {
        for (String gameName : new String[]{
                preferred, "deaths", "global", "main", "chat"
        }) {
            if (gameName == null || gameName.isEmpty()) continue;
            try {
                Object ch = dsrvPlugin.getClass()
                        .getMethod("getDestinationTextChannelForGameChannelName", String.class)
                        .invoke(dsrvPlugin, gameName);
                if (ch != null) return ch;
            } catch (Throwable ignored) {}
        }
        try {
            Object opt = dsrvPlugin.getClass().getMethod("getOptionalMainTextChannel").invoke(dsrvPlugin);
            if (opt instanceof java.util.Optional<?> o && o.isPresent()) return o.get();
        } catch (Throwable ignored) {}
        try {
            return dsrvPlugin.getClass().getMethod("getMainTextChannel").invoke(dsrvPlugin);
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Classloader-safe send: DiscordUtil methods are matched by parameter assignability
     * against the live channel instance (JDA lives in DiscordSRV's loader, not ours).
     */
    private boolean sendDiscordChannelMessage(Class<?> discordUtil, Object channel, String plain) {
        // 1) DiscordUtil.sendMessage / queueMessage / sendMessageBlocking
        for (String name : new String[]{"queueMessage", "sendMessage", "sendMessageBlocking"}) {
            for (java.lang.reflect.Method m : discordUtil.getMethods()) {
                if (!m.getName().equals(name)) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length < 2) continue;
                if (!pt[0].isInstance(channel)) continue;
                if (pt[1] != String.class) continue;
                try {
                    m.setAccessible(true);
                    if (pt.length == 2) {
                        m.invoke(null, channel, plain);
                        return true;
                    }
                    if (pt.length == 3 && (pt[2] == boolean.class || pt[2] == Boolean.class)) {
                        m.invoke(null, channel, plain, false);
                        return true;
                    }
                    if (pt.length == 3 && pt[2] == int.class) {
                        m.invoke(null, channel, plain, 0);
                        return true;
                    }
                } catch (Throwable t) {
                    getLogger().warning("[Discord] " + name + " invoke error: " + t.getMessage());
                }
            }
        }
        // 2) Direct JDA: channel.sendMessage(plain).queue()
        try {
            java.lang.reflect.Method sendMessage = null;
            for (java.lang.reflect.Method m : channel.getClass().getMethods()) {
                if (!m.getName().equals("sendMessage")) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 1 && pt[0] == String.class) {
                    sendMessage = m;
                    break;
                }
            }
            if (sendMessage != null) {
                Object action = sendMessage.invoke(channel, plain);
                if (action != null) {
                    for (java.lang.reflect.Method m : action.getClass().getMethods()) {
                        if (m.getName().equals("queue") && m.getParameterCount() == 0) {
                            m.invoke(action);
                            return true;
                        }
                    }
                    for (java.lang.reflect.Method m : action.getClass().getMethods()) {
                        if (m.getName().equals("queue") && m.getParameterCount() == 2) {
                            m.invoke(action, null, null);
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            getLogger().warning("[Discord] JDA channel.sendMessage error: " + t.getMessage());
        }
        return false;
    }


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

    
    /**
     * Username color for {username-color}:
     * 1) LuckPerms meta username-color / namecolor
     * 2) Last color code in LuckPerms prefix
     * 3) chat.username_color from config
     */
    private String resolveUsernameColor(Player player) {
        if (luckPermsHook != null && luckPermsHook.isAvailable()) {
            String fromLp = luckPermsHook.getNameColor(player);
            if (fromLp != null && !fromLp.isEmpty()) return fromLp;
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method m = papi.getMethod("setPlaceholders", Player.class, String.class);
                String prefix = (String) m.invoke(null, player, "%luckperms_prefix%");
                if (prefix != null && !prefix.isEmpty() && !prefix.equals("%luckperms_prefix%")) {
                    String c = extractTrailingColor(prefix);
                    if (c != null && !c.isEmpty()) return c;
                }
            } catch (Exception ignored) {}
        }
        return ""; // no config fallback
    }


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
            case "global" -> "global";
            case "local"  -> "local";
            case "me"     -> "/me";
            case "pm"     -> "PM";
            case "broadcast" -> "broadcast";
            default -> channel;
        };

        String reasonLabel = switch (reason) {
            case "same"  -> "repeat";
            case "caps"  -> "CAPS";
            case "flood" -> "flood";
            default -> "spam";
        };

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

    void logToConsolePublic(String message) {
        logToConsole(message);
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
        if (text == null || text.isEmpty()) return "";
        String s = text.replace("\u00A7", "&");
        int i = s.length();
        while (i > 0 && s.charAt(i - 1) == ' ') i--;
        StringBuilder found = new StringBuilder();
        while (i > 0) {
            if (i >= 8 && s.charAt(i - 8) == '&' && s.charAt(i - 7) == '#') {
                String hex = s.substring(i - 8, i);
                if (hex.matches("(?i)&#[0-9a-f]{6}")) {
                    found.insert(0, hex);
                    i -= 8;
                    while (i > 0 && s.charAt(i - 1) == ' ') i--;
                    continue;
                }
            }
            if (i >= 5 && s.charAt(i - 5) == '&' && s.charAt(i - 4) == '#') {
                String hex = s.substring(i - 5, i);
                if (hex.matches("(?i)&#[0-9a-f]{3}")) {
                    found.insert(0, hex);
                    i -= 5;
                    while (i > 0 && s.charAt(i - 1) == ' ') i--;
                    continue;
                }
            }
            if (i >= 2 && s.charAt(i - 2) == '&') {
                char c = Character.toLowerCase(s.charAt(i - 1));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || "klmnor".indexOf(c) >= 0) {
                    found.insert(0, "&" + c);
                    i -= 2;
                    while (i > 0 && s.charAt(i - 1) == ' ') i--;
                    continue;
                }
            }
            break;
        }
        return found.toString();
    }

    private String stripTrailingColorCodes(String text) {
        if (text == null || text.isEmpty()) return "";
        // Remove only trailing color codes (&x / &#RRGGBB), keep spaces
        // so " > &#D01C1C" stays " > " before %message%.
        String s = text.replace("\u00A7", "&");
        while (true) {
            String t = extractTrailingColor(s);
            if (t.isEmpty()) break;
            String lower = s.toLowerCase();
            int idx = lower.lastIndexOf(t.toLowerCase());
            if (idx < 0) break;
            // ensure this match is the trailing color sequence (only spaces after it)
            String after = s.substring(idx + t.length());
            if (!after.trim().isEmpty()) break;
            s = s.substring(0, idx) + after; // keep spaces that were after the code (usually none)
        }
        return s;
    }


    /** Online and visible to viewer (hides SuperVanish / PremiumVanish / Essentials vanish). */

    private void loadIgnoreList() {
        ignoreList.clear();
        File f = new File(getDataFolder(), "ignore.yml");
        if (!f.exists()) return;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
            org.bukkit.configuration.ConfigurationSection sec = yaml.getConfigurationSection("ignores");
            if (sec == null) {
                // flat format: player-uuid: [list]
                for (String key : yaml.getKeys(false)) {
                    try {
                        UUID who = UUID.fromString(key);
                        java.util.Set<UUID> set = java.util.concurrent.ConcurrentHashMap.newKeySet();
                        for (String s : yaml.getStringList(key)) {
                            try { set.add(UUID.fromString(s)); } catch (Exception ignored) {}
                        }
                        if (!set.isEmpty()) ignoreList.put(who, set);
                    } catch (IllegalArgumentException ignored) {}
                }
            } else {
                for (String key : sec.getKeys(false)) {
                    try {
                        UUID who = UUID.fromString(key);
                        java.util.Set<UUID> set = java.util.concurrent.ConcurrentHashMap.newKeySet();
                        for (String s : sec.getStringList(key)) {
                            try { set.add(UUID.fromString(s)); } catch (Exception ignored) {}
                        }
                        if (!set.isEmpty()) ignoreList.put(who, set);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            if (!ignoreList.isEmpty()) {
                getLogger().info("Ignore list: loaded " + ignoreList.size() + " player(s).");
            }
        } catch (Throwable t) {
            getLogger().warning("Could not load ignore.yml: " + t.getMessage());
        }
    }

    private void saveIgnoreList() {
        try {
            File f = new File(getDataFolder(), "ignore.yml");
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, Set<UUID>> e : ignoreList.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) continue;
                List<String> list = new ArrayList<>();
                for (UUID id : e.getValue()) list.add(id.toString());
                yaml.set("ignores." + e.getKey().toString(), list);
            }
            yaml.save(f);
        } catch (Throwable t) {
            getLogger().warning("Could not save ignore.yml: " + t.getMessage());
        }
    }

    private void loadSocialSpy() {
        socialSpy.clear();
        File f = new File(getDataFolder(), "spy.yml");
        if (!f.exists()) return;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
            List<String> list = yaml.getStringList("enabled");
            if (list == null) return;
            for (String s : list) {
                try {
                    socialSpy.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {}
            }
            if (!socialSpy.isEmpty()) {
                getLogger().info("Restored SocialSpy for " + socialSpy.size() + " player(s).");
            }
        } catch (Throwable t) {
            getLogger().warning("Could not load spy.yml: " + t.getMessage());
        }
    }

    private void saveSocialSpy() {
        try {
            File f = new File(getDataFolder(), "spy.yml");
            YamlConfiguration yaml = new YamlConfiguration();
            List<String> list = new ArrayList<>();
            for (UUID id : socialSpy) {
                list.add(id.toString());
            }
            yaml.set("enabled", list);
            yaml.save(f);
        } catch (Throwable t) {
            getLogger().warning("Could not save spy.yml: " + t.getMessage());
        }
    }

    private boolean isOnlineVisible(CommandSender viewer, Player target) {
        if (target == null || !target.isOnline()) return false;
        if (!getConfig().getBoolean("vanish.hide_lastseen_online", true)) {
            return true;
        }
        boolean vanished = vanishHook != null && vanishHook.isVanished(target);
        if (viewer instanceof Player p) {
            try {
                if (!p.canSee(target)) return false;
            } catch (Throwable ignored) {}
            if (vanished) {
                java.util.List<String> perms = getConfig().getStringList("vanish.see_permissions");
                if (perms == null || perms.isEmpty()) {
                    perms = java.util.List.of("sv.see", "pv.see", "essentials.vanish.see", "chatsync.vanish.see");
                }
                for (String perm : perms) {
                    if (perm != null && !perm.isEmpty() && p.hasPermission(perm)) return true;
                }
                return false;
            }
            return true;
        }
        return !vanished;
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
        String s = text.replace("\u00A7", "&");
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
        if (!sender.hasPermission(getConfig().getString("advanced.color_permission", "chatsync.color"))) message = stripColorCodes(message);

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
            Component line = buildClickableNameLine(out, sender.getName(), p, "team");
            p.sendMessage(line);
        }
        // SocialSpy — team chat visible to spies who are not in the team
        if (tog("socialspy") && getConfig().getBoolean("socialspy.team_chat", true)) {
            String spyTeamFmt = getConfig().getString("pm.format_spy_team",
                    "%head%&8[SPY-T] &7%player%&8: &7%message%");
            if (spyTeamFmt == null) spyTeamFmt = "%head%&8[SPY-T] &7%player%&8: &7%message%";
            spyTeamFmt = spyTeamFmt.replace("%message%", message)
                    .replace("%team%", team.name != null ? team.name : "");
            for (UUID uid : socialSpy) {
                if (team.members.contains(uid)) continue;
                Player spy = Bukkit.getPlayer(uid);
                if (spy == null || spy.equals(sender)) continue;
                spy.sendMessage(buildClickableNameLine(spyTeamFmt, sender.getName(), spy, "team"));
            }
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
                        {
                            String sent = t(player, "team.invite_sent")
                                    .replace("%team%", team != null ? team.name : "");
                            if (!sent.contains("%player%")) sent = sent + " &f%player%";
                            player.sendMessage(buildClickableNameLine(sent, target.getName(), player));
                        }
                        // Clickable accept / deny
                        String invTpl = t(target, "team.invite_received")
                                .replace("%team%", team != null ? team.name : "");
                        if (!invTpl.contains("%player%")) invTpl = "&e%player% &7→ " + invTpl;
                        Component base = buildClickableNameLine(invTpl, player.getName(), target);
                        Component accept = color(" " + t(target, "team.btn_accept"))
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
                                    {
                                        String mj = t(p, "team.member_joined").replace("%team%", team.name);
                                        p.sendMessage(buildClickableNameLine(mj, player.getName(), p));
                                    }
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
                                p.sendMessage(buildClickableNameLine(t(p, "team.member_left"), player.getName(), p));
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
