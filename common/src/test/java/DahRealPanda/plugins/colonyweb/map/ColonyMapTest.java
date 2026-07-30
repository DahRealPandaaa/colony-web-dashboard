package DahRealPanda.plugins.colonyweb.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The colony map raster.
 *
 * <p>The behaviour worth protecting is what happens when a colony outgrows its map: the new
 * raster inherits everything the old one drew, because the chunks it would otherwise blank out
 * are exactly the ones the server no longer has loaded and cannot redraw.</p>
 */
class ColonyMapTest {

    private static ColonyMap map(int minX, int minZ, int size) {
        return new ColonyMap(minX, minZ, size, size);
    }

    @Nested
    @DisplayName("geometry")
    class Geometry {

        @Test
        @DisplayName("chunk bounds are derived from the block bounds")
        void chunkBounds() {
            ColonyMap colonyMap = map(-64, -64, 128);

            assertEquals(-4, colonyMap.chunkX);
            assertEquals(-4, colonyMap.chunkZ);
            assertEquals(8, colonyMap.chunkCols);
            assertEquals(8, colonyMap.chunkRows);
            assertEquals(64, colonyMap.chunkCount());
        }

        @Test
        @DisplayName("the rasters are sized for one pixel per block")
        void rasterSize() {
            ColonyMap colonyMap = map(0, 0, 64);

            assertEquals(64 * 64, colonyMap.rgb.length);
            assertEquals(64 * 64, colonyMap.top.length);
        }

        @Test
        @DisplayName("a fresh map has drawn nothing")
        void startsEmpty() {
            ColonyMap colonyMap = map(0, 0, 64);

            assertEquals(0, colonyMap.mapped);
            assertEquals(0, colonyMap.cursor);
            for (long stamp : colonyMap.chunkStamp) {
                assertEquals(0, stamp);
            }
        }
    }

    @Nested
    @DisplayName("covers")
    class Covers {

        @Test
        @DisplayName("an area inside the map is covered")
        void inside() {
            assertTrue(map(0, 0, 128).covers(16, 16, 64, 64));
        }

        @Test
        @DisplayName("the exact bounds are covered")
        void exactBounds() {
            assertTrue(map(0, 0, 128).covers(0, 0, 128, 128));
        }

        @Test
        @DisplayName("an area poking out on any side is not covered")
        void outside() {
            ColonyMap colonyMap = map(0, 0, 128);

            assertFalse(colonyMap.covers(-1, 0, 64, 64), "past the west edge");
            assertFalse(colonyMap.covers(0, -1, 64, 64), "past the north edge");
            assertFalse(colonyMap.covers(0, 0, 129, 64), "past the east edge");
            assertFalse(colonyMap.covers(0, 0, 64, 129), "past the south edge");
        }

        @Test
        @DisplayName("negative coordinates work the same way")
        void negativeOrigin() {
            ColonyMap colonyMap = map(-128, -128, 128);

            assertTrue(colonyMap.covers(-128, -128, -64, -64));
            assertFalse(colonyMap.covers(-129, -128, -64, -64));
        }
    }

    @Nested
    @DisplayName("inheriting from an outgrown map")
    class Inheriting {

        @Test
        @DisplayName("pixels the old map drew are copied into the overlap")
        void copiesPixels() {
            ColonyMap old = map(0, 0, 32);
            old.rgb[0] = 0x123456;
            old.top[0] = 70;

            ColonyMap grown = map(0, 0, 64);
            grown.inherit(old);

            assertEquals(0x123456, grown.rgb[0]);
            assertEquals(70, grown.top[0]);
        }

        @Test
        @DisplayName("pixels land at the right place when the origin moves")
        void reindexesOnShift() {
            ColonyMap old = map(0, 0, 32);
            // Block (16, 16) in the old map.
            old.rgb[16 * 32 + 16] = 0xABCDEF;

            // The new map starts 16 blocks further west and north, so that same block moves.
            ColonyMap grown = new ColonyMap(-16, -16, 64, 64);
            grown.inherit(old);

            assertEquals(0xABCDEF, grown.rgb[(16 + 16) * 64 + (16 + 16)]);
        }

        @Test
        @DisplayName("chunk timestamps and the drawn count carry over")
        void carriesChunkStamps() {
            ColonyMap old = map(0, 0, 32);
            old.chunkStamp[0] = 1234L;
            old.mapped = 1;

            ColonyMap grown = map(0, 0, 64);
            grown.inherit(old);

            assertEquals(1234L, grown.chunkStamp[0]);
            assertEquals(1, grown.mapped);
        }

        @Test
        @DisplayName("chunks outside the old map stay undrawn")
        void newAreaStaysUndrawn() {
            ColonyMap old = map(0, 0, 32);
            for (int i = 0; i < old.chunkStamp.length; i++) {
                old.chunkStamp[i] = 1234L;
            }
            old.mapped = old.chunkStamp.length;

            ColonyMap grown = map(0, 0, 64);
            grown.inherit(old);

            assertEquals(old.chunkStamp.length, grown.mapped,
                    "only the chunks the old map actually had should count as drawn");
            assertTrue(grown.mapped < grown.chunkCount(), "the new area is still blank");
        }

        @Test
        @DisplayName("two maps that do not overlap copy nothing")
        void disjointMaps() {
            ColonyMap old = map(0, 0, 32);
            old.rgb[0] = 0x123456;
            old.chunkStamp[0] = 1234L;

            ColonyMap elsewhere = map(1024, 1024, 32);
            elsewhere.inherit(old);

            assertEquals(0, elsewhere.rgb[0]);
            assertEquals(0, elsewhere.mapped);
        }

        @Test
        @DisplayName("the version keeps climbing so browsers do not serve a cached image")
        void keepsVersion() {
            ColonyMap old = map(0, 0, 32);
            old.version = 7;
            old.renderedAt = 999L;
            old.png = new byte[]{1, 2, 3};

            ColonyMap grown = map(0, 0, 64);
            grown.inherit(old);

            assertEquals(7, grown.version);
            assertEquals(999L, grown.renderedAt);
            assertNotEquals(old.png, grown.png,
                    "the old PNG is the wrong size for the new bounds and must not be reused");
        }
    }

    @Nested
    @DisplayName("draw order")
    class DrawOrder {

        @Test
        @DisplayName("every chunk appears exactly once")
        void coversEveryChunk() {
            ColonyMap colonyMap = map(0, 0, 64);

            boolean[] seen = new boolean[colonyMap.chunkCount()];
            for (int index : colonyMap.order) {
                assertFalse(seen[index], "chunk " + index + " listed twice");
                seen[index] = true;
            }
            for (boolean visited : seen) {
                assertTrue(visited, "every chunk should be scheduled");
            }
        }

        @Test
        @DisplayName("the colony itself is drawn before its outskirts")
        void centreFirst() {
            ColonyMap colonyMap = map(0, 0, 64);
            int cols = colonyMap.chunkCols;
            double mid = (cols - 1) / 2.0;

            double first = distanceFromCentre(colonyMap.order[0], cols, mid);
            double last = distanceFromCentre(colonyMap.order[colonyMap.order.length - 1], cols, mid);

            assertTrue(first < last, "the first chunk drawn should be nearer the middle");
        }

        @Test
        @DisplayName("the order never moves outward and then back in")
        void monotonic() {
            ColonyMap colonyMap = map(0, 0, 128);
            int cols = colonyMap.chunkCols;
            double mid = (cols - 1) / 2.0;

            double previous = -1;
            for (int index : colonyMap.order) {
                double distance = distanceFromCentre(index, cols, mid);
                assertTrue(distance >= previous, "draw order jumped back towards the centre");
                previous = distance;
            }
        }

        private double distanceFromCentre(int index, int cols, double mid) {
            double dx = (index % cols) - mid;
            double dz = (index / cols) - mid;
            return dx * dx + dz * dz;
        }
    }
}
