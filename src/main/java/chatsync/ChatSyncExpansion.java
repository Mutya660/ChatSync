package chatsync;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for ChatSync.
 * Placeholders (use in TAB / scoreboard / holograms):
 *   %chatsync_playtime%          — formatted playtime of the player
 *   %chatsync_playtime_seconds%  — raw seconds
 *   %chatsync_messages_total%    — total chat messages (global+local+pm+me)
 *   %chatsync_messages_global%   — global messages
 *   %chatsync_messages_local%    — local messages
 *   %chatsync_messages_pm%       — private messages
 *   %chatsync_messages_me%       — /me messages
 *   %chatsync_messages_broadcast% — broadcast announcements
 */
public class ChatSyncExpansion extends PlaceholderExpansion {

    private final ChatSync plugin;

    public ChatSyncExpansion(ChatSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "chatsync";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Mutya660";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        String key = params.toLowerCase();

        // Playtime
        if (key.equals("playtime") || key.equals("playtime_formatted")) {
            if (plugin.getPlaytimeManager() == null) return "0";
            long sec = plugin.getPlaytimeManager().getPlaytimeSeconds(player.getUniqueId());
            return formatDuration(sec);
        }
        if (key.equals("playtime_seconds")) {
            if (plugin.getPlaytimeManager() == null) return "0";
            return String.valueOf(plugin.getPlaytimeManager().getPlaytimeSeconds(player.getUniqueId()));
        }

        // Chat stats
        ChatStatsManager statsMgr = plugin.getStatsManager();
        if (statsMgr == null) return "0";

        ChatStatsManager.PlayerStats s = statsMgr.get(player.getUniqueId());
        if (s == null) {
            if (key.startsWith("messages_")) return "0";
            return "0";
        }

        return switch (key) {
            case "messages_total", "messages" -> String.valueOf(s.total());
            case "messages_global", "global" -> String.valueOf(s.global);
            case "messages_local", "local" -> String.valueOf(s.local);
            case "messages_pm", "pm" -> String.valueOf(s.pm);
            case "messages_me", "me" -> String.valueOf(s.me);
            case "messages_broadcast", "broadcast" -> String.valueOf(s.broadcast);
            default -> null;
        };
    }

    private static String formatDuration(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long days = totalSeconds / 86400;
        long hoursTotal = totalSeconds / 3600;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (totalSeconds < 60) return seconds + "s";
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hoursTotal > 0) return hoursTotal + "h " + minutes + "m";
        return minutes + "m";
    }
}
