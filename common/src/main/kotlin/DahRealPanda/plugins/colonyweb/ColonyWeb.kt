package DahRealPanda.plugins.colonyweb

import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect
import DahRealPanda.plugins.colonyweb.command.ColonyWebCommand
import com.mojang.brigadier.CommandDispatcher
import com.mojang.logging.LogUtils
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger

/**
 * ColonyWeb — a server-side mod exposing a live, authenticated web dashboard for MineColonies:
 * builders, work orders, citizens, research, defence and warehouse stock.
 *
 * The mod's identity and lifecycle live here, free of any loader API. Each
 * `versions/<mc>-<loader>` project owns the `@Mod` class that subscribes to that
 * loader's events and forwards them to the three methods below.
 */
object ColonyWeb {
    const val MODID = "colonyweb"

    /** Prefix on every log line the mod emits, so it is greppable in latest.log. */
    const val LOG = "[ColonyWeb]"

    private val LOGGER: Logger = LogUtils.getLogger()

    // MineColonies presence is checked at startup (not lazily) so the /colonyweb command
    // knows whether to show the "MineColonies not installed" help text before the first scan.
    @JvmStatic
    fun serverStarting(server: MinecraftServer) {
        LOGGER.info("{} server starting — MineColonies loaded: {}", LOG,
            MineColoniesReflect.isMineColoniesLoaded())
        ColonyWebService.start(server)
    }

    // Shut down the scheduler and HTTP server to release bound ports and daemon threads
    // before the Minecraft server process exits.
    @JvmStatic
    fun serverStopping() {
        LOGGER.info("{} server stopping — shutting down dashboard", LOG)
        ColonyWebService.stop()
    }

    @JvmStatic
    fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        ColonyWebCommand.register(dispatcher)
    }
}
