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
}
