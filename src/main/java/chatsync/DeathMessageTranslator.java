package chatsync;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Переводит ванильные сообщения о смерти на русский, включая вложенные
 * имена мобов/предметов, используя официальные строки Mojang из
 * death_messages_ru.json и entity_names_ru.json. Также делает ник жертвы
 * (и убийцы, если это игрок) кликабельным для быстрого /msg.
 *
 * Translates vanilla death messages to Russian, including nested mob/item
 * names, using official Mojang strings from death_messages_ru.json and
 * entity_names_ru.json. Also makes the victim's name (and the killer's,
 * if a player) clickable for a quick /msg.
 */
public class DeathMessageTranslator implements Listener {

    private final ChatSync plugin;
    private final Map<String, String> translations;

    public DeathMessageTranslator(ChatSync plugin) {
        this.plugin = plugin;
        this.translations = loadAllTranslations(plugin);
    }

    private Map<String, String> loadAllTranslations(JavaPlugin plugin) {
        Map<String, String> combined = new HashMap<>();
        combined.putAll(loadJsonResource(plugin, "death_messages_ru.json"));
        combined.putAll(loadJsonResource(plugin, "entity_names_ru.json"));
        return combined;
    }

    private Map<String, String> loadJsonResource(JavaPlugin plugin, String fileName) {
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) {
                plugin.getLogger().warning(fileName + " не найден в resources!");
                return Map.of();
            }
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
            return loaded != null ? loaded : Map.of();
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось загрузить " + fileName + ": " + e.getMessage());
            return Map.of();
        }
    }

    // MONITOR — чтобы сработать после всех остальных плагинов, которые могут менять сообщение
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("death_messages.translate_to_russian", true)) return;

        Component deathMessage = event.deathMessage();
        if (deathMessage == null) return;

        if (!(deathMessage instanceof TranslatableComponent translatable)) return;
        String translatedText = translateTranslatable(translatable);
        if (translatedText == null) return;

        boolean clickable = plugin.getConfig().getBoolean("toggles.clickable_death_name", true);
        Component finalMessage = clickable
                ? buildClickableMessage(translatedText, collectClickableTargets(event))
                : Component.text(translatedText);

        event.deathMessage(finalMessage);
    }

    /** Собирает игроков, чьи ники встречаются в сообщении о смерти и должны стать кликабельными. */
    private Map<String, Player> collectClickableTargets(PlayerDeathEvent event) {
        Map<String, Player> map = new LinkedHashMap<>();
        Player victim = event.getEntity();
        map.put(victim.getName(), victim);

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) map.put(killer.getName(), killer);
        return map;
    }

    /** Оборачивает вхождения имён игроков в тексте в кликабельные компоненты (клик = /msg <игрок>). */
    private Component buildClickableMessage(String text, Map<String, Player> clickable) {
        if (clickable.isEmpty()) return Component.text(text);

        List<String> names = new ArrayList<>(clickable.keySet());
        names.sort((a, b) -> b.length() - a.length()); // сначала более длинные имена, чтобы избежать частичных совпадений

        net.kyori.adventure.text.TextComponent.@org.jetbrains.annotations.NotNull Builder builder = Component.text();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            String matched = null;
            for (String name : names) {
                if (text.regionMatches(i, name, 0, name.length())) {
                    matched = name;
                    break;
                }
            }
            if (matched != null) {
                if (plain.length() > 0) {
                    builder.append(Component.text(plain.toString()));
                    plain.setLength(0);
                }
                Player p = clickable.get(matched);
                String hover = plugin.t(p, "messages.join_hover").replace("%player%", p.getName());
                builder.append(Component.text(matched)
                        .clickEvent(ClickEvent.suggestCommand("/msg " + p.getName() + " "))
                        .hoverEvent(HoverEvent.showText(plugin.color(hover))));
                i += matched.length();
            } else {
                plain.append(text.charAt(i));
                i++;
            }
        }
        if (plain.length() > 0) builder.append(Component.text(plain.toString()));
        return builder.build();
    }

    /**
     * Переводит любой Component в обычную строку.
     * Если это TranslatableComponent с известным ключом — переводит рекурсивно
     * (включая вложенные имена мобов/предметов в его аргументах).
     * Иначе — просто извлекает текст как есть (например, ник игрока).
     */
    private String resolveArgumentText(Component component) {
        if (component instanceof TranslatableComponent nested) {
            String translated = translateTranslatable(nested);
            if (translated != null) return translated;
        }
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Возвращает переведённую строку для TranslatableComponent, либо null если ключ не найден. */
    private String translateTranslatable(TranslatableComponent translatable) {
        String key = translatable.key();
        String template = translations.get(key);
        if (template == null) return null;

        Object[] args = translatable.args().stream()
                .map(arg -> (Object) resolveArgumentText(arg))
                .toArray();

        try {
            return String.format(template, args);
        } catch (Exception e) {
            return null;
        }
    }
}