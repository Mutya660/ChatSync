package chatsync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hide join/quit messages for vanished players.
 * Soft-detects SuperVanish, PremiumVanish, EssentialsX and common metadata keys.
 * (LiteBans itself has no vanish — this is the vanish side of soft-depends.)
 */
public class VanishHook {

    private final JavaPlugin plugin;
    private final boolean superVanish;
    private final boolean premiumVanish;
    private final boolean essentials;

    public VanishHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.superVanish = Bukkit.getPluginManager().getPlugin("SuperVanish") != null
                || Bukkit.getPluginManager().getPlugin("PremiumVanish") != null;
        this.premiumVanish = Bukkit.getPluginManager().getPlugin("PremiumVanish") != null;
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        this.essentials = ess != null && ess.isEnabled();
        if (superVanish || essentials) {
            plugin.getLogger().info("Vanish detected: join/quit messages hidden for vanished players.");
        }
    }

    public boolean isVanished(Player player) {
        if (player == null) return false;
        // Common metadata (SuperVanish / PremiumVanish / others)
        for (String key : new String[]{"vanished", "VanishAPI", "vanished_player"}) {
            if (player.hasMetadata(key)) {
                for (MetadataValue mv : player.getMetadata(key)) {
                    try {
                        if (mv.asBoolean()) return true;
                    } catch (Exception ignored) {}
                }
            }
        }
        // SuperVanish / PremiumVanish API via reflection
        if (superVanish || premiumVanish) {
            try {
                Class<?> api = Class.forName("de.myzelyam.api.vanish.VanishAPI");
                Object r = api.getMethod("isInvisible", Player.class).invoke(null, player);
                if (r instanceof Boolean && (Boolean) r) return true;
            } catch (Throwable ignored) {}
        }
        // EssentialsX
        if (essentials) {
            try {
                Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
                Object user = ess.getClass().getMethod("getUser", Player.class).invoke(ess, player);
                if (user != null) {
                    Object r = user.getClass().getMethod("isVanished").invoke(user);
                    if (r instanceof Boolean && (Boolean) r) return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public boolean shouldHideJoinQuit(Player player) {
        if (!plugin.getConfig().getBoolean("vanish.hide_join_quit", true)) return false;
        return isVanished(player);
    }
}
