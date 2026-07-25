package DahRealPanda.plugins.untitled1.texture;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keyed PNG cache backed by memory and disk (run/colonyweb-cache/textures).
 */
public final class PngCache {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ConcurrentHashMap<String, byte[]> memory = new ConcurrentHashMap<>();
    private final Path diskDir;

    public PngCache(Path baseDir) {
        this.diskDir = baseDir.resolve("textures");
        try {
            Files.createDirectories(diskDir);
        } catch (IOException e) {
            LOGGER.warn("[ColonyWeb] could not create texture cache dir {}", diskDir, e);
        }
    }

    /** Look up cached bytes (memory first, then disk). */
    public byte[] get(String key) {
        byte[] mem = memory.get(key);
        if (mem != null) {
            return mem;
        }
        Path file = diskFile(key);
        try {
            if (Files.isRegularFile(file)) {
                byte[] bytes = Files.readAllBytes(file);
                memory.put(key, bytes);
                return bytes;
            }
        } catch (IOException e) {
            LOGGER.debug("[ColonyWeb] failed reading cached texture {}", key, e);
        }
        return null;
    }

    /** Store bytes in memory and on disk. */
    public void put(String key, byte[] bytes) {
        if (bytes == null) {
            return;
        }
        memory.put(key, bytes);
        try {
            Files.write(diskFile(key), bytes);
        } catch (IOException e) {
            LOGGER.debug("[ColonyWeb] failed writing cached texture {}", key, e);
        }
    }

    private Path diskFile(String key) {
        return diskDir.resolve(safeName(key) + ".png");
    }

    private static String safeName(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
