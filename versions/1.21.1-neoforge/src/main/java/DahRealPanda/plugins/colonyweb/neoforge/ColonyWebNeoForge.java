package DahRealPanda.plugins.colonyweb.neoforge;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.platform.Platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * NeoForge entrypoint. Everything it does is forward a loader event to {@link ColonyWeb}; the mod
 * itself is in {@code common/} and knows nothing about NeoForge.
 */
@Mod(ColonyWeb.MODID)
public class ColonyWebNeoForge {

    // NeoForge hands the mod container and its mod event bus to the constructor rather than
    // exposing a global ModLoadingContext, and has its own game event bus; that is the whole
    // difference from the Forge entrypoint.
    public ColonyWebNeoForge(ModContainer container, IEventBus modBus) {
        // Must come first: the shared code reads the platform from its very first call.
        Platform.init(new NeoForgePlatform());

        container.registerConfig(ModConfig.Type.COMMON, NeoForgeConfig.SPEC);
        modBus.addListener(NeoForgeConfig::onLoad);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ColonyWeb.serverStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ColonyWeb.serverStopping();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ColonyWeb.registerCommands(event.getDispatcher());
    }
}
