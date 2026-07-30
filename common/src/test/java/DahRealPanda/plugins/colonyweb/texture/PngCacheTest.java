package DahRealPanda.plugins.colonyweb.texture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-level icon cache.
 *
 * <p>Its keys are item ids, which are attacker-influenced in the sense that they come from
 * whatever mods are installed and contain characters no file system accepts, so the mapping
 * from key to file name is the part worth pinning down.</p>
 */
class PngCacheTest {

    @TempDir
    Path dir;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    @Nested
    @DisplayName("storing and reading")
    class RoundTrip {

        @Test
        @DisplayName("what was put in comes back out")
        void putThenGet() {
            PngCache cache = new PngCache(dir);

            cache.put("minecraft:stone", PNG);

            assertArrayEquals(PNG, cache.get("minecraft:stone"));
        }

        @Test
        @DisplayName("a key that was never stored is null")
        void missIsNull() {
            assertNull(new PngCache(dir).get("minecraft:never_rendered"));
        }

        @Test
        @DisplayName("a cached icon survives a restart by way of the disk copy")
        void survivesRestart() {
            new PngCache(dir).put("minecraft:stone", PNG);

            assertArrayEquals(PNG, new PngCache(dir).get("minecraft:stone"));
        }

        @Test
        @DisplayName("storing null is ignored rather than caching a failure")
        void nullIsIgnored() {
            PngCache cache = new PngCache(dir);

            cache.put("minecraft:stone", null);

            assertNull(cache.get("minecraft:stone"));
        }

        @Test
        @DisplayName("re-rendering an icon overwrites the old bytes")
        void overwrites() {
            PngCache cache = new PngCache(dir);
            byte[] updated = {9, 9, 9};

            cache.put("minecraft:stone", PNG);
            cache.put("minecraft:stone", updated);

            assertArrayEquals(updated, cache.get("minecraft:stone"));
            assertArrayEquals(updated, new PngCache(dir).get("minecraft:stone"));
        }

        @Test
        @DisplayName("an empty icon is stored rather than treated as a miss")
        void emptyBytes() {
            PngCache cache = new PngCache(dir);

            cache.put("minecraft:stone", new byte[0]);

            assertNotNull(cache.get("minecraft:stone"));
        }
    }

    @Nested
    @DisplayName("file naming")
    class FileNaming {

        @ParameterizedTest
        @DisplayName("keys with characters a file system rejects are still storable")
        @ValueSource(strings = {
                "minecraft:stone",
                "domum_ornamentum:shingle#a1b2c3d4",
                "some/mod:nested/path",
                "weird\\key*with?chars",
                "trailing.dots...",
        })
        void awkwardKeys(String key) {
            PngCache cache = new PngCache(dir);

            cache.put(key, PNG);

            assertArrayEquals(PNG, cache.get(key), "should survive the round trip through disk");
            assertArrayEquals(PNG, new PngCache(dir).get(key), "and through a restart");
        }

        @Test
        @DisplayName("every cached file lands inside the textures directory")
        void staysInsideCacheDir() throws IOException {
            PngCache cache = new PngCache(dir);

            // A key that would escape the directory if it were used verbatim.
            cache.put("../../escape", PNG);

            try (var files = Files.walk(dir)) {
                List<Path> pngs = files.filter(p -> p.toString().endsWith(".png")).toList();
                assertEquals(1, pngs.size());
                assertTrue(pngs.get(0).startsWith(dir.resolve("textures")),
                        "a cache key must never be able to write outside the cache: " + pngs.get(0));
            }
        }

        @Test
        @DisplayName("two keys differing only in unsafe characters do not collide silently")
        void distinctKeysStayDistinct() {
            PngCache cache = new PngCache(dir);
            byte[] other = {7, 7, 7};

            cache.put("mod:a", PNG);
            cache.put("mod:b", other);

            assertArrayEquals(PNG, cache.get("mod:a"));
            assertArrayEquals(other, cache.get("mod:b"));
        }
    }

    @Nested
    @DisplayName("renderer version stamp")
    class RenderVersion {

        @Test
        @DisplayName("a fresh cache writes the current stamp")
        void writesStamp() {
            new PngCache(dir);

            assertTrue(Files.isRegularFile(dir.resolve("textures").resolve(".render-version")));
        }

        @Test
        @DisplayName("icons drawn by an older renderer are discarded on start")
        void discardsStaleRenders() throws IOException {
            new PngCache(dir).put("minecraft:stone", PNG);
            Path stamp = dir.resolve("textures").resolve(".render-version");
            Files.writeString(stamp, "0", StandardCharsets.UTF_8);

            // A new instance sees the older stamp and clears what the previous renderer drew.
            assertNull(new PngCache(dir).get("minecraft:stone"));
        }

        @Test
        @DisplayName("icons drawn by the current renderer are kept")
        void keepsCurrentRenders() {
            new PngCache(dir).put("minecraft:stone", PNG);

            assertArrayEquals(PNG, new PngCache(dir).get("minecraft:stone"));
        }

        @Test
        @DisplayName("the stamp itself is not mistaken for an icon and deleted")
        void stampSurvivesPurge() throws IOException {
            new PngCache(dir).put("minecraft:stone", PNG);
            Path stamp = dir.resolve("textures").resolve(".render-version");
            Files.writeString(stamp, "0", StandardCharsets.UTF_8);

            new PngCache(dir);

            assertTrue(Files.isRegularFile(stamp));
            assertFalse(Files.readString(stamp).isBlank());
        }
    }

    @Nested
    @DisplayName("unusable cache directory")
    class Unusable {

        @Test
        @DisplayName("a cache whose directory cannot be created still serves from memory")
        void fallsBackToMemory() throws IOException {
            // A regular file where the cache wants a directory: creating it must fail.
            Path blocked = dir.resolve("blocked");
            Files.writeString(blocked, "not a directory");

            PngCache cache = new PngCache(blocked);
            cache.put("minecraft:stone", PNG);

            assertArrayEquals(PNG, cache.get("minecraft:stone"),
                    "a broken disk cache must not take the icons down with it");
        }
    }
}
