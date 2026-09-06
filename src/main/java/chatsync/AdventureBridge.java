package chatsync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;

/**
 * Bridges Paper Adventure audience methods to Spigot/Arclight String API.
 * <p>
 * Arclight may load shaded Adventure classes but still lack
 * {@code Player.sendMessage(Component)} / {@code joinMessage(Component)}.
 * Falling back must use {@code §} section codes — {@code &} codes are shown
 * literally by the vanilla client.
 */
public final class AdventureBridge {

    /** Deserialize formats written with {@code &} in config/lang. */
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /** Serialize for Bukkit {@code sendMessage(String)} (§ codes). */
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private static Boolean componentSend;
    private static Boolean joinMessageComponent;
    private static Boolean quitMessageComponent;
    private static Boolean deathMessageComponent;
    private static Boolean deathMessageGetter;
    private static Boolean advancementMessageComponent;

    static {
        // Hybrid servers: never call Component audience methods — they throw NoSuchMethodError.
        if (ServerCompat.isArclight()) {
            componentSend = false;
            joinMessageComponent = false;
            quitMessageComponent = false;
            deathMessageComponent = false;
            advancementMessageComponent = false;
        }
    }

    private AdventureBridge() {}

    /**
     * Component → legacy string with section signs (§) for Bukkit String API.
     * Click/hover are lost on the String path (Arclight limitation).
     */
    public static String toLegacy(Component component) {
        if (component == null) return "";
        try {
            String section = SECTION.serialize(component);
            if (section != null && !section.isEmpty()) return section;
        } catch (Throwable ignored) {}
        try {
            String amp = AMPERSAND.serialize(component);
            if (amp != null && !amp.isEmpty()) {
                return ChatColor.translateAlternateColorCodes('&', amp);
            }
        } catch (Throwable ignored) {}
        try {
            return PlainTextComponentSerializer.plainText().serialize(component);
        } catch (Throwable t) {
            return "";
        }
    }

    public static void send(CommandSender sender, Component component) {
        if (sender == null || component == null) return;
        if (trySendComponent(sender, component)) return;
        try {
            sender.sendMessage(toLegacy(component));
        } catch (Throwable ignored) {}
    }

    /** Legacy string path. Accepts {@code &} or {@code §} color codes. */
    public static void send(CommandSender sender, String text) {
        if (sender == null || text == null) return;
        try {
            if (text.indexOf('&') >= 0) {
                text = ChatColor.translateAlternateColorCodes('&', text);
            }
            sender.sendMessage(text);
        } catch (Throwable ignored) {}
    }

    private static boolean trySendComponent(CommandSender sender, Component component) {
        if (Boolean.FALSE.equals(componentSend)) return false;
        try {
            Method m = sender.getClass().getMethod("sendMessage", Component.class);
            m.invoke(sender, component);
            componentSend = true;
            return true;
        } catch (NoSuchMethodException e) {
            componentSend = false;
            return false;
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            if (c instanceof NoSuchMethodError || c instanceof AbstractMethodError) {
                componentSend = false;
                return false;
            }
            return false;
        }
    }

    public static void setJoinMessage(PlayerJoinEvent event, Component message) {
        if (event == null) return;
        if (Boolean.FALSE.equals(joinMessageComponent)) {
            try {
                event.setJoinMessage(message == null ? null : toLegacy(message));
            } catch (Throwable ignored) {}
            return;
        }
        try {
            Method m = event.getClass().getMethod("joinMessage", Component.class);
            m.invoke(event, message);
            joinMessageComponent = true;
        } catch (Throwable t) {
            joinMessageComponent = false;
            try {
                event.setJoinMessage(message == null ? null : toLegacy(message));
            } catch (Throwable ignored) {}
        }
    }

    public static void setQuitMessage(PlayerQuitEvent event, Component message) {
        if (event == null) return;
        if (Boolean.FALSE.equals(quitMessageComponent)) {
            try {
                event.setQuitMessage(message == null ? null : toLegacy(message));
            } catch (Throwable ignored) {}
            return;
        }
        try {
            Method m = event.getClass().getMethod("quitMessage", Component.class);
            m.invoke(event, message);
            quitMessageComponent = true;
        } catch (Throwable t) {
            quitMessageComponent = false;
            try {
                event.setQuitMessage(message == null ? null : toLegacy(message));
            } catch (Throwable ignored) {}
        }
    }

    public static void setDeathMessage(PlayerDeathEvent event, Component message) {
        if (event == null) return;
        if (Boolean.FALSE.equals(deathMessageComponent)) {
            try {
                event.setDeathMessage(message == null ? null : toLegacy(message));
            } catch (Throwable ignored) {}
            return;
        }
        try {
            Method m = event.getClass().getMethod("deathMessage", Component.class);
            m.invoke(event, message);
            deathMessageComponent = true;
        } catch (Throwable t) {
            deathMessageComponent = false;
            try {
                event.setDeathMessage(message == null ? null : toLegacy(message));
            } catch (Throwable ignored) {}
        }
    }

    public static Component getDeathMessage(PlayerDeathEvent event) {
        if (event == null) return null;
        if (!Boolean.FALSE.equals(deathMessageGetter)) {
            try {
                Method m = event.getClass().getMethod("deathMessage");
                Object r = m.invoke(event);
                if (r instanceof Component c) {
                    deathMessageGetter = true;
                    return c;
                }
            } catch (Throwable t) {
                deathMessageGetter = false;
            }
        }
        try {
            String s = event.getDeathMessage();
            if (s == null) return null;
            return AMPERSAND.deserialize(s.replace('§', '&'));
        } catch (Throwable t) {
            return null;
        }
    }

    public static void setAdvancementMessage(PlayerAdvancementDoneEvent event, Component message) {
        if (event == null) return;
        if (Boolean.FALSE.equals(advancementMessageComponent)) return;
        try {
            Method m = event.getClass().getMethod("message", Component.class);
            m.invoke(event, message);
            advancementMessageComponent = true;
        } catch (Throwable t) {
            advancementMessageComponent = false;
        }
    }

    public static Component getAdvancementMessage(PlayerAdvancementDoneEvent event) {
        if (event == null) return null;
        try {
            Method m = event.getClass().getMethod("message");
            Object r = m.invoke(event);
            if (r instanceof Component c) return c;
        } catch (Throwable ignored) {}
        return null;
    }

    public static void actionBar(Player player, Component component) {
        if (player == null || component == null) return;
        if (!Boolean.FALSE.equals(componentSend)) {
            try {
                Method m = player.getClass().getMethod("sendActionBar", Component.class);
                m.invoke(player, component);
                return;
            } catch (Throwable t) {
                componentSend = false;
            }
        }
        send(player, component);
    }
}
