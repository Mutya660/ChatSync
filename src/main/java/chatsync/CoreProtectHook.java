package chatsync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Необязательная интеграция с CoreProtect через рефлексию — не требует
 * добавления CoreProtect в зависимости pom.xml. Используется для журналирования
 * модераторских действий (например, /clear) в лог CoreProtect, чтобы их можно
 * было найти через /co lookup.
 *
 * Optional CoreProtect integration via reflection — no CoreProtect dependency
 * needs to be added to pom.xml. Used to log moderation actions (e.g. /clear)
 * into CoreProtect's log so they show up in /co lookup.
 */
public class CoreProtectHook {

    private final JavaPlugin plugin;
    private Object api;
    private Method logCommandMethod;
    private boolean available = false;

    public CoreProtectHook(JavaPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        Plugin cp = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (cp == null || !cp.isEnabled()) return;
        try {
            Method getAPI = cp.getClass().getMethod("getAPI");
            api = getAPI.invoke(cp);
            if (api == null) return;

            Method isEnabled = api.getClass().getMethod("isEnabled");
            if (!(boolean) isEnabled.invoke(api)) return;

            // CoreProtectAPI#logCommand(String user, String command) — доступен с CoreProtect 2.17+
            logCommandMethod = api.getClass().getMethod("logCommand", String.class, String.class);
            available = true;
        } catch (Exception e) {
            plugin.getLogger().info("[ChatSync] CoreProtect найден, но интеграция недоступна (несовместимая версия) — пропускаю.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** Логирует использование /clear в CoreProtect для последующего /co lookup. */
    public void logClear(Player executor, String targetDescription) {
        if (!available || executor == null) return;
        try {
            logCommandMethod.invoke(api, executor.getName(), "/clear " + targetDescription);
        } catch (Exception e) {
            plugin.getLogger().warning("CoreProtect hook failed: " + e.getMessage());
        }
    }
}
