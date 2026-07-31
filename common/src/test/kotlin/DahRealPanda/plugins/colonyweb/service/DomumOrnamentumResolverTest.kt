package DahRealPanda.plugins.colonyweb.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The texture key of a Domum Ornamentum block is derived from its materials alone. Everything the
 * dashboard does with such a block hangs off that key: the PNG it caches, the material breakdown
 * it shows, and whether a taught cutter recipe is recognised as producing the resource a builder
 * asked for. Only [DomumOrnamentumResolver.canonicalFingerprint] is reachable without a live item
 * registry, but it is the part that decides all three.
 */
class DomumOrnamentumResolverTest : DescribeSpec({

    describe("canonicalFingerprint") {

        it("is stable across insertion order") {
            val oneWay = DomumOrnamentumResolver.canonicalFingerprint(
                linkedMapOf("centre" to "minecraft:oak_planks", "frame" to "minecraft:stone"))
            val other = DomumOrnamentumResolver.canonicalFingerprint(
                linkedMapOf("frame" to "minecraft:stone", "centre" to "minecraft:oak_planks"))

            // The same block reaches the scanner as NBT on 1.20.1 and as a data component on 1.21,
            // and the two hand back their entries in different orders.
            oneWay shouldBe other
        }

        it("distinguishes materials from the component they fill") {
            val swapped = DomumOrnamentumResolver.canonicalFingerprint(
                mapOf("centre" to "minecraft:stone", "frame" to "minecraft:oak_planks"))
            val original = DomumOrnamentumResolver.canonicalFingerprint(
                mapOf("centre" to "minecraft:oak_planks", "frame" to "minecraft:stone"))

            swapped shouldNotBe original
        }

        it("ignores nothing but the materials") {
            val single = DomumOrnamentumResolver.canonicalFingerprint(
                mapOf("centre" to "minecraft:oak_planks"))

            // No stack data leaks in, so an unrelated tag or an empty textureData compound — which
            // is what MineColonies writes onto a cutter recipe's output — cannot split one block
            // into two cache entries.
            single shouldBe "centre=minecraft:oak_planks"
        }

        it("is empty for a block with no materials") {
            DomumOrnamentumResolver.canonicalFingerprint(emptyMap()) shouldBe ""
        }
    }
})
