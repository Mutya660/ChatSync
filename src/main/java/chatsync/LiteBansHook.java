package chatsync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soft-depend интеграция с LiteBans.
 * ChatSync перехватывает AsyncChatEvent (HIGHEST), поэтому ванильный mute LiteBans
 * сам по себе не блокирует чат — проверяем через API (litebans.api.Database).
 *
 * Запросы к БД только асинхронно; для синхронных команд (/me, /msg) используется кэш.
 */
public class LiteBansHook {

    private final JavaPlugin plugin;
    private final boolean available;
    private Object database; // litebans.api.Database
    private Method isPlayerMuted;

    /** UUID → true если в муте (кэш, обновляется async). */
    private final Map<UUID, Boolean> muteCache = new ConcurrentHashMap<>();

    public LiteBansHook(JavaPlugin plugin) {
        this.plugin = plugin;
        boolean found = false;
        Object db = null;
        Method muted = null;

        if (Bukkit.getPluginManager().getPlugin("LiteBans") != null) {
            try {
                Class<?> databaseClass = Class.forName("litebans.api.Database");
                Method get = databaseClass.getMethod("get");
                db = get.invoke(null);
                muted = databaseClass.getMethod("isPlayerMuted", UUID.class, String.class);
                found = true;
                plugin.getLogger().info("LiteBans detected: mute checks enabled.");
            } catch (Throwable t) {
                plugin.getLogger().warning("LiteBans found but API unavailable: " + t.getMessage());
            }
        }

        this.available = found;
        this.database = db;
        this.isPlayerMuted = muted;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Проверка мута. Безопасно вызывать с async-потока (AsyncChatEvent).
     * С main-потока возвращает кэш (может быть слегка устаревшим).
     */
    public boolean isMuted(Player player) {
        if (!available || player == null) return false;
        if (!plugin.getConfig().getBoolean("integrations.litebans.enabled", true)) return false;
        if (!plugin.getConfig().getBoolean("integrations.litebans.block_muted", true)) return false;

        // Если уже не main-thread — спрашиваем БД напрямую
        if (!Bukkit.isPrimaryThread()) {
            boolean muted = queryMuted(player.getUniqueId(), playerIp(player));
            muteCache.put(player.getUniqueId(), muted);
            return muted;
        }

        // Main thread — только кэш; фоном обновим
        Boolean cached = muteCache.get(player.getUniqueId());
        refreshAsync(player);
        return cached != null && cached;
    }

    public void onJoin(Player player) {
        if (!available) return;
        refreshAsync(player);
    }

    public void onQuit(UUID uuid) {
        muteCache.remove(uuid);
    }

    private void refreshAsync(Player player) {
        if (!available || player == null) return;
        final UUID uuid = player.getUniqueId();
        final String ip = playerIp(player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean muted = queryMuted(uuid, ip);
            muteCache.put(uuid, muted);
        });
    }

    private boolean queryMuted(UUID uuid, String ip) {
        try {
            Object result = isPlayerMuted.invoke(database, uuid, ip);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            // IllegalStateException если вызвали с main — игнор
            return muteCache.getOrDefault(uuid, false);
        }
    }

    private static String playerIp(Player player) {
        try {
            if (player.getAddress() != null && player.getAddress().getAddress() != null) {
                return player.getAddress().getAddress().getHostAddress();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
