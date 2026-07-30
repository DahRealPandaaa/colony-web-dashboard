package DahRealPanda.plugins.colonyweb.forge

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.platform.ConfigBuilder
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.config.ModConfigEvent
import java.util.function.BooleanSupplier
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * Builds the Forge config spec from the options [Config.define] declares, and copies the
 * loaded values back into [Config] whenever Forge reads the file.
 */
@Mod.EventBusSubscriber(modid = ColonyWeb.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
object ForgeConfig {

    val SPEC: ForgeConfigSpec

    init {
        val builder = ForgeConfigSpec.Builder()
        Config.define(Adapter(builder))
        SPEC = builder.build()
    }

    @SubscribeEvent
    @JvmStatic
    fun onLoad(event: ModConfigEvent) {
        Config.reload()
    }

    private class Adapter(private val builder: ForgeConfigSpec.Builder) : ConfigBuilder {

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
