package chatsync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Делает ник игрока кликабельным (клик = /msg) в сообщении о получении достижения.
 * Перевод не трогаем — TranslatableComponent локализуется на клиенте.
 *
 * Adventure 4.15+: arguments() возвращает List&lt;TranslationArgument&gt;, не Component.
 */
public class AdvancementMessageTranslator implements Listener {

    private final ChatSync plugin;

    public AdvancementMessageTranslator(ChatSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.getConfig().getBoolean("toggles.clickable_advancement_name", true)) return;

        Component message = event.message();
        if (message == null) return; // достижение не анонсируется (рецепт / корневое)

        Component updated = makeNameClickable(message, event.getPlayer());
        if (updated != null) event.message(updated);
    }

    private Component makeNameClickable(Component component, Player player) {
        if (!(component instanceof TranslatableComponent translatable)) {
            return tryReplaceInChildren(component, player);
        }

        List<TranslationArgument> args = translatable.arguments();
        List<TranslationArgument> newArgs = new ArrayList<>(args.size());
        boolean changed = false;
        String playerName = player.getName();

        for (TranslationArgument arg : args) {
            Component argComponent = arg.asComponent();
            String plain = PlainTextComponentSerializer.plainText().serialize(argComponent);

            if (!changed && plain.equals(playerName)) {
                String hover = plugin.t(player, "messages.join_hover").replace("%player%", playerName);
                Component clickable = argComponent
                        .clickEvent(ClickEvent.suggestCommand("/msg " + playerName + " "))
                        .hoverEvent(HoverEvent.showText(plugin.color(hover)));
                newArgs.add(TranslationArgument.component(clickable));
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

    /** Рекурсивный fallback для не-translatable сообщений. */
    private Component tryReplaceInChildren(Component component, Player player) {
        List<Component> children = component.children();
        if (children.isEmpty()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(component);
            if (plain.equals(player.getName())) {
                String hover = plugin.t(player, "messages.join_hover").replace("%player%", player.getName());
                return component
                        .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "))
                        .hoverEvent(HoverEvent.showText(plugin.color(hover)));
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
