package DahRealPanda.plugins.colonyweb.platform

import java.util.function.BooleanSupplier
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * The slice of a loader's config-spec builder that ColonyWeb uses.
 *
 * Forge's [ForgeConfigSpec.Builder] and NeoForge's [ModConfigSpec.Builder] have
 * identical signatures but no common supertype. Rather than keep a copy of every option per
 * loader — ten settings, their defaults, ranges and comments, drifting apart one edit at a
 * time — each loader implements this in about twenty lines and
 * [DahRealPanda.plugins.colonyweb.Config.define] declares the options once.
 *
 * The returned suppliers read the live config value, so they must not be called before the
 * loader has finished loading it.
 */
interface ConfigBuilder {
    fun defineInt(key: String, defaultValue: Int, min: Int, max: Int, vararg comment: String): IntSupplier
    fun defineBoolean(key: String, defaultValue: Boolean, vararg comment: String): BooleanSupplier
    fun defineString(key: String, defaultValue: String, vararg comment: String): Supplier<String>
}
