package chatsync;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Необязательная прямая интеграция с LuckPerms API.
 * Используется как резервный вариант, если PlaceholderAPI не установлен,
 * но LuckPerms есть — тогда %luckperms_prefix% / %luckperms_suffix%
 * всё равно будут работать в форматах чата.
 *
 * Optional direct LuckPerms API integration.
 * Serves as a fallback when PlaceholderAPI is not installed but LuckPerms
 * is present, so %luckperms_prefix% / %luckperms_suffix% still resolve in chat formats.
 */
public class LuckPermsHook {

    private final boolean available;
    private LuckPerms api;

    public LuckPermsHook(JavaPlugin plugin) {
        boolean found = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
        LuckPerms tmp = null;
        if (found) {
            try {
                tmp = LuckPermsProvider.get();
            } catch (IllegalStateException e) {
                found = false;
            }
        }
        this.available = found;
        this.api = tmp;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getPrefix(Player player) {
        if (!available) return "";
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        CachedMetaData meta = user.getCachedData().getMetaData();
        String prefix = meta.getPrefix();
        return prefix != null ? prefix : "";
    }

    public String getSuffix(Player player) {
        if (!available) return "";
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        CachedMetaData meta = user.getCachedData().getMetaData();
        String suffix = meta.getSuffix();
        return suffix != null ? suffix : "";
    }

    /**
     * Color for the player name from LuckPerms:
     * 1) meta key "username-color" or "namecolor" (e.g. &c or &#FF5555)
     * 2) otherwise last color code found in the prefix
     */
    public String getNameColor(Player player) {
        if (!available) return "";
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        CachedMetaData meta = user.getCachedData().getMetaData();
        String metaColor = meta.getMetaValue("username-color");
        if (metaColor == null || metaColor.isBlank())
            metaColor = meta.getMetaValue("namecolor");
        if (metaColor != null && !metaColor.isBlank()) {
            String c = metaColor.trim().replace('§', '&');
            if (!c.startsWith("&") && !c.startsWith("#")) c = "&" + c;
            if (c.startsWith("#")) c = "&" + c; // #RRGGBB → &#RRGGBB
            return c;
        }
        String prefix = meta.getPrefix();
        if (prefix == null || prefix.isEmpty()) return "";
        return extractTrailingColor(prefix);
    }

    private static String extractTrailingColor(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = text.replace('§', '&');
        // hex &#RRGGBB at end
        java.util.regex.Matcher hx = java.util.regex.Pattern
                .compile("(?i)&#[0-9a-f]{6}(?!.*&#[0-9a-f]{6})")
                .matcher(s);
        String lastHex = null;
        while (hx.find()) lastHex = hx.group();
        // also find last &x code
        String lastLegacy = "";
        java.util.regex.Matcher lg = java.util.regex.Pattern
                .compile("(?i)&(?:#[0-9a-f]{6}|[0-9a-fk-or])")
                .matcher(s);
        while (lg.find()) {
            String g = lg.group();
            if (g.length() > 2) lastHex = g; // hex form &
            else lastLegacy = g;
        }
        if (lastHex != null) return lastHex;
        return lastLegacy;
    }
}

