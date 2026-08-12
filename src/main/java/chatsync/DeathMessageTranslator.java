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
 * Перевод сообщений о смерти по language из config.yml + кликабельные ники с hover.
 * Для language=ru загружаются death_messages_ru.json / entity_names_ru.json.
 * Для en (и прочих без пакета) оставляем ванильный текст, только кликабельность.
 */
public class DeathMessageTranslator implements Listener {

    private final ChatSync plugin;
    private final Map<String, String> translations;

    public DeathMessageTranslator(ChatSync plugin) {
        this.plugin = plugin;
        this.translations = loadTranslationsForLanguage(plugin);
    }

    private Map<String, String> loadTranslationsForLanguage(JavaPlugin plugin) {
        String lang = plugin.getConfig().getString("language", "en").toLowerCase();
        // Обратная совместимость: translate_to_russian: true при language en → всё равно ru, если явно включено
        boolean forceRu = plugin.getConfig().getBoolean("death_messages.translate_to_russian", false)
                && !plugin.getConfig().contains("death_messages.translate");
        boolean translate = plugin.getConfig().getBoolean("death_messages.translate", true);

        Map<String, String> combined = new HashMap<>();
        if (!translate) return combined;

        if (forceRu || "ru".equals(lang)) {
            combined.putAll(loadJsonResource(plugin, "death_messages_ru.json"));
            combined.putAll(loadJsonResource(plugin, "entity_names_ru.json"));
            if (!combined.isEmpty()) {
                plugin.getLogger().info("Death messages: loaded Russian translation pack (" + combined.size() + " keys).");
            }
        } else {
            // en / de / fr — ванильные сообщения Minecraft (клиент сам локализует translatable),
            // плагин только делает ники кликабельными.
            plugin.getLogger().info("Death messages: language=" + lang + " — vanilla text, clickable names only.");
        }
        return combined;
    }

    private Map<String, String> loadJsonResource(JavaPlugin plugin, String fileName) {
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) {
                plugin.getLogger().warning(fileName + " not found in resources!");
                return Map.of();
            }
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
            return loaded != null ? loaded : Map.of();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load " + fileName + ": " + e.getMessage());
            return Map.of();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Component deathMessage = event.deathMessage();
        if (deathMessage == null) return;

        boolean clickable = plugin.getConfig().getBoolean("toggles.clickable_death_name", true);
        boolean translate = plugin.getConfig().getBoolean("death_messages.translate", true);

        Component forPlayers = null;

        // Перевод, если есть пакет для текущего language
        if (translate && !translations.isEmpty() && deathMessage instanceof TranslatableComponent translatable) {
            String translatedText = translateTranslatable(translatable);
            if (translatedText != null) {
                forPlayers = clickable
                        ? buildClickableMessage(translatedText, collectClickableTargets(event))
                        : Component.text(translatedText);
            }
        }

        // Без перевода — кликабельные ники + головы
        if (forPlayers == null && clickable) {
            Map<String, Player> targets = collectClickableTargets(event);
            Component updated = makeNamesClickable(deathMessage, targets);
            if (updated == null) updated = deathMessage;
            forPlayers = prependHeads(updated, targets);
        }

        if (forPlayers == null) return;

        // DiscordSRV serializes object-components as "[name head]" — keep heads in-game only
        Component forDiscord = stripObjectComponents(forPlayers);
        event.deathMessage(null);
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            p.sendMessage(forPlayers);
        }
        plugin.relayGameMessageToDiscord(forDiscord);
    }

    /** Remove Adventure object components (player heads) so DiscordSRV plain text is clean. */
    private Component stripObjectComponents(Component component) {
        if (component == null) return Component.empty();
        try {
            String className = component.getClass().getName();
            if (className.contains("ObjectComponent") || className.contains("object")) {
                return Component.empty();
            }
        } catch (Throwable ignored) {}

        java.util.List<Component> children = component.children();
        if (children == null || children.isEmpty()) {
            return component;
        }
        java.util.List<Component> cleaned = new java.util.ArrayList<>(children.size());
        boolean changed = false;
        for (Component child : children) {
            Component c = stripObjectComponents(child);
            if (c != child) changed = true;
            // skip pure empty object placeholders
            if (c.equals(Component.empty()) && child != c) {
                changed = true;
                continue;
            }
            cleaned.add(c);
        }
        return changed ? component.children(cleaned) : component;
    }

    private Component prependHeads(Component message, Map<String, Player> targets) {
        if (!plugin.getConfig().getBoolean("chat.heads.enabled", true)) return message;
        if (!plugin.getConfig().getBoolean("chat.heads.force_first", true)
                && !plugin.getConfig().getBoolean("clickable_names.heads_in_commands", true)) {
            return message;
        }
        if (targets == null || targets.isEmpty()) return message;
        net.kyori.adventure.text.TextComponent.Builder b = Component.text();
        for (Player p : targets.values()) {
            if (p != null) b.append(plugin.buildHeadComponent(p));
        }
        return b.append(message).build();
    }


    private Map<String, Player> collectClickableTargets(PlayerDeathEvent event) {
        Map<String, Player> map = new LinkedHashMap<>();
        Player victim = event.getEntity();
        map.put(victim.getName(), victim);
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) map.put(killer.getName(), killer);
        return map;
    }

    private Component buildClickableMessage(String text, Map<String, Player> clickable) {
        if (clickable.isEmpty()) return Component.text(text);

        boolean heads = plugin.getConfig().getBoolean("chat.heads.enabled", true)
                && plugin.getConfig().getBoolean("chat.heads.force_first", true);

        List<String> names = new ArrayList<>(clickable.keySet());
        names.sort((a, b) -> Integer.compare(b.length(), a.length()));

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        // Heads in order: victim first, then killer (LinkedHashMap insertion order)
        if (heads) {
            for (Player p : clickable.values()) {
                if (p != null) builder.append(plugin.buildHeadComponent(p));
            }
        }

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
                if (p != null) {
                    builder.append(plugin.clickableName(matched, matched, p.getUniqueId(), p));
                } else {
                    builder.append(Component.text(matched));
                }
                i += matched.length();
            } else {
                plain.append(text.charAt(i));
                i++;
            }
        }
        if (plain.length() > 0) {
            builder.append(Component.text(plain.toString()));
        }
        return builder.build();
    }


    /** Делает ники кликабельными внутри ванильного (в т.ч. translatable) компонента. */
    private Component makeNamesClickable(Component component, Map<String, Player> targets) {
        if (component instanceof TranslatableComponent translatable) {
            return makeTranslatableClickable(translatable, targets);
        }
        return tryReplaceInChildren(component, targets);
    }

    private Component makeTranslatableClickable(TranslatableComponent translatable, Map<String, Player> targets) {
        List<net.kyori.adventure.text.TranslationArgument> args = translatable.arguments();
        if (args.isEmpty()) return null;

        List<net.kyori.adventure.text.TranslationArgument> newArgs = new ArrayList<>(args.size());
        boolean changed = false;

        for (net.kyori.adventure.text.TranslationArgument arg : args) {
            Component argComponent = arg.asComponent();
            String plain = PlainTextComponentSerializer.plainText().serialize(argComponent);
            Player match = targets.get(plain);
            if (match != null) {
                newArgs.add(net.kyori.adventure.text.TranslationArgument.component(
                        plugin.clickableName(plain, match.getName(), match.getUniqueId(), match)));
                changed = true;
            } else {
                Component nested = makeNamesClickable(argComponent, targets);
                if (nested != null) {
                    newArgs.add(net.kyori.adventure.text.TranslationArgument.component(nested));
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

    private Component tryReplaceInChildren(Component component, Map<String, Player> targets) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        Player match = targets.get(plain);
        if (match != null && component.children().isEmpty()) {
            return plugin.clickableName(plain, match.getName(), match.getUniqueId(), match);
        }

        List<Component> children = component.children();
        if (children.isEmpty()) return null;

        List<Component> newChildren = new ArrayList<>(children.size());
        boolean changed = false;
        for (Component child : children) {
            Component updated = makeNamesClickable(child, targets);
            if (updated != null) {
                newChildren.add(updated);
                changed = true;
            } else {
                newChildren.add(child);
            }
        }
        return changed ? component.children(newChildren) : null;
    }

    private String resolveArgumentText(Component component) {
        if (component instanceof TranslatableComponent nested) {
            String translated = translateTranslatable(nested);
            if (translated != null) return translated;
        }
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

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
