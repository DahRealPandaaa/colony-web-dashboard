package DahRealPanda.plugins.colonyweb.texture;

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

    /**
     * Bumped whenever the renderer starts drawing icons differently.
     *
     * <p>Cached PNGs are keyed only by item, so an upgrade that changes how an icon looks would
     * otherwise keep serving the old one forever. Stamping the directory lets the mod discard
     * its own stale renders on first start instead of asking anyone to delete a folder.</p>
     *
     * <p>The browser caches icons for a week too, so {@code RENDER_VERSION} in
     * {@code webroot/js/api.js} must be bumped alongside this — clearing only the server's copy
     * still leaves every already-loaded page showing the old one.</p>
     */
    private static final String RENDER_VERSION = "4";

    private static final String VERSION_FILE = ".render-version";

    private final ConcurrentHashMap<String, byte[]> memory = new ConcurrentHashMap<>();
    private final Path diskDir;

    public PngCache(Path baseDir) {
        this.diskDir = baseDir.resolve("textures");
        try {
            Files.createDirectories(diskDir);
            discardStaleRenders();
        } catch (IOException e) {
            LOGGER.warn("[ColonyWeb] could not create texture cache dir {}", diskDir, e);
        }
    }

    /** Drop every cached PNG when they were produced by an older renderer. */
    private void discardStaleRenders() {
        Path stamp = diskDir.resolve(VERSION_FILE);
        try {
            if (Files.isRegularFile(stamp) && RENDER_VERSION.equals(Files.readString(stamp).trim())) {
                return;
            }
            long dropped;
            try (var entries = Files.list(diskDir)) {
                dropped = entries.filter(p -> p.getFileName().toString().endsWith(".png"))
                        .peek(PngCache::deleteQuietly)
                        .count();
            }
            Files.writeString(stamp, RENDER_VERSION);
            if (dropped > 0) {
                LOGGER.info("[ColonyWeb] renderer updated — discarded {} cached icon(s), they will re-render", dropped);
            }
        } catch (IOException e) {
            // A stale icon is a cosmetic problem; failing to start over one is not worth it.
            LOGGER.warn("[ColonyWeb] could not refresh the texture cache in {}", diskDir, e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.debug("[ColonyWeb] failed deleting stale cached texture {}", file, e);
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
