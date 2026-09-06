package chatsync;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Paper / Purpur (and Arclight builds that expose AsyncChatEvent).
 * Registered only when io.papermc.paper.event.player.AsyncChatEvent is present.
 */
public final class PaperChatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final ChatSync plugin;

    public PaperChatListener(ChatSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);
        String raw = LEGACY.serialize(event.message());
        plugin.processChatMessage(event.getPlayer(), raw);
    }
}
