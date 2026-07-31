package DahRealPanda.plugins.colonyweb.util

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [Text] turns the identifiers MineColonies hands back through reflection — registry names,
 * blueprint paths, translation keys — into something a dashboard can show a player. It is the
 * one helper whose output is visible on nearly every screen, so its edge cases are worth pinning.
 */
class TextTest : DescribeSpec({

    describe("pathOf") {
        it("drops the namespace of a registry name") {
            Text.pathOf("minecolonies:blockhutbuilder") shouldBe "blockhutbuilder"
        }

        it("returns a name that has no namespace unchanged") {
            Text.pathOf("blockhutbuilder") shouldBe "blockhutbuilder"
        }

        it("keeps everything after the first colon") {
            Text.pathOf("ns:path:with:colons") shouldBe "path:with:colons"
        }

        it("treats a trailing colon as an empty path") {
            Text.pathOf("minecolonies:") shouldBe ""
        }

        it("maps null to the empty string rather than throwing") {
            Text.pathOf(null) shouldBe ""
        }
    }

    describe("humanize") {
        it("title-cases a snake_case identifier") {
            Text.humanize("guard_tower") shouldBe "Guard Tower"
        }

        it("title-cases a kebab-case identifier") {
            Text.humanize("guard-tower") shouldBe "Guard Tower"
        }

        it("splits camelCase on the case change") {
            Text.humanize("guardTower") shouldBe "Guard Tower"
        }

        it("separates letters from digits in both directions") {
            Text.humanize("level2house") shouldBe "Level 2 House"
        }

        it("keeps only the last segment of a path") {
            Text.humanize("blueprints/dev/sawmill1.blueprint") shouldBe "Sawmill 1"
        }

        it("strips a .json extension") {
            Text.humanize("townhall.json") shouldBe "Townhall"
        }

        it("collapses runs of separators instead of emitting empty words") {
            Text.humanize("guard__tower--east") shouldBe "Guard Tower East"
        }

        it("leaves an already-readable name alone") {
            Text.humanize("Guard Tower") shouldBe "Guard Tower"
        }

        it("does not lowercase an acronym it did not split") {
            Text.humanize("NPC_house") shouldBe "NPC House"
        }

        it("returns the empty string for null, empty and blank input") {
            Text.humanize(null) shouldBe ""
            Text.humanize("") shouldBe ""
            Text.humanize("   ") shouldBe ""
        }

        it("returns the empty string when the name is only separators") {
            Text.humanize("___") shouldBe ""
        }
    }

    describe("looksLikeKey") {
        it("accepts a dotted translation key") {
            Text.looksLikeKey("block.minecraft.stone") shouldBe true
        }

        it("accepts a namespaced registry name") {
            Text.looksLikeKey("minecraft:stone") shouldBe true
        }

        it("rejects anything containing a space, because keys never do") {
            Text.looksLikeKey("Guard Tower") shouldBe false
            Text.looksLikeKey("block.minecraft.stone ") shouldBe false
        }

        it("rejects a plain word") {
            Text.looksLikeKey("stone") shouldBe false
        }

        it("rejects a leading separator, which would leave an empty namespace") {
            Text.looksLikeKey(".stone") shouldBe false
            Text.looksLikeKey(":stone") shouldBe false
        }

        it("rejects the empty string") {
            Text.looksLikeKey("") shouldBe false
        }
    }

    describe("stringOrEmpty") {
        it("passes a value through") {
            Text.stringOrEmpty("Guard Tower") shouldBe "Guard Tower"
        }

        it("turns null into the empty string") {
            Text.stringOrEmpty(null) shouldBe ""
        }
    }

    describe("displayName") {
        it("uses a value that is already human-readable") {
            Text.displayName("Guard Tower", "fallback") shouldBe "Guard Tower"
        }

        it("humanizes the last segment of an untranslated key") {
            Text.displayName("block.minecolonies.blockhutbuilder", "fallback") shouldBe "Blockhutbuilder"
        }

        it("drops a .name suffix before humanizing") {
            Text.displayName("item.minecolonies.guard_tower.name", "fallback") shouldBe "Guard Tower"
        }

        it("falls back when the value is null") {
            Text.displayName(null, "fallback") shouldBe "fallback"
        }

        it("falls back when the value is blank") {
            Text.displayName("   ", "fallback") shouldBe "fallback"
        }

        it("falls back when a key humanizes to nothing") {
            Text.displayName("a.__", "fallback") shouldBe "fallback"
        }
    }
})
