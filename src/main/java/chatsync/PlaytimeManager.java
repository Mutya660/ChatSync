package chatsync;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Playtime на основе ванильной статистики Minecraft ({@link Statistic#PLAY_ONE_MINUTE}),
 * той же, что использует TAB / PlaceholderAPI (%statistic_hours_played%).
 *
 * Несмотря на название, PLAY_ONE_MINUTE считает <b>тики</b> (1 сек = 20 тиков).
 * Для офлайн-игроков значение кэшируется в playtime.yml при выходе.
 *
 * Также хранит last login / last logout для /lastseen.
 */
public class PlaytimeManager {

    private final JavaPlugin plugin;
    private final File dataFile;

    /** Кэш секунд (из ванильной статистики) для офлайн-игроков и топа. */
    private final Map<UUID, Long> totalSeconds = new ConcurrentHashMap<>();
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
        lastLogin.put(uuid, System.currentTimeMillis());
        // Сразу синхронизируем ванильную статистику в кэш
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            totalSeconds.put(uuid, vanillaSeconds(p));
            dirty = true;
        }
    }

    public void onQuit(UUID uuid, String name) {
        nameCache.put(uuid, name);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            totalSeconds.put(uuid, vanillaSeconds(p));
        }
        lastLogout.put(uuid, System.currentTimeMillis());
        dirty = true;
    }

    /**
     * Секунды наигранного времени.
     * Онлайн → живая ванильная статистика (как TAB).
     * Офлайн → последнее сохранённое значение из playtime.yml.
     */
    public long getPlaytimeSeconds(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            return vanillaSeconds(online);
        }
        return totalSeconds.getOrDefault(uuid, 0L);
    }

    /** Тики PLAY_ONE_MINUTE / 20 = секунды. */
    public static long vanillaSeconds(Player player) {
        try {
            // PLAY_ONE_MINUTE: +1 каждый тик (1/20 сек), не каждую минуту
            return Math.max(0L, player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L);
        } catch (IllegalArgumentException e) {
            return 0L;
        }
    }

    public boolean hasData(UUID uuid) {
        if (totalSeconds.containsKey(uuid)) return true;
        Player online = Bukkit.getPlayer(uuid);
        return online != null && online.isOnline();
    }

    public Long getLastLogin(UUID uuid)  { return lastLogin.get(uuid); }
    public Long getLastLogout(UUID uuid) { return lastLogout.get(uuid); }

    public String nameOf(UUID uuid) {
        return nameCache.getOrDefault(uuid, uuid.toString());
    }

    /**
     * Топ-N по playtime.
     * Для онлайн-игроков берём актуальный ванильный счётчик; для остальных — кэш.
     */
    public List<Map.Entry<UUID, Long>> top(int limit) {
        // Обновляем кэш для всех онлайн
        for (Player p : Bukkit.getOnlinePlayers()) {
            totalSeconds.put(p.getUniqueId(), vanillaSeconds(p));
            nameCache.put(p.getUniqueId(), p.getName());
        }

        Set<UUID> all = new HashSet<>(totalSeconds.keySet());
        List<Map.Entry<UUID, Long>> list = new ArrayList<>();
        for (UUID uuid : all) {
            list.add(Map.entry(uuid, getPlaytimeSeconds(uuid)));
        }
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
                // не UUID — пропускаем
            }
        }
    }

    public synchronized void saveIfDirty() {
        if (!dirty) return;
        // перед сохранением подтянем онлайн-игроков
        for (Player p : Bukkit.getOnlinePlayers()) {
            totalSeconds.put(p.getUniqueId(), vanillaSeconds(p));
            nameCache.put(p.getUniqueId(), p.getName());
        }
        save();
        dirty = false;
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        Set<UUID> all = new HashSet<>(totalSeconds.keySet());
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
