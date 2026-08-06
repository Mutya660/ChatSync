package chatsync;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Асинхронная запись истории чата в файлы logs/chat-YYYY-MM-DD.log.
 * Сообщения складываются в очередь и сбрасываются на диск в отдельном потоке,
 * чтобы не блокировать основной поток сервера.
 *
 * Asynchronous chat history logging into logs/chat-YYYY-MM-DD.log files.
 * Messages are queued and flushed to disk on a separate thread so the
 * main server thread is never blocked by file I/O.
 */
public class ChatLogger {

    private final JavaPlugin plugin;
    private final File logsFolder;
    private final boolean async;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private boolean flushScheduled = false;

    public ChatLogger(JavaPlugin plugin, String folderName, boolean async) {
        this.plugin = plugin;
        this.logsFolder = new File(plugin.getDataFolder(), folderName == null || folderName.isBlank() ? "logs" : folderName);
        this.async = async;
        if (!logsFolder.exists()) logsFolder.mkdirs();
    }

    public void log(String line) {
        String timestamped = "[" + LocalDateTime.now().format(TIME_FMT) + "] " + line;
        if (async) {
            queue.add(timestamped);
            scheduleFlush();
        } else {
            writeDirect(timestamped);
        }
    }

    private synchronized void scheduleFlush() {
        if (flushScheduled) return;
        flushScheduled = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            flush();
            boolean more;
            synchronized (this) {
                flushScheduled = false;
                more = !queue.isEmpty();
            }
            if (more) scheduleFlush();
        });
    }

    private void flush() {
        if (queue.isEmpty()) return;
        try (FileWriter fw = new FileWriter(currentFile(), true)) {
            String line;
            while ((line = queue.poll()) != null) {
                fw.write(line);
                fw.write(System.lineSeparator());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write chat log: " + e.getMessage());
        }
    }

    private void writeDirect(String line) {
        try (FileWriter fw = new FileWriter(currentFile(), true)) {
            fw.write(line);
            fw.write(System.lineSeparator());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write chat log: " + e.getMessage());
        }
    }

    private File currentFile() {
        if (!logsFolder.exists()) logsFolder.mkdirs();
        return new File(logsFolder, "chat-" + LocalDate.now().format(DATE_FMT) + ".log");
    }

    /** Принудительно сбрасывает всё, что осталось в очереди (например, при выключении плагина). */
    public void flushNow() {
        flush();
    }
}
