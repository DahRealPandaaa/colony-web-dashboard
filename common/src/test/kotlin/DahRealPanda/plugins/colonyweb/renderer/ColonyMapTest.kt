package DahRealPanda.plugins.colonyweb.renderer

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * The colony map is one pixel per block, drawn a chunk at a time and re-cut whenever the colony
 * grows. All of that rests on the coordinate arithmetic here — which has to hold up west and north
 * of the origin, where a colony is just as likely to be as anywhere else.
 */
class ColonyMapTest : DescribeSpec({

    describe("geometry") {
        it("derives the chunk grid from the block bounds") {
            val map = ColonyMap(minX = 0, minZ = 0, width = 128, height = 64)

            map.chunkX shouldBe 0
            map.chunkZ shouldBe 0
            map.chunkCols shouldBe 8
            map.chunkRows shouldBe 4
            map.chunkCount() shouldBe 32
        }

        it("allocates one pixel per block") {
            val map = ColonyMap(minX = 0, minZ = 0, width = 128, height = 64)

            map.rgb.size shouldBe 128 * 64
            map.top.size shouldBe 128 * 64
        }

        // A colony west or north of the origin has negative bounds, and an integer division would
        // round those towards zero — putting the map's first chunk one chunk too far east.
        it("rounds negative bounds down to the chunk that contains them") {
            val map = ColonyMap(minX = -128, minZ = -64, width = 128, height = 64)

            map.chunkX shouldBe -8
            map.chunkZ shouldBe -4
        }

        it("rounds a bound that is not on a chunk boundary down to the chunk containing it") {
            ColonyMap(minX = 17, minZ = -1, width = 32, height = 32).let { map ->
                map.chunkX shouldBe 1
                map.chunkZ shouldBe -1
            }
        }

        it("starts with nothing drawn") {
            val map = ColonyMap(minX = 0, minZ = 0, width = 64, height = 64)

            map.mapped shouldBe 0
            map.cursor shouldBe 0
            map.version shouldBe 0
            map.renderedAt shouldBe 0L
            map.png shouldBe null
        }

        // Chunks are drawn from the middle outwards, because the town hall is at the centre and
        // that is the part of the map a player looks at first.
        it("draws from the centre outwards") {
            val map = ColonyMap(minX = 0, minZ = 0, width = 48, height = 48)

            map.order.size shouldBe 9
            map.order.first() shouldBe 4
        }
    }

    describe("covers") {
        val map = ColonyMap(minX = 0, minZ = 0, width = 128, height = 128)

        it("covers a region well inside its bounds") {
            map.covers(10, 10, 100, 100) shouldBe true
        }

        it("covers a region that reaches exactly to its bounds") {
            map.covers(0, 0, 128, 128) shouldBe true
        }

        it("does not cover a region that runs off any edge") {
            map.covers(-1, 0, 100, 100) shouldBe false
            map.covers(0, -1, 100, 100) shouldBe false
            map.covers(0, 0, 129, 100) shouldBe false
            map.covers(0, 0, 100, 129) shouldBe false
        }

        it("does not cover a region that misses it entirely") {
            map.covers(1000, 1000, 1100, 1100) shouldBe false
        }

        it("works the same for a map west and north of the origin") {
            val negative = ColonyMap(minX = -256, minZ = -256, width = 128, height = 128)

            negative.covers(-256, -256, -128, -128) shouldBe true
            negative.covers(-257, -256, -128, -128) shouldBe false
        }
    }

    // When a colony grows the map is re-cut around the new bounds. Redrawing it from scratch would
    // blank the dashboard for as long as it takes to walk the chunks again, so the new map takes
    // over the pixels and the per-chunk timestamps of the old one.
    describe("inheriting from the previous map") {
        it("carries over the pixels of the overlapping area") {
            val old = ColonyMap(minX = 0, minZ = 0, width = 32, height = 32)
            old.rgb[0] = 0x00FF00
            old.rgb[31 * 32 + 31] = 0x0000FF

            val grown = ColonyMap(minX = 0, minZ = 0, width = 64, height = 64)
            grown.inherit(old)

            grown.rgb[0] shouldBe 0x00FF00
            grown.rgb[31 * 64 + 31] shouldBe 0x0000FF
        }

        it("carries over which chunks were already drawn") {
            val old = ColonyMap(minX = 0, minZ = 0, width = 32, height = 32)
            old.chunkStamp[0] = 12345L
            old.version = 7
            old.renderedAt = 999L

            val grown = ColonyMap(minX = 0, minZ = 0, width = 64, height = 64)
            grown.inherit(old)

            grown.chunkStamp[0] shouldBe 12345L
            grown.mapped shouldBe 1
            grown.version shouldBe 7
            grown.renderedAt shouldBe 999L
        }

        it("leaves the newly added area undrawn") {
            val old = ColonyMap(minX = 0, minZ = 0, width = 32, height = 32)
            old.chunkStamp.fill(1L)

            val grown = ColonyMap(minX = 0, minZ = 0, width = 64, height = 64)
            grown.inherit(old)

            grown.mapped shouldBe 4
            grown.chunkCount() shouldBe 16
        }

        it("carries over only the part that still overlaps when the map shifts") {
            val old = ColonyMap(minX = 0, minZ = 0, width = 64, height = 64)
            old.rgb[0] = 0x00FF00
            old.rgb[32 * 64 + 32] = 0x0000FF

            val shifted = ColonyMap(minX = 32, minZ = 32, width = 64, height = 64)
            shifted.inherit(old)

            shifted.rgb[0] shouldBe 0x0000FF
        }

        it("inherits nothing from a map that no longer overlaps at all") {
            val old = ColonyMap(minX = 0, minZ = 0, width = 32, height = 32)
            old.rgb.fill(0x00FF00)
            old.chunkStamp.fill(1L)

            val elsewhere = ColonyMap(minX = 1024, minZ = 1024, width = 32, height = 32)
            elsewhere.inherit(old)

            elsewhere.mapped shouldBe 0
            elsewhere.rgb.all { it == 0 } shouldBe true
        }
    }
})
