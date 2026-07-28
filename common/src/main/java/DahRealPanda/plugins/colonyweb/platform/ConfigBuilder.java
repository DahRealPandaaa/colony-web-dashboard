package DahRealPanda.plugins.colonyweb.platform;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The slice of a loader's config-spec builder that ColonyWeb uses.
 *
 * <p>Forge's {@code ForgeConfigSpec.Builder} and NeoForge's {@code ModConfigSpec.Builder} have
 * identical signatures but no common supertype. Rather than keep a copy of every option per
 * loader — ten settings, their defaults, ranges and comments, drifting apart one edit at a
 * time — each loader implements this in about twenty lines and
 * {@link DahRealPanda.plugins.colonyweb.Config#define} declares the options once.</p>
 *
 * <p>The returned suppliers read the live config value, so they must not be called before the
 * loader has finished loading it.</p>
 */
public interface ConfigBuilder {

    IntSupplier defineInt(String key, int defaultValue, int min, int max, String... comment);

    BooleanSupplier defineBoolean(String key, boolean defaultValue, String... comment);

    Supplier<String> defineString(String key, String defaultValue, String... comment);
}
