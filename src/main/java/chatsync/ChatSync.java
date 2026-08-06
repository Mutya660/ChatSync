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

public class ChatSync extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // ──────────────────────────────────────────────────────────────
    //  State
    // ──────────────────────────────────────────────────────────────

    private final Map<UUID, UUID>      lastMessaged  = new HashMap<>();
    private final Map<UUID, Set<UUID>> ignoreList    = new HashMap<>();
    private final Set<UUID>            socialSpy     = new HashSet<>();
    /** UUID → timestamp последнего глобального сообщения (для кулдауна) */
    private final Map<UUID, Long>      globalCooldown = new HashMap<>();
    private final Map<String, YamlConfiguration> langConfigs = new HashMap<>();

    /** Ожидающие подтверждения запросы /clear: ключ отправителя → цель + время истечения. */
    private final Map<UUID, PendingClear> pendingClears = new HashMap<>();
    private static final UUID CONSOLE_UUID = new UUID(0L, 0L);

    private ChatStatsManager statsManager;
    private ChatLogger       chatLogger;
    private LuckPermsHook    luckPermsHook;
    private CoreProtectHook  coreProtectHook;

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

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new DeathMessageTranslator(this), this);

        registerCmd("msg",        this);
        registerCmd("reply",      this);
        registerCmd("ignore",     this);
        registerCmd("ignorelist", this);
        registerCmd("socialspy",  this);
        registerCmd("chatsync",   this);
        registerCmd("me",         this);
        registerCmd("clear",      this);
        registerCmd("chatstats",  this);
        registerCmd("broadcast",  this);

        if (getConfig().getBoolean("stats.enabled", true)) {
            long intervalTicks = 20L * Math.max(30, getConfig().getInt("stats.save_interval", 300));
            Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                    () -> statsManager.saveIfDirty(), intervalTicks, intervalTicks);
        }

        // Периодическая очистка просроченных заявок /clear, чтобы карта не росла бесконечно.
        Bukkit.getScheduler().runTaskTimer(this, this::purgeExpiredClears, 20L * 60, 20L * 60);

        getLogger().info("ChatSync v" + getDescription().getVersion() + " enabled!");
        if (luckPermsHook.isAvailable()) getLogger().info("LuckPerms detected: direct API fallback enabled.");
        if (coreProtectHook.isAvailable()) getLogger().info("CoreProtect detected: /clear will be logged for /co lookup.");
    }

    @Override
    public void onDisable() {
        if (statsManager != null) statsManager.save();
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
        String locale = player.locale().toString().toLowerCase().replace("-", "_");
        String mapped = LOCALE_MAP.get(locale);
        if (mapped != null) return mapped;
        if (locale.length() >= 2) {
            String prefix = locale.substring(0, 2);
            if (langConfigs.containsKey(prefix)) return prefix;
        }
        return getConfig().getString("language", "en");
    }

    private String t(Player player, String key) { return t(getLang(player), key); }

    private String t(String lang, String key) {
        YamlConfiguration cfg = langConfigs.get(lang);
        if (cfg != null && cfg.contains(key)) return cfg.getString(key, key);
        YamlConfiguration en = langConfigs.get("en");
        if (en != null && en.contains(key)) return en.getString(key, key);
        return key;
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
        if (!tog("join_message")) { event.joinMessage(null); return; }
        event.joinMessage(buildJoinQuitMessage(
                getConfig().getString("messages.join", "&a+ &f%player%"), player));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!tog("quit_message")) { event.quitMessage(null); }
        else event.quitMessage(buildJoinQuitMessage(
                getConfig().getString("messages.quit", "&c- &f%player%"), player));
        lastMessaged.remove(player.getUniqueId());
        socialSpy.remove(player.getUniqueId());
        globalCooldown.remove(player.getUniqueId());
        pendingClears.remove(player.getUniqueId());
    }

    private Component buildJoinQuitMessage(String template, Player player) {
        String hover = t(player, "messages.join_hover").replace("%player%", player.getName());
        return buildNameComponent(template, player, hover);
    }

    // ──────────────────────────────────────────────────────────────
    //  Chat
    // ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);

        Player sender     = event.getPlayer();
        String rawMessage = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());

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

        // ── Кулдаун глобального чата ───────────────────────────────
        if (isGlobal && !sender.hasPermission("chatsync.bypass_cooldown")) {
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
        }

        if (isGlobal) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && isIgnoring(p, sender)) continue;
                p.sendMessage(buildChatComponent(formatStr, sender, rawMessage, p));
            }
            logToConsole("[GlobalChat] " + sender.getName() + ": " + rawMessage);
            sendToDiscord(sender, rawMessage, "global");
            statsManager.record(sender.getUniqueId(), sender.getName(), ChatStatsManager.MessageType.GLOBAL);
            logChat("[GLOBAL] " + sender.getName() + ": " + stripColorCodes(rawMessage));
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
                        .replace("%player%", sender.getName())
                        .replace("%message%", rawMessage);
                for (UUID uid : socialSpy) {
                    Player spy = Bukkit.getPlayer(uid);
                    if (spy == null || spy.equals(sender)) continue;
                    // Не дублируем тем, кто уже видел сообщение (был в радиусе)
                    if (spy.getWorld().equals(sender.getWorld()) &&
                            spy.getLocation().distance(sender.getLocation()) <= radius) continue;
                    spy.sendMessage(color(spyLocalFmt));
                }
            }
            statsManager.record(sender.getUniqueId(), sender.getName(), ChatStatsManager.MessageType.LOCAL);
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

        String rawMessage = joinArgs(args, 0);
        if (!pSender.hasPermission("chatsync.color")) rawMessage = stripColorCodes(rawMessage);

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
        statsManager.record(pSender.getUniqueId(), pSender.getName(), ChatStatsManager.MessageType.ME);
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

        if (args.length == 0) {
            int topSize = getConfig().getInt("stats.top_size", 10);
            List<Map.Entry<UUID, ChatStatsManager.PlayerStats>> top = statsManager.top(topSize);
            sender.sendMessage(color(tAny(sender, "chatstats.top_header").replace("%count%", String.valueOf(top.size()))));
            int rank = 1;
            for (Map.Entry<UUID, ChatStatsManager.PlayerStats> entry : top) {
                String line = tAny(sender, "chatstats.top_entry")
                        .replace("%rank%", String.valueOf(rank++))
                        .replace("%player%", statsManager.nameOf(entry.getKey()))
                        .replace("%total%", String.valueOf(entry.getValue().total()));
                sender.sendMessage(color(line));
            }
            return true;
        }

        String  targetName = args[0];
        boolean isSelf     = sender instanceof Player p0 && p0.getName().equalsIgnoreCase(targetName);
        if (!isSelf) {
            String othersPermission = getConfig().getString("commands.chatstats.permission_others", "chatsync.chatstats.others");
            if (!sender.hasPermission(othersPermission)) {
                sender.sendMessage(color(tAny(sender, "chatstats.no_permission_others")));
                return true;
            }
        }

        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
        UUID uuid = off.getUniqueId();
        ChatStatsManager.PlayerStats stats = statsManager.get(uuid);
        if (stats == null) {
            sender.sendMessage(color(tAny(sender, "chatstats.no_data").replace("%player%", targetName)));
            return true;
        }

        sender.sendMessage(color(tAny(sender, "chatstats.player_header").replace("%player%", statsManager.nameOf(uuid))));
        sender.sendMessage(color(tAny(sender, "chatstats.player_line")
                .replace("%global%", String.valueOf(stats.global))
                .replace("%local%", String.valueOf(stats.local))
                .replace("%pm%", String.valueOf(stats.pm))
                .replace("%me%", String.valueOf(stats.me))
                .replace("%total%", String.valueOf(stats.total()))));
        return true;
    }

    // ── /broadcast ───────────────────────────────────────────────

    private boolean cmdBroadcast(CommandSender sender, String[] args) {
        String permission = getConfig().getString("commands.broadcast.permission", "chatsync.broadcast");
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(color(tAny(sender, "broadcast.no_permission")));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(color(tAny(sender, "broadcast.usage")));
            return true;
        }

        String rawMessage = joinArgs(args, 0);
        if (sender instanceof Player pSender && !pSender.hasPermission("chatsync.color")) {
            rawMessage = stripColorCodes(rawMessage);
        }

        String    format    = getConfig().getString("broadcast.format", "&6&l[Announcement] &e%message%").replace("%message%", rawMessage);
        Component component = color(format);

        boolean actionbar   = getConfig().getBoolean("broadcast.actionbar", false);
        boolean titleEnable = getConfig().getBoolean("broadcast.title.enable", false);
        String  titleText   = getConfig().getString("broadcast.title.text", "%message%").replace("%message%", rawMessage);
        String  subtitle    = getConfig().getString("broadcast.title.subtitle", "").replace("%message%", rawMessage);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(component);
            if (actionbar) p.sendActionBar(component);
            if (titleEnable) p.showTitle(Title.title(color(titleText), color(subtitle)));
            playCustomSound(p, "broadcast.sound");
        }

        String executorName = sender instanceof Player p ? p.getName() : "Console";
        logToConsole("[Broadcast] " + executorName + ": " + rawMessage);
        logChat("[BROADCAST] " + executorName + ": " + stripColorCodes(rawMessage));
        if (sender instanceof Player p) sendToDiscord(p, rawMessage, "broadcast");
        return true;
    }

    // ──────────────────────────────────────────────────────────────
    //  Tab complete
    // ──────────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        if (args.length == 1 && (name.equals("msg") || name.equals("ignore") || name.equals("clear") || name.equals("chatstats"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase()))
                    names.add(p.getName());
            if (name.equals("clear")) names.add("confirm");
            return names;
        }
        if (name.equals("clear") && args.length == 2) return List.of("confirm");
        if (name.equals("chatsync") && args.length == 1) return List.of("reload");
        return List.of();
    }

    // ──────────────────────────────────────────────────────────────
    //  PM
    // ──────────────────────────────────────────────────────────────

    private void sendPM(Player from, Player to, String message) {
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
                    .replace("%sender%",   from.getName())
                    .replace("%receiver%", to.getName())
                    .replace("%message%",  message);
            for (UUID uid : socialSpy) {
                Player spy = Bukkit.getPlayer(uid);
                if (spy != null && !spy.equals(from) && !spy.equals(to))
                    spy.sendMessage(color(spyFmt));
            }
        }
        logToConsole("[PM] " + from.getName() + " → " + to.getName() + ": " + message);
        statsManager.record(from.getUniqueId(), from.getName(), ChatStatsManager.MessageType.PM);
        logChat("[PM] " + from.getName() + " -> " + to.getName() + ": " + stripColorCodes(message));
    }

    // ──────────────────────────────────────────────────────────────
    //  Component builders
    // ──────────────────────────────────────────────────────────────

    private Component buildChatComponent(String format, Player sender, String rawMessage, Player viewer) {
        final String PP = "%player%", MP = "%message%";
        int idx = format.indexOf(PP);
        if (idx == -1)
            return LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(resolvePlaceholders(format, sender).replace(MP, rawMessage));

        String before = resolvePlaceholders(format.substring(0, idx), sender);
        String after  = resolvePlaceholders(format.substring(idx + PP.length()), sender).replace(MP, rawMessage);
        String hover  = t(viewer, "messages.join_hover").replace("%player%", sender.getName());

        Component nameComp = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(extractTrailingColor(before) + sender.getName())
                .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "))
                .hoverEvent(HoverEvent.showText(color(hover)));

        return Component.text()
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(before))
                .append(nameComp)
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(after))
                .build();
    }

    private Component buildNameComponent(String template, Player player, String hoverText) {
        final String PH = "%player%";
        int idx = template.indexOf(PH);
        if (idx == -1)
            return LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(resolvePlaceholders(template, player));

        String before = resolvePlaceholders(template.substring(0, idx), player);
        String after  = resolvePlaceholders(template.substring(idx + PH.length()), player);

        Component nameComp = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(extractTrailingColor(before) + player.getName())
                .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "))
                .hoverEvent(HoverEvent.showText(color(hoverText)));

        return Component.text()
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(before))
                .append(nameComp)
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(after))
                .build();
    }

    private Component formatPM(String format, Player target, Player other, String hoverText) {
        int idx = format.indexOf("%sender%");
        String ph = "%sender%";
        if (idx == -1) { idx = format.indexOf("%receiver%"); ph = "%receiver%"; }
        if (idx == -1)
            return LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(resolvePlaceholders(format, target));

        String before = format.substring(0, idx);
        String after  = format.substring(idx + ph.length());

        Component nameComp = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(extractTrailingColor(before) + other.getName())
                .clickEvent(ClickEvent.suggestCommand("/msg " + other.getName() + " "))
                .hoverEvent(HoverEvent.showText(color(hoverText)));

        return Component.text()
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(resolvePlaceholders(before, target)))
                .append(nameComp)
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(resolvePlaceholders(after, target)))
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

    private void logToConsole(String message) {
        Bukkit.getConsoleSender().sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    /** Пишет строку в асинхронный файловый лог чата, если это включено в config.yml. */
    private void logChat(String line) {
        if (chatLogger != null && getConfig().getBoolean("logging.enabled", true)) {
            chatLogger.log(line);
        }
    }

    private String extractTrailingColor(String text) {
        if (text.length() >= 2 && text.charAt(text.length() - 2) == '&')
            return text.substring(text.length() - 2);
        return "&f";
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
        return text.replaceAll("(?i)&[0-9a-fk-or]", "");
    }

    /** Читает toggles.<key> из config.yml, по умолчанию true */
    private boolean tog(String key) {
        return getConfig().getBoolean("toggles." + key, true);
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
