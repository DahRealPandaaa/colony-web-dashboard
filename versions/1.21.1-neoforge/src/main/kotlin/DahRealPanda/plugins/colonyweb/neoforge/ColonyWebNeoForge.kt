package DahRealPanda.plugins.colonyweb.neoforge

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.platform.Platform
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent

/**
 * NeoForge entrypoint. Everything it does is forward a loader event to [ColonyWeb]; the mod
 * itself is in `common/` and knows nothing about NeoForge.
 */
@Mod(ColonyWeb.MODID)
class ColonyWebNeoForge(container: ModContainer, modBus: IEventBus) {

    // NeoForge hands the mod container and its mod event bus to the constructor rather than
    // exposing a global ModLoadingContext, and has its own game event bus; that is the whole
    // difference from the Forge entrypoint.
    init {
        // Must come first: the shared code reads the platform from its very first call.
        Platform.init(NeoForgePlatform())

        container.registerConfig(ModConfig.Type.COMMON, NeoForgeConfig.SPEC)
        modBus.addListener(NeoForgeConfig::onLoad)
        NeoForge.EVENT_BUS.register(this)
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
