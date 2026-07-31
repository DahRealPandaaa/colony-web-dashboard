package DahRealPanda.plugins.colonyweb.util

import DahRealPanda.plugins.colonyweb.support.FakePlatform
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** A base class, so the superclass walk has something to walk. */
private open class ReflectBase {
    private val secret: String = "from the base class"

    fun inheritedValue(): String = "inherited"

    private fun privateInBase(): String = "private in base"
}

private class ReflectTarget : ReflectBase() {
    private val hidden: Int = 42

    fun name(): String = "target"

    fun explode(): String = throw IllegalStateException("this member is not available on this version")

    fun overloaded(): String = "zero"

    fun overloaded(first: String): String = "one:$first"

    fun overloaded(first: String, second: String): String = "two:$first,$second"

    fun onlyTakesTwo(first: String, second: String): String = "two:$first,$second"

    fun nullable(): String? = null

    fun preferred(): String = "preferred"

    fun fallback(): String = "fallback"

    companion object {
        @JvmStatic
        fun staticValue(): String = "static"

        @JvmStatic
        val STATIC_FIELD: String = "static field"
    }
}

/**
 * [MineColoniesReflect] is the seam the entire mod is built on: MineColonies is never on the
 * compile or runtime classpath, so every colony value is fetched by method name at runtime. The
 * contract it has to keep is that nothing it does can throw — a renamed or removed member has to
 * come back empty so the scan carries on with the rest of the colony.
 */
class MineColoniesReflectTest : DescribeSpec({

    beforeSpec { FakePlatform.withMineColonies() }

    val target = ReflectTarget()

    describe("invoke") {
        it("calls a no-argument method and returns its value") {
            MineColoniesReflect.invoke(target, "name").orElse(null) shouldBe "target"
        }

        it("finds a method declared on a superclass") {
            MineColoniesReflect.invoke(target, "inheritedValue").orElse(null) shouldBe "inherited"
        }

        it("returns empty for a method that does not exist") {
            MineColoniesReflect.invoke(target, "noSuchMethod").isPresent shouldBe false
        }

        it("returns empty rather than propagating an exception from the method") {
            MineColoniesReflect.invoke(target, "explode").isPresent shouldBe false
        }

        it("returns empty for a null target") {
            MineColoniesReflect.invoke(null, "name").isPresent shouldBe false
        }

        it("returns empty when the method returns null") {
            MineColoniesReflect.invoke(target, "nullable").isPresent shouldBe false
        }

        it("returns the same answer when called again, now that the lookup is cached") {
            MineColoniesReflect.invoke(target, "name").orElse(null) shouldBe "target"
            MineColoniesReflect.invoke(target, "noSuchMethod").isPresent shouldBe false
        }
    }

    describe("invokeAny") {
        it("uses the overload that takes every argument it was given") {
            MineColoniesReflect.invokeAny(target, "overloaded", "a", "b").orElse(null) shouldBe "two:a,b"
        }

        // MineColonies has changed the arity of these accessors between versions, so the scanners
        // pass everything they have and let the lookup use as much of it as the method wants.
        it("drops arguments the available overload does not take") {
            val onlyZeroArg = object {
                fun value(): String = "zero-arg only"
            }

            MineColoniesReflect.invokeAny(onlyZeroArg, "value", "unused", "also unused")
                .orElse(null) shouldBe "zero-arg only"
        }

        it("calls a no-argument method when given no arguments") {
            MineColoniesReflect.invokeAny(target, "overloaded").orElse(null) shouldBe "zero"
        }

        it("returns empty when no overload can take that few arguments") {
            MineColoniesReflect.invokeAny(target, "onlyTakesTwo", "a").isPresent shouldBe false
        }

        it("returns empty for a method that does not exist") {
            MineColoniesReflect.invokeAny(target, "noSuchMethod", "a").isPresent shouldBe false
        }

        it("returns empty for a null target") {
            MineColoniesReflect.invokeAny(null, "name").isPresent shouldBe false
        }

        it("returns empty rather than propagating an exception") {
            MineColoniesReflect.invokeAny(target, "explode").isPresent shouldBe false
        }
    }

    describe("invokeAnyOf") {
        it("uses the first name that resolves") {
            MineColoniesReflect.invokeAnyOf(target, "preferred", "fallback").orElse(null) shouldBe "preferred"
        }

        it("falls through to a later name when the earlier ones are absent") {
            MineColoniesReflect.invokeAnyOf(target, "goneInThisVersion", "fallback")
                .orElse(null) shouldBe "fallback"
        }

        it("returns empty when none of the names resolves") {
            MineColoniesReflect.invokeAnyOf(target, "neither", "nor").isPresent shouldBe false
        }

        it("returns empty for a null target") {
            MineColoniesReflect.invokeAnyOf(null, "preferred").isPresent shouldBe false
        }
    }

    describe("class and field lookup") {
        it("resolves a class that is on the classpath") {
            MineColoniesReflect.resolve("java.lang.String").orElse(null) shouldBe String::class.java
        }

        it("returns empty for a class that is not, which is the normal case without MineColonies") {
            MineColoniesReflect.resolve("com.minecolonies.api.colony.IColonyManager").isPresent shouldBe false
        }

        it("reads a private field") {
            MineColoniesReflect.fieldValue(target, "hidden").orElse(null) shouldBe 42
        }

        it("reads a private field declared on a superclass") {
            MineColoniesReflect.fieldValue(target, "secret").orElse(null) shouldBe "from the base class"
        }

        it("returns empty for a field that does not exist, and for a null target") {
            MineColoniesReflect.fieldValue(target, "noSuchField").isPresent shouldBe false
            MineColoniesReflect.fieldValue(null, "hidden").isPresent shouldBe false
        }

        it("returns empty when asked for a member of a class that is not present") {
            MineColoniesReflect.invokeStatic("com.minecolonies.api.Gone", "getInstance").isPresent shouldBe false
            MineColoniesReflect.staticFieldValue("com.minecolonies.api.Gone", "INSTANCE").isPresent shouldBe false
        }

        it("returns empty for a member that is missing from a class that is present") {
            MineColoniesReflect.invokeStatic("java.lang.String", "noSuchStatic").isPresent shouldBe false
        }
    }

    // The answer is cached on the first call, because the scanners ask on every pass and a mod
    // list cannot change while the server is running.
    describe("mod detection") {
        it("reports MineColonies as present, and keeps reporting it") {
            MineColoniesReflect.isMineColoniesLoaded() shouldBe true

            FakePlatform.install()

            MineColoniesReflect.isMineColoniesLoaded() shouldBe true
        }
    }
})
