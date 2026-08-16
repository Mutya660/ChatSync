package chatsync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кастомные GM/TP для staff (или всех, если staff_only=false).
 * Перехват /gamemode /gm /tp /teleport — без ванильного feedback.
 */
public class SystemMessageListener implements Listener {

    private final ChatSync plugin;
    /** Уже обработали GM/TP сами — не дублировать из event. */
    private final Set<UUID> silenceVanilla = ConcurrentHashMap.newKeySet();
    /** Анти-дубль анонсов (uuid → expiry ms). */
    private final Map<UUID, Long> recentAnnounce = new ConcurrentHashMap<>();

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

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
        if (!(s instanceof Player)) return true;
        return s.hasPermission(seePerm()) || s.hasPermission("chatsync.admin") || s.isOp();
    }

    private boolean interceptEnabled() {
        return plugin.getConfig().getBoolean("system_messages.intercept_commands", true);
    }

    // ── Command intercept ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!interceptEnabled()) return;
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String body = raw.substring(1).trim();
        String[] parts = body.split("\\s+");
        if (parts.length == 0) return;
        String cmd = stripNamespace(parts[0].toLowerCase(Locale.ROOT));

        Player sender = event.getPlayer();

        if (plugin.getConfig().getBoolean("system_messages.gamemode.enabled", true)
                && isGamemodeCmd(cmd)) {
            if (handleGamemodeCommand(sender, parts)) {
                event.setCancelled(true);
            }
            return;
        }

        if (plugin.getConfig().getBoolean("system_messages.teleport.enabled", true)
                && isTeleportCmd(cmd)) {
            if (handleTeleportCommand(sender, parts)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!interceptEnabled()) return;
        String body = event.getCommand();
        if (body == null) return;
        body = body.trim();
        String[] parts = body.split("\\s+");
        if (parts.length == 0) return;
        String cmd = stripNamespace(parts[0].toLowerCase(Locale.ROOT));

        if (plugin.getConfig().getBoolean("system_messages.gamemode.enabled", true)
                && isGamemodeCmd(cmd)) {
            if (handleGamemodeCommand(event.getSender(), parts)) {
                event.setCancelled(true);
            }
            return;
        }
        if (plugin.getConfig().getBoolean("system_messages.teleport.enabled", true)
                && isTeleportCmd(cmd)) {
            if (handleTeleportCommand(event.getSender(), parts)) {
                event.setCancelled(true);
            }
        }
    }

    private static boolean isGamemodeCmd(String cmd) {
        return cmd.equals("gamemode") || cmd.equals("gm") || cmd.equals("gmc")
                || cmd.equals("gms") || cmd.equals("gma") || cmd.equals("gmsp");
    }

    private static boolean isTeleportCmd(String cmd) {
        return cmd.equals("tp") || cmd.equals("teleport") || cmd.equals("tppos")
                || cmd.equals("tele") || cmd.equals("tpo");
    }

    private static String stripNamespace(String cmd) {
        int colon = cmd.indexOf(':');
        return colon >= 0 ? cmd.substring(colon + 1) : cmd;
    }

    /** @return true if handled (cancel vanilla) */
    private boolean handleGamemodeCommand(CommandSender sender, String[] parts) {
        GameMode mode;
        Player target;
        String cmd = stripNamespace(parts[0].toLowerCase(Locale.ROOT));

        // /gmc /gms /gma /gmsp [player]
        if (cmd.equals("gmc") || cmd.equals("gms") || cmd.equals("gma") || cmd.equals("gmsp")) {
            mode = switch (cmd) {
                case "gmc" -> GameMode.CREATIVE;
                case "gms" -> GameMode.SURVIVAL;
                case "gma" -> GameMode.ADVENTURE;
                default -> GameMode.SPECTATOR;
            };
            if (parts.length >= 2) {
                target = findPlayer(parts[1]);
                if (target == null) return false;
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                return false;
            }
        } else {
            // /gm <mode> [player]  or /gamemode <mode> [player]
            // also /gamemode <player> <mode> (vanilla order sometimes)
            if (parts.length < 2) return false;

            mode = parseGameMode(parts[1]);
            if (mode != null) {
                if (parts.length >= 3) {
                    target = findPlayer(parts[2]);
                    if (target == null) return false;
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    return false;
                }
            } else {
                // /gamemode <player> <mode>
                target = findPlayer(parts[1]);
                if (target == null || parts.length < 3) return false;
                mode = parseGameMode(parts[2]);
                if (mode == null) return false;
            }
        }

        // Не блокируем из‑за прав слишком жёстко: если игрок смог набрать команду —
        // пусть vanilla обработает при отказе setGameMode. Перехватываем всегда при валидном mode.
        final UUID targetId = target.getUniqueId();
        silenceVanilla.add(targetId);
        GameMode before = target.getGameMode();
        target.setGameMode(mode);
        Bukkit.getScheduler().runTaskLater(plugin, () -> silenceVanilla.remove(targetId), 10L);

        if (before != mode) {
            announceGamemode(target, mode);
        }

        if (sender instanceof Player sp && !sp.getUniqueId().equals(targetId) && canSee(sender)) {
            sender.sendMessage(LEGACY.deserialize("&7GM &f" + target.getName() + " &8→ &f" + mode.name().toLowerCase(Locale.ROOT)));
        } else if (!(sender instanceof Player)) {
            plugin.logToConsolePublic("[GM] " + target.getName() + " → " + mode.name().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    private boolean handleTeleportCommand(CommandSender sender, String[] parts) {
        // /tp <player>                     — self to player
        // /tp <x> <y> <z>                  — self to coords
        // /tp <player> <x> <y> <z>
        // /tp <player> <player>
        // /tp <player> <x> <y> <z> <yaw> <pitch>
        if (parts.length < 2) return false;

        Player who;
        Location dest;
        Player destPlayer = null; // если телепорт к игроку

        if (isCoord(parts[1])) {
            if (!(sender instanceof Player self)) return false;
            if (parts.length < 4) return false;
            who = self;
            dest = parseLocation(self.getLocation(), parts, 1);
        } else {
            Player first = findPlayer(parts[1]);
            if (first == null) return false;

            if (parts.length == 2) {
                if (!(sender instanceof Player self)) return false;
                who = self;
                dest = first.getLocation().clone();
                destPlayer = first;
            } else if (parts.length >= 4 && isCoord(parts[2])) {
                who = first;
                dest = parseLocation(first.getLocation(), parts, 2);
            } else {
                Player second = findPlayer(parts[2]);
                if (second == null) return false;
                who = first;
                dest = second.getLocation().clone();
                destPlayer = second;
            }
        }
        if (who == null || dest == null) return false;

        final UUID whoId = who.getUniqueId();
        final Player whoFinal = who;
        final Location finalDest = dest;
        final Player finalDestPlayer = destPlayer;
        silenceVanilla.add(whoId);
        whoFinal.teleport(finalDest);
        Bukkit.getScheduler().runTaskLater(plugin, () -> silenceVanilla.remove(whoId), 10L);

        announceTeleport(whoFinal, finalDest, finalDestPlayer);
        return true;
    }

    private Player findPlayer(String name) {
        if (name == null || name.isEmpty()) return null;
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        return Bukkit.getPlayer(name);
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

    // ── Events (fallback, если команду не перехватили) ───────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (!plugin.getConfig().getBoolean("system_messages.gamemode.enabled", true)) return;
        Player player = event.getPlayer();
        if (silenceVanilla.contains(player.getUniqueId())) return;

        // Анонсируем и command, и plugin-изменения (чтобы не остался только vanilla feedback)
        // Если команду перехватили — сюда не зайдём из‑за silenceVanilla
        announceGamemode(player, event.getNewGameMode());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("system_messages.teleport.enabled", true)) return;
        if (silenceVanilla.contains(event.getPlayer().getUniqueId())) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.COMMAND
                && cause != PlayerTeleportEvent.TeleportCause.PLUGIN
                && cause != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }
        if (event.getTo() == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != null && to.getWorld() != null
                && from.getWorld().equals(to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        // если не перехватили команду — всё равно анонс (ищем ближайшего игрока на точке)
        Player destPlayer = findPlayerNear(to, event.getPlayer());
        announceTeleport(event.getPlayer(), to, destPlayer);
    }

    /** Игрок, к которому тепнулись (на тех же координатах, не сам who). */
    private Player findPlayerNear(Location to, Player exclude) {
        if (to.getWorld() == null) return null;
        for (Player p : to.getWorld().getPlayers()) {
            if (p.getUniqueId().equals(exclude.getUniqueId())) continue;
            Location pl = p.getLocation();
            if (pl.getWorld() != null && pl.getWorld().equals(to.getWorld())
                    && Math.abs(pl.getX() - to.getX()) < 1.5
                    && Math.abs(pl.getY() - to.getY()) < 2.0
                    && Math.abs(pl.getZ() - to.getZ()) < 1.5) {
                return p;
            }
        }
        return null;
    }

    // ── Announce ─────────────────────────────────────────────────

    private boolean shouldSkipDuplicate(UUID id) {
        long now = System.currentTimeMillis();
        Long exp = recentAnnounce.get(id);
        if (exp != null && exp > now) return true;
        recentAnnounce.put(id, now + 500L);
        return false;
    }

    private void announceGamemode(Player player, GameMode mode) {
        if (shouldSkipDuplicate(player.getUniqueId())) return;

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
        broadcastStaff("[GM] ", filled, player, null, null);
    }

    private void announceTeleport(Player player, Location to, Player destPlayer) {
        if (shouldSkipDuplicate(player.getUniqueId())) return;

        String world = to.getWorld() != null ? to.getWorld().getName() : "?";
        String x = formatCoord(to.getX());
        String y = formatCoord(to.getY());
        String z = formatCoord(to.getZ());

        String tpl;
        if (destPlayer != null) {
            tpl = plugin.getConfig().getString("system_messages.teleport.format_to_player",
                    "%head%&7%player% &8→ &f%target% &8(&7%world% %x%&8, &7%y%&8, &7%z%&8)");
        } else {
            tpl = plugin.getConfig().getString("system_messages.teleport.format",
                    "%head%&7%player% &8→ &f%world% &7%x%&8, &7%y%&8, &7%z%");
        }
        if (tpl == null || tpl.isEmpty()) return;

        String filled = tpl
                .replace("%world%", world)
                .replace("%to%", world)
                .replace("%from%", player.getWorld() != null ? player.getWorld().getName() : "?")
                .replace("%x%", x)
                .replace("%y%", y)
                .replace("%z%", z)
                .replace("%target%", destPlayer != null ? destPlayer.getName() : "")
                .replace("%target_player%", destPlayer != null ? destPlayer.getName() : "");

        broadcastStaff("[TP] ", filled, player, destPlayer, to);
    }

    private String formatCoord(double v) {
        if (Math.rint(v) == v) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /**
     * Рассылка staff. %player% — кто телепортировался/сменил GM.
     * %target% — кликабельный ник (ТП к нему по клику).
     */
    private void broadcastStaff(String consolePrefix, String template, Player player,
                                Player clickTarget, Location clickLoc) {
        Component msg = buildMessage(template, player, clickTarget, clickLoc);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (canSee(p)) p.sendMessage(msg);
        }
        String plain = plugin.plainComponent(msg);
        if (!plain.isEmpty()) {
            plugin.logToConsolePublic(consolePrefix + plain);
        }
    }

    private Component buildMessage(String template, Player actor, Player clickTarget, Location clickLoc) {
        // Собираем через buildNameComponentPublic для %player% / %head%, потом подменяем %target%
        String withTargetPh = template;
        final String TARGET_PH = "%TARGET_CLICK%";
        boolean hasTarget = clickTarget != null && (template.contains("%target%") || template.contains("%target_player%"));
        if (hasTarget) {
            withTargetPh = template
                    .replace("%target_player%", TARGET_PH)
                    .replace("%target%", TARGET_PH);
        }

        Component base = plugin.buildNameComponentPublic(withTargetPh, actor);

        if (!hasTarget) {
            return base;
        }

        // Кликабельный ник цели: /tp <name>
        String hover = plugin.getConfig().getString("system_messages.teleport.click_hover",
                "&7Клик: телепорт к &f%player%");
        hover = hover.replace("%player%", clickTarget.getName());
        Component targetComp = LEGACY.deserialize("&f" + clickTarget.getName())
                .clickEvent(ClickEvent.runCommand("/tp " + clickTarget.getName()))
                .hoverEvent(HoverEvent.showText(LEGACY.deserialize(hover)));

        // Если base уже plain text с плейсхолдером — проще пересобрать вручную
        String plainTpl = withTargetPh
                .replace("%player%", actor.getName())
                .replace("%head%", "");
        int idx = plainTpl.indexOf(TARGET_PH);
        if (idx < 0) {
            // buildNameComponent мог не оставить плейсхолдер — допишем " → target"
            return Component.text()
                    .append(base)
                    .append(LEGACY.deserialize(" &8→ "))
                    .append(targetComp)
                    .build();
        }

        // Разбор шаблона вокруг TARGET_PH без %player%/%head% (их уже в base сложно резать)
        // Надёжнее: собрать сообщение заново из legacy + клик
        return rebuildWithClickableTarget(template, actor, clickTarget, clickLoc, targetComp);
    }

    private Component rebuildWithClickableTarget(String template, Player actor, Player target,
                                                 Location to, Component targetComp) {
        String world = to != null && to.getWorld() != null ? to.getWorld().getName() : "?";
        String x = to != null ? formatCoord(to.getX()) : "?";
        String y = to != null ? formatCoord(to.getY()) : "?";
        String z = to != null ? formatCoord(to.getZ()) : "?";

        String tpl = template
                .replace("%world%", world)
                .replace("%to%", world)
                .replace("%from%", actor.getWorld() != null ? actor.getWorld().getName() : "?")
                .replace("%x%", x)
                .replace("%y%", y)
                .replace("%z%", z)
                .replace("%mode%", "")
                .replace("%gamemode%", "");

        // Разбиваем по %target% / %target_player% / %player% / %head%
        net.kyori.adventure.text.TextComponent.Builder out = Component.text();
        String rest = tpl;
        while (!rest.isEmpty()) {
            int h = rest.indexOf("%head%");
            int p = rest.indexOf("%player%");
            int t1 = rest.indexOf("%target%");
            int t2 = rest.indexOf("%target_player%");
            int t = minPos(t1, t2);

            int next = minPos(h, minPos(p, t));
            if (next < 0) {
                out.append(LEGACY.deserialize(rest));
                break;
            }
            if (next > 0) {
                out.append(LEGACY.deserialize(rest.substring(0, next)));
            }
            if (next == h) {
                try {
                    out.append(plugin.buildHeadComponent(actor));
                } catch (Throwable ignored) {}
                rest = rest.substring(next + 6);
            } else if (next == p) {
                out.append(plugin.buildNameComponentPublic("%player%", actor));
                rest = rest.substring(next + 8);
            } else {
                // target
                out.append(targetComp);
                if (next == t1) rest = rest.substring(next + 8);
                else rest = rest.substring(next + 15);
            }
        }
        return out.build();
    }

    private static int minPos(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }
}
