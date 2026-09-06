package chatsync;

import org.bukkit.Bukkit;

/**
 * Detects the runtime server platform so ChatSync can adapt:
 * Paper / Purpur (full API), Arclight (Spigot bridge + mods), plain Spigot/Bukkit.
 */
public final class ServerCompat {

    public enum Platform {
        PAPER,
        ARCLIGHT,
        SPIGOT,
        UNKNOWN
    }

    private static final Platform PLATFORM;
    private static final boolean HAS_PAPER_ASYNC_CHAT;
    private static final boolean HAS_ADVENTURE_AUDIENCE;
    private static final String SERVER_NAME;
    private static final String SERVER_VERSION;

    static {
        String name = "unknown";
        String ver = "";
        try {
            name = Bukkit.getName() != null ? Bukkit.getName() : "unknown";
        } catch (Throwable ignored) {}
        try {
            ver = Bukkit.getVersion() != null ? Bukkit.getVersion() : "";
        } catch (Throwable ignored) {}
        SERVER_NAME = name;
        SERVER_VERSION = ver;

        String n = (name + " " + ver).toLowerCase();
        boolean arclight = n.contains("arclight")
                || classExists("io.izzel.arclight.common.ArclightConstants")
                || classExists("io.izzel.arclight.boot.AbstractBootstrap")
                || classExists("io.izzel.arclight.api.Arclight");
        boolean paperBrand = n.contains("paper") || n.contains("purpur") || n.contains("leaf")
                || n.contains("folia") || n.contains("pufferfish");

        HAS_PAPER_ASYNC_CHAT = classExists("io.papermc.paper.event.player.AsyncChatEvent");
        HAS_ADVENTURE_AUDIENCE = classExists("net.kyori.adventure.audience.Audience");

        if (arclight) {
            PLATFORM = Platform.ARCLIGHT;
        } else if (paperBrand || HAS_PAPER_ASYNC_CHAT) {
            PLATFORM = Platform.PAPER;
        } else if (n.contains("spigot") || n.contains("craftbukkit") || n.contains("bukkit")) {
            PLATFORM = Platform.SPIGOT;
        } else {
            PLATFORM = Platform.UNKNOWN;
        }
    }

    private ServerCompat() {}

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Platform platform() { return PLATFORM; }

    public static boolean isArclight() { return PLATFORM == Platform.ARCLIGHT; }

    public static boolean isPaperFamily() { return PLATFORM == Platform.PAPER; }

    /** Prefer Paper AsyncChatEvent when the class is present (Paper or Arclight builds that ship it). */
    public static boolean hasPaperAsyncChat() { return HAS_PAPER_ASYNC_CHAT; }

    public static boolean hasAdventure() { return HAS_ADVENTURE_AUDIENCE; }

    public static String serverName() { return SERVER_NAME; }

    public static String serverVersion() { return SERVER_VERSION; }

    public static String describe() {
        return PLATFORM.name() + " (" + SERVER_NAME + " / " + SERVER_VERSION + ")"
                + " paper-async-chat=" + HAS_PAPER_ASYNC_CHAT;
    }
}
