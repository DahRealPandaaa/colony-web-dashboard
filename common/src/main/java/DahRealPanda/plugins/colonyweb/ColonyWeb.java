package DahRealPanda.plugins.colonyweb;

import DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect;
import DahRealPanda.plugins.colonyweb.command.ColonyWebCommand;
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
 * ColonyWeb — a server-side Forge mod exposing a live, authenticated web dashboard for
 * MineColonies: builders, work orders, citizens, research, defence and warehouse stock.
 */
@Mod(ColonyWeb.MODID)
public class ColonyWeb {

    public static final String MODID = "colonyweb";

    /** Prefix on every log line the mod emits, so it is greppable in latest.log. */
    public static final String LOG = "[ColonyWeb]";

    private static final Logger LOGGER = LogUtils.getLogger();

    public ColonyWeb() {
        // Register config and wire the server lifecycle + command events on the forge bus.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("{} server starting — MineColonies loaded: {}", LOG,
                MineColoniesReflect.isMineColoniesLoaded());
        ColonyWebService.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("{} server stopping — shutting down dashboard", LOG);
        ColonyWebService.stop();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ColonyWebCommand.register(event.getDispatcher());
    }
}
