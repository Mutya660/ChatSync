package chatsync;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Перевод + кликабельные ники. Головы только игрокам.
 * Discord/консоль всегда получают clean-текст (даже если event обнулён).
 */
public class DeathMessageTranslator implements Listener {

    private void purgeStalePending() {
        long cutoff = System.currentTimeMillis() - 10_000L;
        pendingAt.entrySet().removeIf(e -> {
            if (e.getValue() < cutoff) {
                pendingWithHeads.remove(e.getKey());
                pendingClean.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    private final ChatSync plugin;
    private final Map<String, String> translations;
    private final Map<UUID, Component> pendingWithHeads = new ConcurrentHashMap<>();
    private final Map<UUID, Component> pendingClean = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingAt = new ConcurrentHashMap<>();

    public DeathMessageTranslator(ChatSync plugin) {
        this.plugin = plugin;
        this.translations = loadTranslationsForLanguage(plugin);
    }

    private Map<String, String> loadTranslationsForLanguage(JavaPlugin plugin) {
        String lang = plugin.getConfig().getString("language", "en").toLowerCase();
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

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerDeathEarly(PlayerDeathEvent event) {
        purgeStalePending();
        Component deathMessage = event.deathMessage();
        if (deathMessage == null) {
            // Another plugin cleared it, or showDeathMessages is false.
            // Do not invent a message if the server intentionally hid deaths.
            return;
        }

        boolean clickable = plugin.isClickableEnabled("death");
        boolean translate = plugin.getConfig().getBoolean("death_messages.translate", true);
        boolean heads = plugin.isHeadsEnabled("death");
        Map<String, Player> targets = collectClickableTargets(event);

        Component body = null;

        if (translate && !translations.isEmpty() && deathMessage instanceof TranslatableComponent translatable) {
            String translatedText = translateTranslatable(translatable);
            if (translatedText != null) {
                body = clickable
                        ? buildClickableMessage(translatedText, targets)
                        : Component.text(translatedText);
            }
        }

        if (body == null && clickable) {
            Component updated = makeNamesClickable(deathMessage, targets);
            body = updated != null ? updated : deathMessage;
        }

        if (body == null) body = deathMessage;

        Component clean = plugin.stripObjectComponents(body);
        event.deathMessage(clean);
        UUID _id = event.getEntity().getUniqueId();
        pendingClean.put(_id, clean);
        pendingAt.put(_id, System.currentTimeMillis());

        Component withHeads = body;
        if (heads) {
            withHeads = prependHeads(body, targets);
        }
        pendingWithHeads.put(event.getEntity().getUniqueId(), withHeads);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeathLate(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        Component withHeads = pendingWithHeads.remove(id);
        Component clean = pendingClean.remove(id);
        pendingAt.remove(id);
        if (withHeads == null && clean == null) return;

        boolean heads = plugin.isHeadsEnabled("death");

        // Text without object-heads for console / Discord.
        Component forLog = clean != null ? clean
                : plugin.stripObjectComponents(withHeads);
        String plain = forLog != null ? plugin.plainComponent(forLog) : "";

        // What players see in-game.
        Component show = (heads && withHeads != null) ? withHeads : clean;
        if (show == null) show = forLog;

        // Always take ownership of the death line:
        // 1) clear event so Paper/DiscordSRV do not broadcast a second copy
        // 2) send ourselves (works for en + translated ru/etc.)
        event.deathMessage(null);
        if (show != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    p.sendMessage(show);
                } catch (Throwable ignored) {}
            }
        }

        if (!plain.isEmpty()) {
            plugin.logToConsolePublic("[Death] " + plain);
            plugin.relayGameMessageToDiscord(forLog);
        }
    }

    private Component prependHeads(Component message, Map<String, Player> targets) {
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

        List<String> names = new ArrayList<>(clickable.keySet());
        names.sort((a, b) -> Integer.compare(b.length(), a.length()));

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
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
                    builder.append(plugin.clickableName(matched, matched, p.getUniqueId(), p, "death"));
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
                        plugin.clickableName(plain, match.getName(), match.getUniqueId(), match, "death")));
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
            return plugin.clickableName(plain, match.getName(), match.getUniqueId(), match, "death");
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
