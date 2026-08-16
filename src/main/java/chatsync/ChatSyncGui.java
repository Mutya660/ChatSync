package chatsync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Минималистичное GUI: топ чата, топ времени, игнор.
 * Головы: online → SkinsRestorer → Paper profile cache.
 */
public class ChatSyncGui implements Listener {

    private final ChatSync plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey pageKey;
    private final NamespacedKey targetKey;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public ChatSyncGui(ChatSync plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.pageKey = new NamespacedKey(plugin, "gui_page");
        this.targetKey = new NamespacedKey(plugin, "gui_target");
    }

    private String tr(Player p, String key, String fallback) {
        String s = plugin.t(p, key);
        if (s == null || s.isEmpty() || s.equals(key)) return fallback;
        return s;
    }

    // ── Main ─────────────────────────────────────────────────────

    public void openMain(Player player) {
        if (!plugin.getConfig().getBoolean("gui.enabled", true)) {
            player.sendMessage(LEGACY.deserialize(tr(player, "gui.disabled", "&cGUI disabled.")));
            return;
        }
        if (!player.hasPermission(plugin.getConfig().getString("gui.permission", "chatsync.gui"))) {
            player.sendMessage(LEGACY.deserialize(tr(player, "gui.no_permission", "&cNo permission.")));
            return;
        }
        String title = tr(player, "gui.main_title", "&8ChatSync");
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.MAIN, 0), 27, LEGACY.deserialize(title));

        fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);

        inv.setItem(11, item(Material.WRITABLE_BOOK,
                tr(player, "gui.btn_chat_top", "&e&lТоп чата"),
                List.of(
                        tr(player, "gui.btn_chat_top_lore", "&7Рейтинг по сообщениям"),
                        "&8",
                        tr(player, "gui.click_open", "&8› &7Открыть")
                ),
                "open_chat_top", 0, null));

        inv.setItem(13, item(Material.CLOCK,
                tr(player, "gui.btn_playtime_top", "&b&lТоп онлайна"),
                List.of(
                        tr(player, "gui.btn_playtime_top_lore", "&7Рейтинг по времени игры"),
                        "&8",
                        tr(player, "gui.click_open", "&8› &7Открыть")
                ),
                "open_playtime_top", 0, null));

        inv.setItem(15, item(Material.BARRIER,
                tr(player, "gui.btn_ignore", "&c&lИгнор"),
                List.of(
                        tr(player, "gui.btn_ignore_lore", "&7Список игнорируемых"),
                        "&8",
                        tr(player, "gui.click_open", "&8› &7Открыть")
                ),
                "open_ignore", 0, null));

        inv.setItem(22, item(Material.RED_STAINED_GLASS_PANE,
                tr(player, "gui.close", "&cЗакрыть"),
                List.of(),
                "close", 0, null));

        player.openInventory(inv);
    }

    // ── Chat top ─────────────────────────────────────────────────

    public void openChatTop(Player player, int page) {
        List<Map.Entry<UUID, ChatStatsManager.PlayerStats>> top =
                plugin.getStatsManager() != null
                        ? plugin.getStatsManager().top(plugin.getConfig().getInt("gui.top_limit", 200))
                        : List.of();
        String title = tr(player, "gui.chat_top_title", "&8Топ чата");
        openPagedHeads(player, GuiType.CHAT_TOP, page, top.size(), title, (slot, index) -> {
            if (index >= top.size()) return null;
            Map.Entry<UUID, ChatStatsManager.PlayerStats> e = top.get(index);
            String name = resolveName(e.getKey());
            ChatStatsManager.PlayerStats s = e.getValue();
            String line = tr(player, "chatstats.player_line",
                    "&7G: &f%global%  &7L: &f%local%  &7PM: &f%pm%  &7/me: &f%me%  &7BC: &f%broadcast%  &7Σ: &e%total%")
                    .replace("%global%", String.valueOf(s.global))
                    .replace("%local%", String.valueOf(s.local))
                    .replace("%pm%", String.valueOf(s.pm))
                    .replace("%me%", String.valueOf(s.me))
                    .replace("%broadcast%", String.valueOf(s.broadcast))
                    .replace("%total%", String.valueOf(s.total()));
            List<String> lore = new ArrayList<>();
            lore.add("&8#" + (index + 1));
            for (String part : line.split(" {2,}")) {
                if (!part.isBlank()) lore.add(part.trim());
            }
            return skull(e.getKey(), name, lore, "noop", page, e.getKey().toString());
        });
    }

    // ── Playtime top ─────────────────────────────────────────────

    public void openPlaytimeTop(Player player, int page) {
        List<Map.Entry<UUID, Long>> top =
                plugin.getPlaytimeManager() != null
                        ? plugin.getPlaytimeManager().top(plugin.getConfig().getInt("gui.top_limit", 200))
                        : List.of();
        String title = tr(player, "gui.playtime_top_title", "&8Топ онлайна");
        openPagedHeads(player, GuiType.PLAYTIME_TOP, page, top.size(), title, (slot, index) -> {
            if (index >= top.size()) return null;
            Map.Entry<UUID, Long> e = top.get(index);
            String name = resolveName(e.getKey());
            String time = formatTime(player, e.getValue());
            return skull(e.getKey(), name,
                    List.of("&8#" + (index + 1),
                            tr(player, "gui.playtime_line", "&7Время: &f%time%").replace("%time%", time)),
                    "noop", page, e.getKey().toString());
        });
    }

    // ── Ignore ───────────────────────────────────────────────────

    public void openIgnore(Player player, int page) {
        List<UUID> ignored = new ArrayList<>(plugin.getIgnoredUuids(player.getUniqueId()));
        String title = tr(player, "gui.ignore_title", "&8Игнор");
        openPagedHeads(player, GuiType.IGNORE, page, ignored.size(), title, (slot, index) -> {
            if (index >= ignored.size()) return null;
            UUID uid = ignored.get(index);
            String name = resolveName(uid);
            return skull(uid, name,
                    List.of(tr(player, "gui.ignore_lore", "&7Клик — снять игнор")),
                    "unignore", page, uid.toString());
        });
    }

    // ── Paged layout ─────────────────────────────────────────────

    private interface SlotFiller {
        ItemStack get(int slot, int index);
    }

    private void openPagedHeads(Player player, GuiType type, int page, int total, String title, SlotFiller filler) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(type, page), 54, LEGACY.deserialize(title));
        int perPage = Math.max(1, Math.min(45, plugin.getConfig().getInt("gui.page_size", 45)));
        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= total) break;
            ItemStack it = filler.get(i, index);
            if (it != null) inv.setItem(i, it);
        }
        // bottom bar
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        }
        inv.setItem(45, item(Material.ARROW, tr(player, "gui.back", "&7← Назад"), List.of(), "open_main", 0, null));
        if (page > 0) {
            inv.setItem(48, item(Material.SPECTRAL_ARROW, tr(player, "gui.prev", "&7← Пред."), List.of(), "page_prev", page - 1, null));
        }
        inv.setItem(49, item(Material.PAPER,
                tr(player, "gui.page", "&f%page%").replace("%page%", String.valueOf(page + 1)),
                List.of(), "noop", page, null));
        if (start + perPage < total) {
            inv.setItem(50, item(Material.SPECTRAL_ARROW, tr(player, "gui.next", "&7След. →"), List.of(), "page_next", page + 1, null));
        }
        if (total == 0) {
            inv.setItem(22, item(Material.STRUCTURE_VOID,
                    tr(player, "gui.empty", "&8Пусто"), List.of(), "noop", 0, null));
        }
        player.openInventory(inv);
    }

    private void fillBorder(Inventory inv, Material mat) {
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            boolean edge = row == 0 || row == (size / 9 - 1) || col == 0 || col == 8;
            if (edge) inv.setItem(i, pane(mat));
        }
    }

    private ItemStack pane(Material mat) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        stack.setItemMeta(meta);
        return stack;
    }

    // ── Clicks ───────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        Integer page = meta.getPersistentDataContainer().get(pageKey, PersistentDataType.INTEGER);
        if (page == null) page = 0;
        String target = meta.getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);

        switch (action) {
            case "close" -> player.closeInventory();
            case "open_main" -> openMain(player);
            case "open_chat_top" -> openChatTop(player, 0);
            case "open_playtime_top" -> openPlaytimeTop(player, 0);
            case "open_ignore" -> openIgnore(player, 0);
            case "page_prev", "page_next" -> {
                switch (holder.type) {
                    case CHAT_TOP -> openChatTop(player, page);
                    case PLAYTIME_TOP -> openPlaytimeTop(player, page);
                    case IGNORE -> openIgnore(player, page);
                    default -> {}
                }
            }
            case "unignore" -> {
                if (!player.hasPermission(plugin.getConfig().getString("gui.permission_unmute", "chatsync.gui.unmute"))) {
                    player.sendMessage(LEGACY.deserialize(tr(player, "gui.no_permission", "&cNo permission.")));
                    return;
                }
                if (target == null) return;
                UUID tid;
                try { tid = UUID.fromString(target); } catch (Exception e) { return; }
                plugin.unignorePlayer(player, tid);
                player.sendMessage(LEGACY.deserialize(
                        tr(player, "gui.unignored", "&aСнят игнор: &f%player%")
                                .replace("%player%", resolveName(tid))));
                openIgnore(player, page);
            }
            default -> {}
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) event.setCancelled(true);
    }

    // ── Items ────────────────────────────────────────────────────

    private ItemStack item(Material mat, String name, List<String> lore, String action, int page, String target) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lc = new ArrayList<>();
            for (String l : lore) lc.add(LEGACY.deserialize(l));
            meta.lore(lc);
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page);
        if (target != null) meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack skull(UUID uuid, String name, List<String> lore, String action, int page, String target) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        applySkullSkin(meta, uuid, name);
        meta.displayName(LEGACY.deserialize("&f" + (name != null ? name : shortId(uuid))));
        if (lore != null) {
            List<Component> lc = new ArrayList<>();
            for (String l : lore) lc.add(LEGACY.deserialize(l));
            meta.lore(lc);
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page);
        if (target != null) meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Скин: online → SkinsRestorer (UUID/name) → Paper profile → OfflinePlayer.
     * Нужно для игроков из старых stats/playtime до обновления плагина.
     */
    private void applySkullSkin(SkullMeta meta, UUID uuid, String name) {
        Player online = uuid != null ? Bukkit.getPlayer(uuid) : null;
        if (online != null) {
            try {
                meta.setOwningPlayer(online);
                return;
            } catch (Throwable ignored) {}
        }

        // SkinsRestorer / offline textures → base64 → PlayerProfile textures
        try {
            String[] tex = plugin.resolveSkinTexturesOffline(uuid, name);
            if (tex != null && tex[0] != null && !tex[0].isEmpty()) {
                if (applyTextureToSkull(meta, uuid, name, tex[0])) {
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // Paper createProfile + cache
        try {
            Method create = Bukkit.class.getMethod("createProfile", UUID.class, String.class);
            Object profile = create.invoke(null, uuid != null ? uuid : UUID.randomUUID(),
                    name != null ? name : "Player");
            try {
                // Cache only — never complete(true): that hits Mojang and freezes the server thread
                profile.getClass().getMethod("completeFromCache").invoke(profile);
            } catch (NoSuchMethodException ignored) {}
            try {
                meta.getClass().getMethod("setPlayerProfile", Class.forName("com.destroystokyo.paper.profile.PlayerProfile"))
                        .invoke(meta, profile);
                return;
            } catch (Throwable ignored) {}
            if (profile instanceof PlayerProfile bp) {
                meta.setOwnerProfile(bp);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            if (uuid != null) meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            else if (name != null) meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
        } catch (Throwable ignored) {}
    }

    /** Apply base64 textures property to SkullMeta via PlayerProfile / reflection. */
    private boolean applyTextureToSkull(SkullMeta meta, UUID uuid, String name, String textureValue) {
        UUID id = uuid != null ? uuid : UUID.nameUUIDFromBytes(("OfflinePlayer:" + (name != null ? name : "x")).getBytes(StandardCharsets.UTF_8));
        String display = name != null ? name : "Player";

        // 1) Bukkit PlayerProfile + set textures URL from base64 JSON
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(id, display);
            String url = extractSkinUrl(textureValue);
            if (url != null) {
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(new URL(url));
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
                return true;
            }
        } catch (Throwable ignored) {}

        // 2) Paper profile setProperty textures
        try {
            Method create = Bukkit.class.getMethod("createProfile", UUID.class, String.class);
            Object profile = create.invoke(null, id, display);
            // setProperty("textures", value, signature)
            try {
                Method setProp = profile.getClass().getMethod("setProperty", String.class, String.class);
                setProp.invoke(profile, "textures", textureValue);
            } catch (NoSuchMethodException e) {
                try {
                    Class<?> propClass = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
                    Constructor<?> ctor;
                    Object prop;
                    try {
                        ctor = propClass.getConstructor(String.class, String.class, String.class);
                        prop = ctor.newInstance("textures", textureValue, null);
                    } catch (NoSuchMethodException e2) {
                        ctor = propClass.getConstructor(String.class, String.class);
                        prop = ctor.newInstance("textures", textureValue);
                    }
                    Method setProperty = profile.getClass().getMethod("setProperty", propClass);
                    setProperty.invoke(profile, prop);
                } catch (Throwable ignored) {}
            }
            meta.getClass().getMethod("setPlayerProfile", Class.forName("com.destroystokyo.paper.profile.PlayerProfile"))
                    .invoke(meta, profile);
            return true;
        } catch (Throwable ignored) {}

        return false;
    }

    private String extractSkinUrl(String base64Value) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            // {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/..."}}}
            int i = json.indexOf("\"url\"");
            if (i < 0) return null;
            int colon = json.indexOf(':', i);
            int q1 = json.indexOf('"', colon + 1);
            int q2 = json.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) return null;
            return json.substring(q1 + 1, q2);
        } catch (Throwable t) {
            return null;
        }
    }

    private String resolveName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        if (plugin.getPlaytimeManager() != null) {
            String n = plugin.getPlaytimeManager().nameOf(uuid);
            if (n != null && !n.equals(uuid.toString()) && !n.equals(shortId(uuid))) return n;
        }
        if (plugin.getStatsManager() != null) {
            String n = plugin.getStatsManager().nameOf(uuid);
            if (n != null && !n.equals(uuid.toString()) && !n.equals(shortId(uuid))) return n;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op.getName() != null) return op.getName();
        return shortId(uuid);
    }

    private static String shortId(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private String formatTime(Player viewer, long seconds) {
        // Localized units from lang duration.*
        String dUnit = tr(viewer, "duration.days", "d");
        String hUnit = tr(viewer, "duration.hours", "h");
        String mUnit = tr(viewer, "duration.minutes", "m");
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        if (d > 0) return d + dUnit + " " + h + hUnit;
        if (h > 0) return h + hUnit + " " + m + mUnit;
        return m + mUnit;
    }

    enum GuiType { MAIN, CHAT_TOP, PLAYTIME_TOP, IGNORE }

    static class GuiHolder implements InventoryHolder {
        final GuiType type;
        final int page;
        GuiHolder(GuiType type, int page) {
            this.type = type;
            this.page = page;
        }
        @Override public Inventory getInventory() { return null; }
    }
}
