package chatsync;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Spigot / Arclight fallback when Paper AsyncChatEvent is unavailable.
 * AsyncPlayerChatEvent is deprecated on Paper but remains the portable Bukkit API.
 */
@SuppressWarnings("deprecation")
public final class LegacyChatListener implements Listener {

    private final ChatSync plugin;

    public LegacyChatListener(ChatSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        event.setCancelled(true);
        plugin.processChatMessage(event.getPlayer(), event.getMessage());
    }
}
