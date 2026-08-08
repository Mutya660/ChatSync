package chatsync;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for ChatSync.
 *
 * Playtime:
 *   %chatsync_playtime%  %chatsync_playtime_seconds%
 * Messages:
 *   %chatsync_messages_total%  %chatsync_messages_global%  %chatsync_messages_local%
 *   %chatsync_messages_pm%  %chatsync_messages_me%  %chatsync_messages_broadcast%
 * Team:
 *   %chatsync_team%  %chatsync_team_name%  %chatsync_team_color%
 *   %chatsync_team_owner%  %chatsync_team_size%  %chatsync_team_members%
 *   %chatsync_in_team%  %chatsync_team_is_owner%  %chatsync_team_is_leader%
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

        // Team
        TeamManager tm = plugin.getTeamManager();
        if (tm != null && (key.startsWith("team") || key.equals("in_team"))) {
            TeamManager.Team team = tm.getTeamOf(player.getUniqueId());
            return switch (key) {
                case "in_team" -> team != null ? "yes" : "no";
                case "team", "team_name" -> team != null ? team.name : "";
                case "team_color" -> team != null && team.color != null ? team.color : "";
                case "team_owner" -> {
                    if (team == null) yield "";
                    OfflinePlayer op = Bukkit.getOfflinePlayer(team.owner);
                    yield op.getName() != null ? op.getName() : "";
                }
                case "team_size" -> team != null ? String.valueOf(team.members.size()) : "0";
                case "team_members" -> {
                    if (team == null) yield "";
                    StringBuilder sb = new StringBuilder();
                    for (java.util.UUID id : team.members) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                        String n = op.getName() != null ? op.getName() : "?";
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(n);
                    }
                    yield sb.toString();
                }
                case "team_is_owner" -> team != null && team.isOwner(player.getUniqueId()) ? "yes" : "no";
                case "team_is_leader" -> team != null && team.isLeader(player.getUniqueId()) ? "yes" : "no";
                default -> null;
            };
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
