package DahRealPanda.plugins.colonyweb.command

import DahRealPanda.plugins.colonyweb.ColonyWebService
import DahRealPanda.plugins.colonyweb.Config
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import java.net.InetAddress

object ColonyWebCommand {
    private const val OP_LEVEL = 2

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(Commands.literal("colonyweb")
            .executes { ctx ->
                printUrl(ctx.source)
                1
            }
            .then(Commands.literal("port").executes { ctx ->
                val service = ColonyWebService.get()
                reply(ctx.source, "Dashboard port: "
                        + (service?.getPort() ?: Config.httpPort))
                1
            })
            .then(Commands.literal("status").requires(op()).executes { ctx ->
                printStatus(ctx.source)
                1
            })
            .then(Commands.literal("sync")
                .executes { ctx -> AccessCommands.syncSelf(ctx.source) }
                .then(playerArg().requires(op())
                    .executes { ctx -> AccessCommands.syncOther(ctx.source, profiles(ctx)) }))
            .then(Commands.literal("logout").requires(op())
                .then(playerArg()
                    .executes { ctx -> AccessCommands.logout(ctx.source, profiles(ctx)) }))
            .then(Commands.literal("access").requires(op())
                .then(Commands.literal("grant").then(playerArg().then(colonyArg()
                    .executes { ctx -> AccessCommands.grant(ctx.source, profiles(ctx), colony(ctx)) })))
                .then(Commands.literal("revoke").then(playerArg().then(colonyArg()
                    .executes { ctx -> AccessCommands.revoke(ctx.source, profiles(ctx), colony(ctx)) })))
                .then(Commands.literal("list").then(playerArg()
                    .executes { ctx -> AccessCommands.list(ctx.source, profiles(ctx)) }))))
    }

    private fun playerArg() = Commands.argument("player", GameProfileArgument.gameProfile())

    private fun colonyArg() = Commands.argument("colony", IntegerArgumentType.integer(0))

    private fun profiles(ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Collection<com.mojang.authlib.GameProfile> =
        GameProfileArgument.getGameProfiles(ctx, "player")

    private fun colony(ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int =
        IntegerArgumentType.getInteger(ctx, "colony")

    private fun op(): java.util.function.Predicate<CommandSourceStack> =
        java.util.function.Predicate { source -> source.hasPermission(OP_LEVEL) }

    private fun printUrl(source: CommandSourceStack) {
        source.sendSuccess({ Component.literal("Colony dashboard: ").append(link(dashboardUrl())) }, false)
        if (Config.authEnabled && source.entity is ServerPlayer) {
            source.sendSuccess({
                Component.literal("Run ")
                    .append(Component.literal("/colonyweb sync").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" to get your sign-in code."))
            }, false)
        }
    }

    private fun printStatus(source: CommandSourceStack) {
        val service = ColonyWebService.get()
        if (service == null) {
            reply(source, "Dashboard service is not running.")
            return
        }
        reply(source, "Dashboard: " + if (service.isWebRunning()) "running" else "stopped"
                + " | port " + service.getPort()
                + " | viewers " + service.getSseClients()
                + " | MineColonies " + if (service.isMineColoniesDetected()) "detected" else "not detected"
                + " | auth " + if (service.auth.enabled()) "on" else "off"
                + " | sessions " + service.auth.sessionCount()
                + " | codes pending " + service.auth.pendingCodeCount())
    }

    fun dashboardUrl(): String {
        return "http://" + resolveHost() + ":" + Config.httpPort + "/"
    }

    fun link(url: String): Component {
        return Component.literal(url).setStyle(
            Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(
                    HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Open the colony dashboard")
                    )
                )
        )
    }

    fun reply(source: CommandSourceStack, message: String) {
        source.sendSuccess({ Component.literal(message) }, false)
    }

    private fun resolveHost(): String {
        if (Config.publicHost.isNotBlank()) {
            return Config.publicHost
        }
        return try {
            InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
            "localhost"
        }
    }
}
