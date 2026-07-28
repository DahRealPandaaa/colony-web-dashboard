package DahRealPanda.plugins.colonyweb.forge;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.Config;
import DahRealPanda.plugins.colonyweb.platform.ConfigBuilder;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Builds the Forge config spec from the options {@link Config#define} declares, and copies the
 * loaded values back into {@link Config} whenever Forge reads the file.
 */
@Mod.EventBusSubscriber(modid = ColonyWeb.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeConfig {

    static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        Config.define(new Adapter(builder));
        SPEC = builder.build();
    }

    private ForgeConfig() {
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        Config.reload();
    }

    private record Adapter(ForgeConfigSpec.Builder builder) implements ConfigBuilder {

        @Override
        public IntSupplier defineInt(String key, int defaultValue, int min, int max, String... comment) {
            ForgeConfigSpec.IntValue value = builder.comment(comment).defineInRange(key, defaultValue, min, max);
            return value::get;
        }

        @Override
        public BooleanSupplier defineBoolean(String key, boolean defaultValue, String... comment) {
            ForgeConfigSpec.BooleanValue value = builder.comment(comment).define(key, defaultValue);
            return value::get;
        }

        @Override
        public Supplier<String> defineString(String key, String defaultValue, String... comment) {
            ForgeConfigSpec.ConfigValue<String> value = builder.comment(comment).define(key, defaultValue);
            return value::get;
        }
    }
}
