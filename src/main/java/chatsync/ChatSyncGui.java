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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Минималистичное GUI. Язык = локаль игрока (en/ru/de/fr).
 */
public class ChatSyncGui implements Listener {

    private final ChatSync plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey pageKey;
    private final NamespacedKey targetKey;
    private final Map<UUID, UUID> pendingUnignore = new ConcurrentHashMap<>();

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

        inv.setItem(11, item(Material.PAPER,
                tr(player, "gui.btn_chat_top", "&fChat top"),
                List.of(tr(player, "gui.btn_chat_top_lore", "&7Messages ranking")),
                "open_chat_top", 0, null));
        inv.setItem(13, item(Material.CLOCK,
                tr(player, "gui.btn_playtime_top", "&fPlaytime top"),
                List.of(tr(player, "gui.btn_playtime_top_lore", "&7Hours ranking")),
                "open_playtime_top", 0, null));
        inv.setItem(15, item(Material.NAME_TAG,
                tr(player, "gui.btn_ignore", "&fIgnore list"),
                List.of(tr(player, "gui.btn_ignore_lore", "&7Manage ignored")),
                "open_ignore", 0, null));
        inv.setItem(22, item(Material.BOOK,
                tr(player, "gui.btn_commands", "&fCommands"),
                List.of(tr(player, "gui.btn_commands_lore", "&7Plugin commands")),
                "open_commands", 0, null));

        player.openInventory(inv);
    }

    public void openChatTop(Player player, int page) {
        List<Map.Entry<UUID, ChatStatsManager.PlayerStats>> top =
                plugin.getStatsManager() != null ? plugin.getStatsManager().top(200) : List.of();
        String title = tr(player, "gui.chat_top_title", "&8Chat top");
        openPagedHeads(player, GuiType.CHAT_TOP, page, top.size(), title, (slot, index) -> {
            if (index >= top.size()) return null;
            Map.Entry<UUID, ChatStatsManager.PlayerStats> e = top.get(index);
            String name = resolveName(e.getKey());
            ChatStatsManager.PlayerStats s = e.getValue();
            // Same format as /chatstats player_line
            String line = tr(player, "chatstats.player_line",
                    "&7Global: &f%global%  &7Local: &f%local%  &7PMs: &f%pm%  &7/me: &f%me%  &7BC: &f%broadcast%  &7Total: &e%total%")
                    .replace("%global%", String.valueOf(s.global))
                    .replace("%local%", String.valueOf(s.local))
                    .replace("%pm%", String.valueOf(s.pm))
                    .replace("%me%", String.valueOf(s.me))
                    .replace("%broadcast%", String.valueOf(s.broadcast))
                    .replace("%total%", String.valueOf(s.total()));
            List<String> lore = new ArrayList<>();
            lore.add("&8#" + (index + 1));
            // split long line into short lore lines for readability
            for (String part : line.split(" {2,}")) {
                if (!part.isBlank()) lore.add(part.trim());
            }
            return skull(e.getKey(), name, lore, "noop", page, e.getKey().toString());
        });
    }

    public void openPlaytimeTop(Player player, int page) {
        List<Map.Entry<UUID, Long>> top =
                plugin.getPlaytimeManager() != null ? plugin.getPlaytimeManager().top(200) : List.of();
        String title = tr(player, "gui.playtime_top_title", "&8Playtime top");
        openPagedHeads(player, GuiType.PLAYTIME_TOP, page, top.size(), title, (slot, index) -> {
            if (index >= top.size()) return null;
            Map.Entry<UUID, Long> e = top.get(index);
            String name = resolveName(e.getKey());
            String time = formatTime(e.getValue());
            return skull(e.getKey(), name,
                    List.of("&8#" + (index + 1),
                            tr(player, "gui.playtime_line", "&7Playtime: &f%time%").replace("%time%", time)),
                    "noop", page, e.getKey().toString());
        });
    }

    public void openIgnore(Player player, int page) {
        List<UUID> ignored = new ArrayList<>(plugin.getIgnoredUuids(player.getUniqueId()));
        String title = tr(player, "gui.ignore_title", "&8Ignore list");
        openPagedHeads(player, GuiType.IGNORE, page, ignored.size(), title, (slot, index) -> {
            if (index >= ignored.size()) return null;
            UUID uid = ignored.get(index);
            String name = resolveName(uid);
            return skull(uid, name,
                    List.of(tr(player, "gui.ignore_lore", "&7Click twice to unignore")),
                    "unignore", page, uid.toString());
        });
    }

    public void openCommands(Player player, int page) {
        // name, desc key, permission
        String[][] cmds = {
                {"/msg <player>", "gui.cmd.msg", "-"},
                {"/reply <msg>", "gui.cmd.reply", "-"},
                {"/ignore <player>", "gui.cmd.ignore", "-"},
                {"/ignorelist", "gui.cmd.ignorelist", "-"},
                {"/socialspy", "gui.cmd.socialspy", "chatsync.spy"},
                {"/me <action>", "gui.cmd.me", "chatsync.me"},
                {"/clear", "gui.cmd.clear", "chatsync.clear"},
                {"/chatstats", "gui.cmd.chatstats", "chatsync.chatstats"},
                {"/broadcast", "gui.cmd.broadcast", "chatsync.broadcast"},
                {"/playtime", "gui.cmd.playtime", "chatsync.playtime"},
                {"/playtimetop", "gui.cmd.playtimetop", "chatsync.playtimetop"},
                {"/lastseen", "gui.cmd.lastseen", "chatsync.lastseen"},
                {"/team", "gui.cmd.team", "chatsync.team"},
                {"/chatsync gui", "gui.cmd.gui", "chatsync.gui"},
                {"/chatsync reload", "gui.cmd.reload", "chatsync.admin"}
        };
        String title = tr(player, "gui.commands_title", "&8Commands");
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.COMMANDS, page), 54, LEGACY.deserialize(title));
        int perPage = 45;
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < cmds.length; i++) {
            String[] c = cmds[start + i];
            String desc = tr(player, c[1], c[1]);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + desc);
            if (c[2] != null && !c[2].equals("-")) {
                lore.add(tr(player, "gui.cmd_perm", "&8perm: &f%perm%").replace("%perm%", c[2]));
            }
            inv.setItem(i, item(Material.MAP, "&f" + c[0], lore, "noop", page, null));
        }
        inv.setItem(45, item(Material.ARROW, tr(player, "gui.back", "&7Back"), List.of(), "open_main", 0, null));
        if (page > 0) inv.setItem(48, item(Material.ARROW, tr(player, "gui.prev", "&7Prev"), List.of(), "open_commands", page - 1, null));
        if (start + perPage < cmds.length) inv.setItem(50, item(Material.ARROW, tr(player, "gui.next", "&7Next"), List.of(), "open_commands", page + 1, null));
        player.openInventory(inv);
    }

    private interface SlotFiller {
        ItemStack get(int slot, int index);
    }

    private void openPagedHeads(Player player, GuiType type, int page, int total, String title, SlotFiller filler) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(type, page), 54, LEGACY.deserialize(title));
        int perPage = 45;
        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= total) break;
            ItemStack it = filler.get(i, index);
            if (it != null) inv.setItem(i, it);
        }
        inv.setItem(45, item(Material.ARROW, tr(player, "gui.back", "&7Back"), List.of(), "open_main", 0, null));
        if (page > 0) inv.setItem(48, item(Material.ARROW, tr(player, "gui.prev", "&7Prev"), List.of(), "page_prev", page - 1, null));
        if (start + perPage < total) inv.setItem(50, item(Material.ARROW, tr(player, "gui.next", "&7Next"), List.of(), "page_next", page + 1, null));
        if (total == 0) {
            inv.setItem(22, item(Material.GRAY_STAINED_GLASS_PANE,
                    tr(player, "gui.empty", "&8Empty"), List.of(), "noop", 0, null));
        }
        player.openInventory(inv);
    }

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
            case "open_commands" -> openCommands(player, page);
            case "page_prev", "page_next" -> {
                switch (holder.type) {
                    case CHAT_TOP -> openChatTop(player, page);
                    case PLAYTIME_TOP -> openPlaytimeTop(player, page);
                    case IGNORE -> openIgnore(player, page);
                    case COMMANDS -> openCommands(player, page);
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
                        tr(player, "gui.unignored", "&aUnignored &f%player%")
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
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        try { meta.setOwningPlayer(op); } catch (Throwable ignored) {}
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

    private String formatTime(long seconds) {
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    enum GuiType { MAIN, CHAT_TOP, PLAYTIME_TOP, IGNORE, COMMANDS }

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
