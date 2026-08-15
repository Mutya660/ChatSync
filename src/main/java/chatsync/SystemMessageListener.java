package chatsync;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Кастомные сообщения GM / TP с головами; консоль — plain с префиксом.
 */
public class SystemMessageListener implements Listener {

    private final ChatSync plugin;

    public SystemMessageListener(ChatSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (!plugin.getConfig().getBoolean("system_messages.gamemode.enabled", true)) return;
        Player player = event.getPlayer();
        GameMode mode = event.getNewGameMode();
        String key = "system_messages.gamemode.format";
        if (mode == GameMode.CREATIVE) key = "system_messages.gamemode.format_creative";
        else if (mode == GameMode.SURVIVAL) key = "system_messages.gamemode.format_survival";
        else if (mode == GameMode.ADVENTURE) key = "system_messages.gamemode.format_adventure";
        else if (mode == GameMode.SPECTATOR) key = "system_messages.gamemode.format_spectator";
        String tpl = plugin.getConfig().getString(key,
                plugin.getConfig().getString("system_messages.gamemode.format",
                        "%head%&7%player% &8→ &f%mode%"));
        if (tpl == null || tpl.isEmpty()) return;
        String modeName = mode.name().toLowerCase();
        String filled = tpl.replace("%mode%", modeName).replace("%gamemode%", modeName);
        broadcastSystem("[GM] ", filled, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("system_messages.teleport.enabled", true)) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        java.util.List<String> causes = plugin.getConfig().getStringList("system_messages.teleport.causes");
        if (causes != null && !causes.isEmpty()) {
            boolean ok = false;
            for (String c : causes) {
                if (c != null && c.equalsIgnoreCase(cause.name())) { ok = true; break; }
            }
            if (!ok) return;
        } else {
            switch (cause) {
                case COMMAND:
                case PLUGIN:
                case SPECTATE:
                case UNKNOWN:
                    break;
                default:
                    // portals / pearls — optional via config; default skip noisy ones
                    if (!plugin.getConfig().getBoolean("system_messages.teleport.include_portals", false)) {
                        return;
                    }
                    switch (cause) {
                        case END_PORTAL:
                        case NETHER_PORTAL:
                        case ENDER_PEARL:
                        case CHORUS_FRUIT:
                            break;
                        default:
                            return;
                    }
            }
        }
        if (event.getTo() == null || event.getFrom() == null) return;
        boolean crossWorld = event.getFrom().getWorld() != null
                && event.getTo().getWorld() != null
                && !event.getFrom().getWorld().equals(event.getTo().getWorld());
        if (!crossWorld && plugin.getConfig().getBoolean("system_messages.teleport.cross_world_only", false)) {
            return;
        }
        // skip tiny same-chunk teleports (same block)
        if (!crossWorld
                && event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        String tpl = plugin.getConfig().getString("system_messages.teleport.format",
                "%head%&7%player% &8→ &f%to%");
        if (tpl == null || tpl.isEmpty()) return;
        String fromW = event.getFrom().getWorld() != null ? event.getFrom().getWorld().getName() : "?";
        String toW = event.getTo().getWorld() != null ? event.getTo().getWorld().getName() : "?";
        String filled = tpl
                .replace("%from%", fromW)
                .replace("%to%", toW)
                .replace("%cause%", cause.name().toLowerCase());
        broadcastSystem("[TP] ", filled, player);
    }

    private void broadcastSystem(String consolePrefix, String template, Player player) {
        Component withHead = plugin.buildNameComponentPublic(template, player);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(withHead);
        }
        String plain = plugin.plainComponent(withHead);
        if (!plain.isEmpty()) {
            plugin.logToConsolePublic(consolePrefix + plain);
        }
    }
}
