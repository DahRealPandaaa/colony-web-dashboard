package DahRealPanda.plugins.colonyweb;

import DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect;
import DahRealPanda.plugins.colonyweb.command.ColonyWebCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * ColonyWeb — a server-side mod exposing a live, authenticated web dashboard for MineColonies:
 * builders, work orders, citizens, research, defence and warehouse stock.
 *
 * <p>The mod's identity and lifecycle live here, free of any loader API. Each
 * {@code versions/<mc>-<loader>} project owns the {@code @Mod} class that subscribes to that
 * loader's events and forwards them to the three methods below.</p>
 */
public final class ColonyWeb {

    public static final String MODID = "colonyweb";

    /** Prefix on every log line the mod emits, so it is greppable in latest.log. */
    public static final String LOG = "[ColonyWeb]";

    private static final Logger LOGGER = LogUtils.getLogger();

    private ColonyWeb() {
    }

    /** Bring the dashboard up. */
    public static void serverStarting(MinecraftServer server) {
        LOGGER.info("{} server starting — MineColonies loaded: {}", LOG,
                MineColoniesReflect.isMineColoniesLoaded());
        ColonyWebService.start(server);
    }

    /** Take it back down. */
    public static void serverStopping() {
        LOGGER.info("{} server stopping — shutting down dashboard", LOG);
        ColonyWebService.stop();
    }

    /** Register the {@code /colonyweb} command tree. */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        ColonyWebCommand.register(dispatcher);
    }
}
