package chatsync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;

/**
 * Bridges Paper Adventure audience methods to Spigot/Arclight String API.
 * Arclight exposes Adventure classes when shaded, but CraftPlayer often has
 * no sendMessage(Component) / joinMessage(Component) — only legacy String.
 */
public final class AdventureBridge {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static Boolean componentSend;
    private static Boolean joinMessageComponent;
    private static Boolean quitMessageComponent;
    private static Boolean deathMessageComponent;
    private static Boolean deathMessageGetter;
    private static Boolean advancementMessageComponent;

    private AdventureBridge() {}

    public static String toLegacy(Component component) {
        if (component == null) return "";
        try {
            return LEGACY.serialize(component);
        } catch (Throwable t) {
            try {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(component);
            } catch (Throwable t2) {
                return component.toString();
            }
        }
    }

    public static void send(CommandSender sender, Component component) {
        if (sender == null || component == null) return;
        if (trySendComponent(sender, component)) return;
        try {
            sender.sendMessage(toLegacy(component));
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
            // other errors: fall back
            return false;
        }
    }

    public static void setJoinMessage(PlayerJoinEvent event, Component message) {
        if (event == null) return;
        if (Boolean.FALSE.equals(joinMessageComponent)) {
            event.setJoinMessage(message == null ? null : toLegacy(message));
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
            event.setQuitMessage(message == null ? null : toLegacy(message));
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
            return LEGACY.deserialize(s);
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
        try {
            Method m = player.getClass().getMethod("sendActionBar", Component.class);
            m.invoke(player, component);
        } catch (Throwable t) {
            try {
                player.sendMessage(toLegacy(component));
            } catch (Throwable ignored) {}
        }
    }
}
