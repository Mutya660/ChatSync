package chatsync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
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
 * Делает ник игрока кликабельным (клик = быстрый /msg) в стандартном
 * сообщении о получении достижения. Сам перевод не трогаем — TranslatableComponent
 * и так локализуется на клиенте под его язык (Paper даёт message() именно как Component).
 *
 * Makes the player name clickable (click = quick /msg) in the vanilla
 * advancement-announcement message. Translation itself is untouched — the
 * TranslatableComponent is already client-side localized (Paper exposes
 * message() as a Component for exactly this kind of tweak).
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
        if (message == null) return; // достижение не анонсируется в чат (рецепт/корневое)

        Component updated = makeNameClickable(message, event.getPlayer());
        if (updated != null) event.message(updated);
    }

    private Component makeNameClickable(Component component, Player player) {
        if (!(component instanceof TranslatableComponent translatable)) return null;

        List<Component> args = translatable.args();
        List<Component> newArgs = new ArrayList<>(args.size());
        boolean changed = false;

        for (Component arg : args) {
            if (!changed && PlainTextComponentSerializer.plainText().serialize(arg).equals(player.getName())) {
                String hover = plugin.t(player, "messages.join_hover").replace("%player%", player.getName());
                newArgs.add(arg
                        .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "))
                        .hoverEvent(HoverEvent.showText(plugin.color(hover))));
                changed = true;
            } else {
                newArgs.add(arg);
            }
        }

        if (!changed) return null;
        return Component.translatable(translatable.key(), newArgs).style(translatable.style());
    }
}