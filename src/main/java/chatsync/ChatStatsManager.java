package chatsync;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит статистику сообщений (глобальный чат, локальный чат, ЛС, /me) по каждому игроку.
 * Данные накапливаются в памяти и периодически (асинхронно) сохраняются в stats.yml.
 *
 * Tracks per-player message statistics (global chat, local chat, PMs, /me).
 * Data accumulates in memory and is periodically saved (asynchronously) to stats.yml.
 */
public class ChatStatsManager {

    public enum MessageType { GLOBAL, LOCAL, PM, ME }

    private final JavaPlugin plugin;
    private final File statsFile;
    private final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public ChatStatsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    /** Счётчики сообщений одного игрока. / Message counters for a single player. */
    public static class PlayerStats {
        public int global;
        public int local;
        public int pm;
        public int me;

        public int total() {
            return global + local + pm + me;
        }
    }

    public void record(UUID uuid, String name, MessageType type) {
        nameCache.put(uuid, name);
        PlayerStats s = stats.computeIfAbsent(uuid, k -> new PlayerStats());
        switch (type) {
            case GLOBAL -> s.global++;
            case LOCAL -> s.local++;
            case PM -> s.pm++;
            case ME -> s.me++;
        }
        dirty = true;
    }

    public PlayerStats get(UUID uuid) {
        return stats.get(uuid);
    }

    public String nameOf(UUID uuid) {
        return nameCache.getOrDefault(uuid, uuid.toString());
    }

    /** Возвращает топ-N игроков по общему количеству сообщений. / Returns the top-N players by total messages. */
    public List<Map.Entry<UUID, PlayerStats>> top(int limit) {
        List<Map.Entry<UUID, PlayerStats>> list = new ArrayList<>(stats.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().total(), a.getValue().total()));
        return list.subList(0, Math.min(Math.max(limit, 0), list.size()));
    }

    public void load() {
        if (!statsFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(statsFile);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                PlayerStats s = new PlayerStats();
                s.global = cfg.getInt(key + ".global", 0);
                s.local = cfg.getInt(key + ".local", 0);
                s.pm = cfg.getInt(key + ".pm", 0);
                s.me = cfg.getInt(key + ".me", 0);
                stats.put(uuid, s);
                String name = cfg.getString(key + ".name");
                if (name != null) nameCache.put(uuid, name);
            } catch (IllegalArgumentException ignored) {
                // Не UUID-ключ — пропускаем / not a UUID key — skip
            }
        }
    }

    /** Сохраняет только если были изменения с прошлого сохранения. / Saves only if data changed since last save. */
    public synchronized void saveIfDirty() {
        if (!dirty) return;
        save();
        dirty = false;
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerStats> e : stats.entrySet()) {
            String key = e.getKey().toString();
            PlayerStats s = e.getValue();
            cfg.set(key + ".global", s.global);
            cfg.set(key + ".local", s.local);
            cfg.set(key + ".pm", s.pm);
            cfg.set(key + ".me", s.me);
            cfg.set(key + ".name", nameCache.get(e.getKey()));
        }
        try {
            if (!statsFile.getParentFile().exists()) statsFile.getParentFile().mkdirs();
            cfg.save(statsFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save stats.yml: " + e.getMessage());
        }
    }
}
