package DahRealPanda.plugins.colonyweb.neoforge;

import DahRealPanda.plugins.colonyweb.Config;
import DahRealPanda.plugins.colonyweb.platform.ConfigBuilder;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Builds the NeoForge config spec from the options {@link Config#define} declares, and copies the
 * loaded values back into {@link Config} whenever NeoForge reads the file.
 *
 * <p>Identical to the Forge version bar the class names — which is exactly why the options
 * themselves are declared once, in {@code common/}, rather than duplicated here.</p>
 *
 * <p>{@link #onLoad} is subscribed by hand from {@link ColonyWebNeoForge}: NeoForge 21.1 has
 * deprecated the {@code bus} attribute of {@code @EventBusSubscriber} that the Forge side still
 * uses.</p>
 */
public final class NeoForgeConfig {

    static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        Config.define(new Adapter(builder));
        SPEC = builder.build();
    }

    private NeoForgeConfig() {
    }

    static void onLoad(final ModConfigEvent event) {
        Config.reload();
    }

    private record Adapter(ModConfigSpec.Builder builder) implements ConfigBuilder {

        @Override
        public IntSupplier defineInt(String key, int defaultValue, int min, int max, String... comment) {
            ModConfigSpec.IntValue value = builder.comment(comment).defineInRange(key, defaultValue, min, max);
            return value::get;
        }

        @Override
        public BooleanSupplier defineBoolean(String key, boolean defaultValue, String... comment) {
            ModConfigSpec.BooleanValue value = builder.comment(comment).define(key, defaultValue);
            return value::get;
        }

        @Override
        public Supplier<String> defineString(String key, String defaultValue, String... comment) {
            ModConfigSpec.ConfigValue<String> value = builder.comment(comment).define(key, defaultValue);
            return value::get;
        }
    }
}
