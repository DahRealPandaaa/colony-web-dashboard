package DahRealPanda.plugins.colonyweb.neoforge

import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.platform.ConfigBuilder
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.neoforge.common.ModConfigSpec
import java.util.function.BooleanSupplier
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * Builds the NeoForge config spec from the options [Config.define] declares, and copies the
 * loaded values back into [Config] whenever NeoForge reads the file.
 *
 * Identical to the Forge version bar the class names — which is exactly why the options
 * themselves are declared once, in `common/`, rather than duplicated here.
 *
 * [onLoad] is subscribed by hand from [ColonyWebNeoForge]: NeoForge 21.1 has
 * deprecated the `bus` attribute of `@EventBusSubscriber` that the Forge side still
 * uses.
 */
object NeoForgeConfig {

    val SPEC: ModConfigSpec

    init {
        val builder = ModConfigSpec.Builder()
        Config.define(Adapter(builder))
        SPEC = builder.build()
    }

    @JvmStatic
    fun onLoad(event: ModConfigEvent) {
        Config.reload()
    }

    private class Adapter(private val builder: ModConfigSpec.Builder) : ConfigBuilder {

        override fun defineInt(key: String, defaultValue: Int, min: Int, max: Int, vararg comment: String): IntSupplier {
            val value = builder.comment(*comment).defineInRange(key, defaultValue, min, max)
            return IntSupplier { value.get() }
        }

        override fun defineBoolean(key: String, defaultValue: Boolean, vararg comment: String): BooleanSupplier {
            val value = builder.comment(*comment).define(key, defaultValue)
            return BooleanSupplier { value.get() }
        }

        override fun defineString(key: String, defaultValue: String, vararg comment: String): Supplier<String> {
            val value = builder.comment(*comment).define(key, defaultValue)
            return Supplier { value.get() }
        }
    }
}
