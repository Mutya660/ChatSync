package chatsync;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Переводит ванильные сообщения о смерти на русский, включая вложенные
 * имена мобов/предметов (например "Zombie" внутри "%1$s был убит %2$s"),
 * используя официальные строки Mojang из death_messages_ru.json и entity_names_ru.json.
 *
 * Оба файла должны лежать в src/main/resources/
 */
public class DeathMessageTranslator implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, String> translations;

    public DeathMessageTranslator(JavaPlugin plugin) {
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

        Component translated = translateRoot(deathMessage);
        if (translated != null) {
            event.deathMessage(translated);
        }
    }

    /** Переводит корневой компонент сообщения о смерти. Возвращает null, если перевод невозможен. */
    private Component translateRoot(Component component) {
        if (!(component instanceof TranslatableComponent translatable)) {
            return null;
        }
        String translatedText = translateTranslatable(translatable);
        return translatedText != null ? Component.text(translatedText) : null;
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
            // ключа нет в таблице (редкий моб/предмет) — берём английский текст как запасной вариант
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
            // несовпадение количества %s — не ломаем сообщение
            return null;
        }
    }
}
