package DahRealPanda.plugins.colonyweb.forge

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.platform.Platform
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig

/**
 * Forge entrypoint. Everything it does is forward a loader event to [ColonyWeb]; the mod
 * itself is in `common/` and knows nothing about Forge.
 */
@Mod(ColonyWeb.MODID)
class ColonyWebForge {

    init {
        // Must come first: the shared code reads the platform from its very first call.
        Platform.init(ForgePlatform())

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfig.SPEC)
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        ColonyWeb.serverStarting(event.server)
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        ColonyWeb.serverStopping()
    }

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        ColonyWeb.registerCommands(event.dispatcher)
    }
}
