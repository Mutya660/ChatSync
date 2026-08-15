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
 * Inventory GUI: main menu, chat top, playtime top, ignore list, commands list.
 */
public class ChatSyncGui implements Listener {

    private final ChatSync plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey pageKey;
    private final NamespacedKey targetKey;
    /** Pending unignore confirm: viewer UUID → target UUID */
    private final Map<UUID, UUID> pendingUnignore = new ConcurrentHashMap<>();

    public ChatSyncGui(ChatSync plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.pageKey = new NamespacedKey(plugin, "gui_page");
        this.targetKey = new NamespacedKey(plugin, "gui_target");
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public void openMain(Player player) {
        if (!plugin.getConfig().getBoolean("gui.enabled", true)) {
            player.sendMessage(LEGACY.deserialize("&cGUI disabled."));
            return;
        }
        if (!player.hasPermission(plugin.getConfig().getString("gui.permission", "chatsync.gui"))) {
            player.sendMessage(LEGACY.deserialize("&cNo permission."));
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.MAIN, 0), 27, LEGACY.deserialize("&8ChatSync Menu"));
        inv.setItem(10, item(Material.WRITABLE_BOOK, "&aChat Top", List.of("&7Top by messages", "&eClick to open"), "open_chat_top", 0, null));
        inv.setItem(12, item(Material.CLOCK, "&bPlaytime Top", List.of("&7Top by hours played", "&eClick to open"), "open_playtime_top", 0, null));
        inv.setItem(14, item(Material.BARRIER, "&cIgnore List", List.of("&7Players you ignore", "&eClick to manage"), "open_ignore", 0, null));
        inv.setItem(16, item(Material.COMMAND_BLOCK, "&eCommands", List.of("&7Plugin commands list", "&eClick to open"), "open_commands", 0, null));
        inv.setItem(22, item(Material.ARROW, "&7Close", List.of(), "close", 0, null));
        player.openInventory(inv);
    }

    public void openChatTop(Player player, int page) {
        List<Map.Entry<UUID, ChatStatsManager.PlayerStats>> top =
                plugin.getStatsManager() != null ? plugin.getStatsManager().top(100) : List.of();
        openPagedHeads(player, GuiType.CHAT_TOP, page, top.size(), (slot, index) -> {
            if (index >= top.size()) return null;
            Map.Entry<UUID, ChatStatsManager.PlayerStats> e = top.get(index);
            String name = plugin.getStatsManager().nameOf(e.getKey());
            ChatStatsManager.PlayerStats s = e.getValue();
            return skull(e.getKey(), name,
                    List.of("&7Total: &f" + s.total(),
                            "&8G:&f" + s.global + " &8L:&f" + s.local + " &8PM:&f" + s.pm,
                            "&e#" + (index + 1)),
                    "noop", page, e.getKey().toString());
        }, "Chat Top");
    }

    public void openPlaytimeTop(Player player, int page) {
        List<Map.Entry<UUID, Long>> top =
                plugin.getPlaytimeManager() != null ? plugin.getPlaytimeManager().top(100) : List.of();
        openPagedHeads(player, GuiType.PLAYTIME_TOP, page, top.size(), (slot, index) -> {
            if (index >= top.size()) return null;
            Map.Entry<UUID, Long> e = top.get(index);
            String name = plugin.getPlaytimeManager().nameOf(e.getKey());
            long sec = e.getValue();
            String time = formatTime(sec);
            return skull(e.getKey(), name,
                    List.of("&7Playtime: &f" + time, "&e#" + (index + 1)),
                    "noop", page, e.getKey().toString());
        }, "Playtime Top");
    }

    public void openIgnore(Player player, int page) {
        List<UUID> ignored = new ArrayList<>(plugin.getIgnoredUuids(player.getUniqueId()));
        openPagedHeads(player, GuiType.IGNORE, page, ignored.size(), (slot, index) -> {
            if (index >= ignored.size()) return null;
            UUID uid = ignored.get(index);
            String name = resolveName(uid);
            return skull(uid, name,
                    List.of("&cIgnored", "&eClick twice to unignore"),
                    "unignore", page, uid.toString());
        }, "Ignore List");
    }

    public void openCommands(Player player, int page) {
        List<String[]> cmds = List.of(
                new String[]{"/msg", "Private message", "chatsync.msg.console (console)"},
                new String[]{"/reply", "Reply to last PM", "-"},
                new String[]{"/ignore", "Ignore a player", "-"},
                new String[]{"/ignorelist", "List ignored", "-"},
                new String[]{"/socialspy", "Spy on PMs", "chatsync.spy"},
                new String[]{"/me", "Roleplay action", "chatsync.me"},
                new String[]{"/clear", "Clear chat", "chatsync.clear"},
                new String[]{"/chatstats", "Chat statistics", "chatsync.chatstats"},
                new String[]{"/broadcast", "Server announcement", "chatsync.broadcast"},
                new String[]{"/playtime", "Playtime", "chatsync.playtime"},
                new String[]{"/playtimetop", "Playtime leaderboard", "chatsync.playtimetop"},
                new String[]{"/lastseen", "Last seen", "chatsync.lastseen"},
                new String[]{"/team", "Team / party", "chatsync.team"},
                new String[]{"/chatsync gui", "This menu", "chatsync.gui"},
                new String[]{"/chatsync reload", "Reload config", "chatsync.admin"}
        );
        int size = 54;
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.COMMANDS, page), size, LEGACY.deserialize("&8Commands"));
        int perPage = 45;
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < cmds.size(); i++) {
            String[] c = cmds.get(start + i);
            inv.setItem(i, item(Material.PAPER, "&e" + c[0], List.of("&7" + c[1], "&8Perm: &f" + c[2]), "noop", page, null));
        }
        inv.setItem(45, item(Material.ARROW, "&7Back", List.of(), "open_main", 0, null));
        if (page > 0) inv.setItem(48, item(Material.ARROW, "&7Prev", List.of(), "open_commands", page - 1, null));
        if (start + perPage < cmds.size()) inv.setItem(50, item(Material.ARROW, "&7Next", List.of(), "open_commands", page + 1, null));
        inv.setItem(53, item(Material.BARRIER, "&cClose", List.of(), "close", 0, null));
        player.openInventory(inv);
    }

    private interface SlotFiller {
        ItemStack get(int slot, int index);
    }

    private void openPagedHeads(Player player, GuiType type, int page, int total, SlotFiller filler, String title) {
        int size = 54;
        Inventory inv = Bukkit.createInventory(new GuiHolder(type, page), size, LEGACY.deserialize("&8" + title));
        int perPage = 45;
        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= total) break;
            ItemStack it = filler.get(i, index);
            if (it != null) inv.setItem(i, it);
        }
        inv.setItem(45, item(Material.ARROW, "&7Back", List.of(), "open_main", 0, null));
        if (page > 0) inv.setItem(48, item(Material.ARROW, "&7Prev", List.of(), "page_prev", page - 1, null));
        if (start + perPage < total) inv.setItem(50, item(Material.ARROW, "&7Next", List.of(), "page_next", page + 1, null));
        inv.setItem(53, item(Material.BARRIER, "&cClose", List.of(), "close", 0, null));
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
                    player.sendMessage(LEGACY.deserialize("&cNo permission to unignore here."));
                    return;
                }
                if (target == null) return;
                UUID tid;
                try { tid = UUID.fromString(target); } catch (Exception e) { return; }
                UUID pending = pendingUnignore.get(player.getUniqueId());
                if (pending != null && pending.equals(tid)) {
                    pendingUnignore.remove(player.getUniqueId());
                    plugin.unignorePlayer(player, tid);
                    player.sendMessage(LEGACY.deserialize("&aUnignored &f" + resolveName(tid)));
                    openIgnore(player, page);
                } else {
                    pendingUnignore.put(player.getUniqueId(), tid);
                    player.sendMessage(LEGACY.deserialize("&eClick again to confirm unignore of &f" + resolveName(tid)));
                }
            }
            default -> {}
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
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
        try {
            meta.setOwningPlayer(op);
        } catch (Throwable ignored) {}
        meta.displayName(LEGACY.deserialize("&f" + (name != null ? name : uuid.toString().substring(0, 8))));
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
            if (n != null && !n.equals(uuid.toString())) return n;
        }
        if (plugin.getStatsManager() != null) {
            String n = plugin.getStatsManager().nameOf(uuid);
            if (n != null && !n.equals(uuid.toString())) return n;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
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
