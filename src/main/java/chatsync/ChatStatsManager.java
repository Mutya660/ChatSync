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
 * Статистика сообщений (глобальный / локальный / ЛС / /me) по каждому игроку.
 * Данные в памяти + периодическое сохранение в stats.yml.
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

    public static class PlayerStats {
        public int global;
        public int local;
        public int pm;
        public int me;

        public int total() {
            return global + local + pm + me;
        }

        public PlayerStats copy() {
            PlayerStats c = new PlayerStats();
            c.global = global;
            c.local = local;
            c.pm = pm;
            c.me = me;
            return c;
        }
    }

    public void record(UUID uuid, String name, MessageType type) {
        if (uuid == null) return;
        if (name != null && !name.isEmpty()) nameCache.put(uuid, name);
        // синхронизация на объект счётчика — AsyncChatEvent идёт с async-потока
        PlayerStats s = stats.computeIfAbsent(uuid, k -> new PlayerStats());
        synchronized (s) {
            switch (type) {
                case GLOBAL -> s.global++;
                case LOCAL  -> s.local++;
                case PM     -> s.pm++;
                case ME     -> s.me++;
            }
        }
        dirty = true;
    }

    public PlayerStats get(UUID uuid) {
        PlayerStats s = stats.get(uuid);
        return s == null ? null : s.copy();
    }

    /** Есть ли хотя бы одна запись. */
    public boolean isEmpty() {
        return stats.isEmpty();
    }

    public int size() {
        return stats.size();
    }

    public String nameOf(UUID uuid) {
        return nameCache.getOrDefault(uuid, uuid.toString());
    }

    /** Поиск UUID по нику (без учёта регистра) в кэше статистики. */
    public UUID findUuidByName(String name) {
        if (name == null) return null;
        for (Map.Entry<UUID, String> e : nameCache.entrySet()) {
            if (e.getValue() != null && e.getValue().equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        return null;
    }

    public List<Map.Entry<UUID, PlayerStats>> top(int limit) {
        List<Map.Entry<UUID, PlayerStats>> list = new ArrayList<>(stats.size());
        for (Map.Entry<UUID, PlayerStats> e : stats.entrySet()) {
            PlayerStats snap = e.getValue().copy();
            if (snap.total() <= 0) continue; // не показываем нулевых
            list.add(Map.entry(e.getKey(), snap));
        }
        list.sort((a, b) -> Integer.compare(b.getValue().total(), a.getValue().total()));
        int n = Math.min(Math.max(limit, 0), list.size());
        return list.subList(0, n);
    }

    public void load() {
        if (!statsFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(statsFile);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                PlayerStats s = new PlayerStats();
                s.global = cfg.getInt(key + ".global", 0);
                s.local  = cfg.getInt(key + ".local", 0);
                s.pm     = cfg.getInt(key + ".pm", 0);
                s.me     = cfg.getInt(key + ".me", 0);
                if (s.total() > 0 || cfg.contains(key + ".name")) {
                    stats.put(uuid, s);
                }
                String name = cfg.getString(key + ".name");
                if (name != null && !name.isEmpty()) nameCache.put(uuid, name);
            } catch (IllegalArgumentException ignored) {
                // не UUID
            }
        }
        plugin.getLogger().info("ChatStats: loaded " + stats.size() + " player(s) from stats.yml");
    }

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
            synchronized (s) {
                cfg.set(key + ".global", s.global);
                cfg.set(key + ".local", s.local);
                cfg.set(key + ".pm", s.pm);
                cfg.set(key + ".me", s.me);
            }
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
