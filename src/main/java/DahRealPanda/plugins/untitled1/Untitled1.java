package DahRealPanda.plugins.untitled1;

import DahRealPanda.plugins.untitled1.colony.MineColoniesReflect;
import DahRealPanda.plugins.untitled1.command.ColonyWebCommand;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Colony Web Dashboard — server-side Forge mod that exposes a live web dashboard of
 * MineColonies builder work orders, hut inventories and warehouse stock.
 */
@Mod(Untitled1.MODID)
public class Untitled1 {

    public static final String MODID = "untitled1";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Untitled1() {
        // Register config and wire the server lifecycle + command events on the forge bus.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[ColonyWeb] server starting — MineColonies loaded: {}",
                MineColoniesReflect.isMineColoniesLoaded());
        ColonyWebService.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[ColonyWeb] server stopping — shutting down dashboard");
        ColonyWebService.stop();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ColonyWebCommand.register(event.getDispatcher());
    }
}
