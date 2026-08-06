package chatsync;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отслеживает суммарное время игры (playtime) и время последнего входа/выхода
 * каждого игрока. Данные накапливаются в памяти и периодически (асинхронно)
 * сохраняются в playtime.yml.
 *
 * Tracks total playtime and last login/logout time for each player. Data
 * accumulates in memory and is periodically (asynchronously) saved to playtime.yml.
 */
public class PlaytimeManager {

    private final JavaPlugin plugin;
    private final File dataFile;

    private final Map<UUID, Long> totalSeconds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLogin    = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLogout   = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameCache  = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public PlaytimeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playtime.yml");
        load();
    }

    public void onJoin(UUID uuid, String name) {
        nameCache.put(uuid, name);
        long now = System.currentTimeMillis();
        sessionStart.put(uuid, now);
        lastLogin.put(uuid, now);
        dirty = true;
    }

    public void onQuit(UUID uuid, String name) {
        nameCache.put(uuid, name);
        long now = System.currentTimeMillis();
        Long start = sessionStart.remove(uuid);
        if (start != null) {
            long added = Math.max(0, (now - start) / 1000L);
            totalSeconds.merge(uuid, added, Long::sum);
        }
        lastLogout.put(uuid, now);
        dirty = true;
    }

    /** Суммарный playtime в секундах, включая текущую незасейвленную сессию, если игрок онлайн. */
    public long getPlaytimeSeconds(UUID uuid) {
        long base = totalSeconds.getOrDefault(uuid, 0L);
        Long start = sessionStart.get(uuid);
        if (start != null) base += Math.max(0, (System.currentTimeMillis() - start) / 1000L);
        return base;
    }

    public boolean hasData(UUID uuid) {
        return totalSeconds.containsKey(uuid) || sessionStart.containsKey(uuid);
    }

    public Long getLastLogin(UUID uuid)  { return lastLogin.get(uuid); }
    public Long getLastLogout(UUID uuid) { return lastLogout.get(uuid); }

    public String nameOf(UUID uuid) {
        return nameCache.getOrDefault(uuid, uuid.toString());
    }

    /** Топ-N игроков по суммарному playtime (с учётом текущих онлайн-сессий). */
    public List<Map.Entry<UUID, Long>> top(int limit) {
        Set<UUID> all = new HashSet<>(totalSeconds.keySet());
        all.addAll(sessionStart.keySet());

        List<Map.Entry<UUID, Long>> list = new ArrayList<>();
        for (UUID uuid : all) list.add(Map.entry(uuid, getPlaytimeSeconds(uuid)));
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list.subList(0, Math.min(Math.max(limit, 0), list.size()));
    }

    public void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                totalSeconds.put(uuid, cfg.getLong(key + ".seconds", 0));
                if (cfg.contains(key + ".last_login"))  lastLogin.put(uuid, cfg.getLong(key + ".last_login"));
                if (cfg.contains(key + ".last_logout")) lastLogout.put(uuid, cfg.getLong(key + ".last_logout"));
                String name = cfg.getString(key + ".name");
                if (name != null) nameCache.put(uuid, name);
            } catch (IllegalArgumentException ignored) {
                // не UUID-ключ — пропускаем / not a UUID key — skip
            }
        }
    }

    public synchronized void saveIfDirty() {
        if (!dirty) return;
        save();
        dirty = false;
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        Set<UUID> all = new HashSet<>(totalSeconds.keySet());
        all.addAll(sessionStart.keySet());
        all.addAll(lastLogin.keySet());
        all.addAll(lastLogout.keySet());

        for (UUID uuid : all) {
            String key = uuid.toString();
            cfg.set(key + ".seconds", getPlaytimeSeconds(uuid));
            Long login = lastLogin.get(uuid);
            if (login != null) cfg.set(key + ".last_login", login);
            Long logout = lastLogout.get(uuid);
            if (logout != null) cfg.set(key + ".last_logout", logout);
            cfg.set(key + ".name", nameCache.get(uuid));
        }

        try {
            if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
            cfg.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save playtime.yml: " + e.getMessage());
        }
    }
}