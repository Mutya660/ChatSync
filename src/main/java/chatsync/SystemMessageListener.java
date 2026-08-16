package chatsync;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кастомные GM/TP только для staff.
 * Перехватывает /gamemode /gm /tp /teleport, чтобы убрать ванильный feedback.
 */
public class SystemMessageListener implements Listener {

    private final ChatSync plugin;
    /** UUID игроков, чей GM/TP мы уже анонсировали (чтобы не дублировать из event). */
    private final Set<UUID> silenceVanilla = ConcurrentHashMap.newKeySet();

    public SystemMessageListener(ChatSync plugin) {
        this.plugin = plugin;
    }

    private boolean staffOnly() {
        return plugin.getConfig().getBoolean("system_messages.staff_only", true);
    }

    private String seePerm() {
        return plugin.getConfig().getString("system_messages.see_permission", "chatsync.system.see");
    }

    private boolean canSee(CommandSender s) {
        if (!staffOnly()) return true;
        if (!(s instanceof Player)) return true; // console always
        return s.hasPermission(seePerm()) || s.hasPermission("chatsync.admin") || s.isOp();
    }

    // ── Intercept commands → no vanilla feedback ─────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("system_messages.intercept_commands", true)) return;
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String body = raw.substring(1).trim();
        String[] parts = body.split("\\s+");
        if (parts.length == 0) return;
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        // strip plugin prefix: minecraft:gamemode
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);

        Player sender = event.getPlayer();

        if (plugin.getConfig().getBoolean("system_messages.gamemode.enabled", true)
                && (cmd.equals("gamemode") || cmd.equals("gm"))) {
            if (handleGamemodeCommand(sender, parts)) {
                event.setCancelled(true);
            }
            return;
        }

        if (plugin.getConfig().getBoolean("system_messages.teleport.enabled", true)
                && (cmd.equals("tp") || cmd.equals("teleport") || cmd.equals("tppos"))) {
            if (handleTeleportCommand(sender, parts)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!plugin.getConfig().getBoolean("system_messages.intercept_commands", true)) return;
        String body = event.getCommand();
        if (body == null) return;
        body = body.trim();
        String[] parts = body.split("\\s+");
        if (parts.length == 0) return;
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);

        if (plugin.getConfig().getBoolean("system_messages.gamemode.enabled", true)
                && (cmd.equals("gamemode") || cmd.equals("gm"))) {
            if (handleGamemodeCommand(event.getSender(), parts)) {
                event.setCancelled(true);
            }
            return;
        }
        if (plugin.getConfig().getBoolean("system_messages.teleport.enabled", true)
                && (cmd.equals("tp") || cmd.equals("teleport") || cmd.equals("tppos"))) {
            if (handleTeleportCommand(event.getSender(), parts)) {
                event.setCancelled(true);
            }
        }
    }

    /** @return true if handled (cancel vanilla) */
    private boolean handleGamemodeCommand(CommandSender sender, String[] parts) {
        // /gm <mode> [player]  or /gamemode <mode> [player]
        if (parts.length < 2) return false;
        GameMode mode = parseGameMode(parts[1]);
        if (mode == null) return false;

        Player target;
        if (parts.length >= 3) {
            target = Bukkit.getPlayerExact(parts[2]);
            if (target == null) target = Bukkit.getPlayer(parts[2]);
            if (target == null) return false; // let vanilla error
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            return false; // console needs target
        }

        // permission check roughly: if player can't change, don't intercept
        if (sender instanceof Player p && !p.hasPermission("minecraft.command.gamemode")
                && !p.hasPermission("bukkit.command.gamemode")
                && !p.isOp()) {
            // still allow if they have essentials etc — if no perm leave to vanilla
            if (!p.hasPermission("essentials.gamemode") && !p.hasPermission("essentials.gamemode.all")) {
                return false;
            }
        }

        final UUID targetId = target.getUniqueId();
        silenceVanilla.add(targetId);
        target.setGameMode(mode);
        Bukkit.getScheduler().runTaskLater(plugin, () -> silenceVanilla.remove(targetId), 5L);

        announceGamemode(target, mode);
        // quiet confirm to executor if different
        if (sender instanceof Player sp && !sp.getUniqueId().equals(target.getUniqueId()) && canSee(sender)) {
            sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&7GM &f" + target.getName() + " &8→ &f" + mode.name().toLowerCase()));
        } else if (!(sender instanceof Player)) {
            plugin.logToConsolePublic("[GM] " + target.getName() + " → " + mode.name().toLowerCase());
        }
        return true;
    }

    private boolean handleTeleportCommand(CommandSender sender, String[] parts) {
        // Common forms:
        // /tp <player>                     — self to player
        // /tp <x> <y> <z>                  — self to coords
        // /tp <player> <x> <y> <z>
        // /tp <player> <player>
        // /tp <player> <x> <y> <z> <yaw> <pitch>
        if (parts.length < 2) return false;

        Player who;
        Location dest = null;

        if (isCoord(parts[1])) {
            // /tp x y z [yaw pitch]
            if (!(sender instanceof Player self)) return false;
            if (parts.length < 4) return false;
            who = self;
            dest = parseLocation(self.getLocation(), parts, 1);
        } else {
            Player first = Bukkit.getPlayerExact(parts[1]);
            if (first == null) first = Bukkit.getPlayer(parts[1]);
            if (first == null) return false;

            if (parts.length == 2) {
                // /tp <player> — sender to player
                if (!(sender instanceof Player self)) return false;
                who = self;
                dest = first.getLocation().clone();
            } else if (parts.length >= 4 && isCoord(parts[2])) {
                // /tp <player> x y z
                who = first;
                dest = parseLocation(first.getLocation(), parts, 2);
            } else {
                // /tp <player> <player>
                Player second = Bukkit.getPlayerExact(parts[2]);
                if (second == null) second = Bukkit.getPlayer(parts[2]);
                if (second == null) return false;
                who = first;
                dest = second.getLocation().clone();
            }
        }
        if (who == null || dest == null) return false;

        final UUID whoId = who.getUniqueId();
        final Player whoFinal = who;
        final Location finalDest = dest;
        silenceVanilla.add(whoId);
        whoFinal.teleport(finalDest);
        Bukkit.getScheduler().runTaskLater(plugin, () -> silenceVanilla.remove(whoId), 5L);

        announceTeleport(whoFinal, finalDest);
        return true;
    }

    private Location parseLocation(Location base, String[] parts, int start) {
        try {
            double x = parseCoord(parts[start], base.getX());
            double y = parseCoord(parts[start + 1], base.getY());
            double z = parseCoord(parts[start + 2], base.getZ());
            Location loc = new Location(base.getWorld(), x, y, z, base.getYaw(), base.getPitch());
            if (parts.length > start + 4) {
                loc.setYaw(Float.parseFloat(parts[start + 3]));
                loc.setPitch(Float.parseFloat(parts[start + 4]));
            } else if (parts.length > start + 3) {
                loc.setYaw(Float.parseFloat(parts[start + 3]));
            }
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    private double parseCoord(String s, double relativeBase) {
        if (s.startsWith("~")) {
            if (s.length() == 1) return relativeBase;
            return relativeBase + Double.parseDouble(s.substring(1));
        }
        return Double.parseDouble(s);
    }

    private boolean isCoord(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.startsWith("~")) return true;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private GameMode parseGameMode(String s) {
        if (s == null) return null;
        s = s.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> {
                try {
                    yield GameMode.valueOf(s.toUpperCase(Locale.ROOT));
                } catch (Exception e) {
                    yield null;
                }
            }
        };
    }

    // ── Events (for non-command changes, e.g. plugins) ───────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (!plugin.getConfig().getBoolean("system_messages.gamemode.enabled", true)) return;
        if (silenceVanilla.contains(event.getPlayer().getUniqueId())) return; // already announced
        // Only announce plugin/code changes if configured
        if (!plugin.getConfig().getBoolean("system_messages.gamemode.announce_non_command", false)) return;
        announceGamemode(event.getPlayer(), event.getNewGameMode());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("system_messages.teleport.enabled", true)) return;
        if (silenceVanilla.contains(event.getPlayer().getUniqueId())) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        // Only COMMAND/PLUGIN when not already handled by intercept
        if (cause != PlayerTeleportEvent.TeleportCause.COMMAND
                && cause != PlayerTeleportEvent.TeleportCause.PLUGIN
                && cause != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }
        if (event.getTo() == null) return;
        // skip same-block
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != null && to.getWorld() != null
                && from.getWorld().equals(to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("system_messages.teleport.announce_non_command", false)) return;
        announceTeleport(event.getPlayer(), to);
    }

    private void announceGamemode(Player player, GameMode mode) {
        String key = "system_messages.gamemode.format";
        if (mode == GameMode.CREATIVE) key = "system_messages.gamemode.format_creative";
        else if (mode == GameMode.SURVIVAL) key = "system_messages.gamemode.format_survival";
        else if (mode == GameMode.ADVENTURE) key = "system_messages.gamemode.format_adventure";
        else if (mode == GameMode.SPECTATOR) key = "system_messages.gamemode.format_spectator";
        String tpl = plugin.getConfig().getString(key,
                plugin.getConfig().getString("system_messages.gamemode.format",
                        "%head%&7%player% &8→ &f%mode%"));
        if (tpl == null || tpl.isEmpty()) return;
        String modeName = mode.name().toLowerCase(Locale.ROOT);
        String filled = tpl.replace("%mode%", modeName).replace("%gamemode%", modeName);
        broadcastStaff("[GM] ", filled, player);
    }

    private void announceTeleport(Player player, Location to) {
        String tpl = plugin.getConfig().getString("system_messages.teleport.format",
                "%head%&7%player% &8→ &f%world% &7%x%&8, &7%y%&8, &7%z%");
        if (tpl == null || tpl.isEmpty()) return;
        String world = to.getWorld() != null ? to.getWorld().getName() : "?";
        String filled = tpl
                .replace("%world%", world)
                .replace("%to%", world)
                .replace("%from%", player.getWorld() != null ? player.getWorld().getName() : "?")
                .replace("%x%", formatCoord(to.getX()))
                .replace("%y%", formatCoord(to.getY()))
                .replace("%z%", formatCoord(to.getZ()));
        broadcastStaff("[TP] ", filled, player);
    }

    private String formatCoord(double v) {
        if (Math.rint(v) == v) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private void broadcastStaff(String consolePrefix, String template, Player player) {
        Component withHead = plugin.buildNameComponentPublic(template, player);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (canSee(p)) p.sendMessage(withHead);
        }
        String plain = plugin.plainComponent(withHead);
        if (!plain.isEmpty()) {
            plugin.logToConsolePublic(consolePrefix + plain);
        }
    }
}
