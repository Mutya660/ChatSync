package chatsync;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Кастомные сообщения о смене режима и телепортации с головами игроков.
 * В консоль — только plain (без ObjectComponent).
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
        broadcastSystem(filled, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("system_messages.teleport.enabled", false)) return;
        // Не спамим каждый мелкий tp — только cross-world или если cause в whitelist
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        java.util.List<String> causes = plugin.getConfig().getStringList("system_messages.teleport.causes");
        if (causes != null && !causes.isEmpty()) {
            boolean ok = false;
            for (String c : causes) {
                if (c != null && c.equalsIgnoreCase(cause.name())) { ok = true; break; }
            }
            if (!ok) return;
        } else {
            // default: only COMMAND / PLUGIN / END_PORTAL / NETHER_PORTAL / SPECTATE
            switch (cause) {
                case COMMAND, PLUGIN, END_PORTAL, NETHER_PORTAL, SPECTATE, ENDER_PEARL -> {}
                default -> { return; }
            }
        }
        if (event.getTo() == null || event.getFrom() == null) return;
        boolean crossWorld = event.getFrom().getWorld() != null
                && event.getTo().getWorld() != null
                && !event.getFrom().getWorld().equals(event.getTo().getWorld());
        if (!crossWorld && plugin.getConfig().getBoolean("system_messages.teleport.cross_world_only", true)) {
            return;
        }
        Player player = event.getPlayer();
        String tpl = plugin.getConfig().getString("system_messages.teleport.format",
                "%head%&7%player% &8телепортировался");
        if (tpl == null || tpl.isEmpty()) return;
        String fromW = event.getFrom().getWorld() != null ? event.getFrom().getWorld().getName() : "?";
        String toW = event.getTo().getWorld() != null ? event.getTo().getWorld().getName() : "?";
        String filled = tpl
                .replace("%from%", fromW)
                .replace("%to%", toW)
                .replace("%cause%", cause.name().toLowerCase());
        broadcastSystem(filled, player);
    }

    private void broadcastSystem(String template, Player player) {
        // buildNameComponent already adds head when force_first / %head%
        Component withHead = plugin.buildNameComponentPublic(template, player);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(withHead);
        }
        // консоль без ObjectComponent
        String plain = plugin.plainComponent(withHead);
        if (!plain.isEmpty()) {
            Bukkit.getConsoleSender().sendMessage(
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                            .deserialize(plain));
        }
    }
}
