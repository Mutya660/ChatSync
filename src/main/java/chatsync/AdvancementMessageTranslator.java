package chatsync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Делает ник игрока кликабельным в анонсе достижения.
 * Текст достижения (название) локализуется клиентом / датапаком сервера —
 * ChatSync его не подменяет. Hover — единый (с playtime, если включено).
 * Головы в анонсах достижений не используются.
 */
public class AdvancementMessageTranslator implements Listener {

    private final ChatSync plugin;

    public AdvancementMessageTranslator(ChatSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.getConfig().getBoolean("toggles.clickable_advancement_name", true)) return;

        // Скрытые достижения (рецепты и т.п.) без display — без анонса
        if (event.getAdvancement().getDisplay() == null) return;

        Component message = event.message();
        if (message == null) return;

        Player player = event.getPlayer();
        Component updated = makeNameClickable(message, player);
        if (updated == null) return;

        // Без голов — один текст для Minecraft и DiscordSRV
        event.message(updated);
    }

    private Component makeNameClickable(Component component, Player player) {
        if (component instanceof TranslatableComponent translatable) {
            return makeTranslatableClickable(translatable, player);
        }
        return tryReplaceInChildren(component, player);
    }

    private Component makeTranslatableClickable(TranslatableComponent translatable, Player player) {
        List<TranslationArgument> args = translatable.arguments();
        if (args.isEmpty()) return null;

        String playerName = player.getName();
        List<TranslationArgument> newArgs = new ArrayList<>(args.size());
        boolean changed = false;

        for (TranslationArgument arg : args) {
            Component argComponent = arg.asComponent();
            String plain = PlainTextComponentSerializer.plainText().serialize(argComponent);

            if (!changed && plain.equals(playerName)) {
                newArgs.add(TranslationArgument.component(
                        plugin.clickableName(plain, playerName, player.getUniqueId(), player)));
                changed = true;
            } else {
                Component nested = makeNameClickable(argComponent, player);
                if (nested != null) {
                    newArgs.add(TranslationArgument.component(nested));
                    changed = true;
                } else {
                    newArgs.add(arg);
                }
            }
        }

        if (!changed) return null;
        return Component.translatable()
                .key(translatable.key())
                .arguments(newArgs)
                .style(translatable.style())
                .build();
    }

    private Component tryReplaceInChildren(Component component, Player player) {
        List<Component> children = component.children();
        if (children.isEmpty()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(component);
            if (plain.equals(player.getName())) {
                return plugin.clickableName(plain, player.getName(), player.getUniqueId(), player);
            }
            return null;
        }
        List<Component> newChildren = new ArrayList<>(children.size());
        boolean changed = false;
        for (Component child : children) {
            Component updated = makeNameClickable(child, player);
            if (updated != null) {
                newChildren.add(updated);
                changed = true;
            } else {
                newChildren.add(child);
            }
        }
        return changed ? component.children(newChildren) : null;
    }
}
