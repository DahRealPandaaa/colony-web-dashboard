package DahRealPanda.plugins.colonyweb.util

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import net.minecraft.core.BlockPos

/**
 * Every value the dashboard shows arrives as an `Object` from reflection, so [ScanCoercion] is
 * the single place that decides what happens when MineColonies hands back null, or a type nobody
 * expected. Getting that wrong shows a wrong number rather than an error, which is why the
 * fallback behaviour is pinned this closely.
 */
class ScanCoercionTest : DescribeSpec({

    describe("firstNonNull") {
        it("returns the first value that is present") {
            ScanCoercion.firstNonNull(null, "second", "third") shouldBe "second"
        }

        it("returns null when every candidate is missing") {
            ScanCoercion.firstNonNull(null, null) shouldBe null
        }

        it("returns null when there are no candidates at all") {
            ScanCoercion.firstNonNull() shouldBe null
        }

        it("treats a false and a zero as present, not as absent") {
            ScanCoercion.firstNonNull(false, "later") shouldBe false
            ScanCoercion.firstNonNull(0, "later") shouldBe 0
        }
    }

    describe("intOf") {
        it("passes an int through") {
            ScanCoercion.intOf(42, -1) shouldBe 42
        }

        it("narrows any other number, because reflection often returns a boxed long") {
            ScanCoercion.intOf(42L, -1) shouldBe 42
            ScanCoercion.intOf(42.9, -1) shouldBe 42
        }

        it("falls back for null") {
            ScanCoercion.intOf(null, -1) shouldBe -1
        }

        it("falls back for a value that is not a number, including its digits as text") {
            ScanCoercion.intOf("42", -1) shouldBe -1
            ScanCoercion.intOf(true, -1) shouldBe -1
        }
    }

    describe("doubleOf") {
        it("passes a double through") {
            ScanCoercion.doubleOf(4.5, -1.0) shouldBe 4.5
        }

        it("widens an int") {
            ScanCoercion.doubleOf(4, -1.0) shouldBe 4.0
        }

        it("falls back for null and for non-numbers") {
            ScanCoercion.doubleOf(null, -1.0) shouldBe -1.0
            ScanCoercion.doubleOf("4.5", -1.0) shouldBe -1.0
        }
    }

    describe("boolOf") {
        it("passes a boolean through") {
            ScanCoercion.boolOf(true, false) shouldBe true
            ScanCoercion.boolOf(false, true) shouldBe false
        }

        it("falls back for null") {
            ScanCoercion.boolOf(null, true) shouldBe true
        }

        it("does not treat the string \"true\" or the number 1 as truthy") {
            ScanCoercion.boolOf("true", false) shouldBe false
            ScanCoercion.boolOf(1, false) shouldBe false
        }
    }

    describe("stringOf") {
        it("passes a string through") {
            ScanCoercion.stringOf("Guard Tower", "fallback") shouldBe "Guard Tower"
        }

        it("stringifies a non-string value") {
            ScanCoercion.stringOf(42, "fallback") shouldBe "42"
        }

        it("falls back for null") {
            ScanCoercion.stringOf(null, "fallback") shouldBe "fallback"
        }

        it("falls back for an empty string, which is never a useful name") {
            ScanCoercion.stringOf("", "fallback") shouldBe "fallback"
        }

        it("keeps a blank-but-not-empty string, which the caller may want verbatim") {
            ScanCoercion.stringOf(" ", "fallback") shouldBe " "
        }
    }

    describe("blockPosOf") {
        it("passes a block position through") {
            val pos = BlockPos(1, 2, 3)
            ScanCoercion.blockPosOf(pos) shouldBe pos
        }

        it("returns null for null and for anything else") {
            ScanCoercion.blockPosOf(null) shouldBe null
            ScanCoercion.blockPosOf("1,2,3") shouldBe null
        }
    }

    describe("itemStackOf") {
        it("returns null for null and for anything that is not a stack") {
            ScanCoercion.itemStackOf(null) shouldBe null
            ScanCoercion.itemStackOf("minecraft:stone") shouldBe null
        }
    }
})
