package DahRealPanda.plugins.colonyweb.command;

import DahRealPanda.plugins.colonyweb.ColonyWebService;
import DahRealPanda.plugins.colonyweb.Config;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.net.InetAddress;
import java.util.function.Predicate;

/**
 * {@code /colonyweb} — the dashboard's in-game surface. Command bodies live in
 * {@link AccessCommands}; this class is only the Brigadier tree.
 *
 * <pre>
 * /colonyweb                                 print the dashboard link
 * /colonyweb sync                            sign yourself in — syncs your colonies, gives you a code
 * /colonyweb port                            configured port
 * /colonyweb status                          service status                          (op)
 * /colonyweb sync &lt;player&gt;             issue a code for someone else           (op)
 * /colonyweb access grant &lt;player&gt; &lt;id&gt;   grant access to one colony              (op)
 * /colonyweb access revoke &lt;player&gt; &lt;id&gt;  take that grant away                    (op)
 * /colonyweb access list &lt;player&gt;         show what a player can see              (op)
 * /colonyweb logout &lt;player&gt;              sign a player out everywhere            (op)
 * </pre>
 */
public final class ColonyWebCommand {

    /** Vanilla's "server operator" permission level. */
    private static final int OP_LEVEL = 2;

    private ColonyWebCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("colonyweb")
                .executes(ctx -> {
                    printUrl(ctx.getSource());
                    return 1;
                })
                .then(Commands.literal("port").executes(ctx -> {
                    ColonyWebService service = ColonyWebService.get();
                    reply(ctx.getSource(), "Dashboard port: "
                            + (service != null ? service.getPort() : Config.httpPort));
                    return 1;
                }))
                .then(Commands.literal("status").requires(op()).executes(ctx -> {
                    printStatus(ctx.getSource());
                    return 1;
                }))
                .then(Commands.literal("sync")
                        .executes(ctx -> AccessCommands.syncSelf(ctx.getSource()))
                        .then(playerArg().requires(op())
                                .executes(ctx -> AccessCommands.syncOther(ctx.getSource(), profiles(ctx)))))
                .then(Commands.literal("logout").requires(op())
                        .then(playerArg()
                                .executes(ctx -> AccessCommands.logout(ctx.getSource(), profiles(ctx)))))
                .then(Commands.literal("access").requires(op())
                        .then(Commands.literal("grant").then(playerArg().then(colonyArg()
                                .executes(ctx -> AccessCommands.grant(ctx.getSource(), profiles(ctx), colony(ctx))))))
                        .then(Commands.literal("revoke").then(playerArg().then(colonyArg()
                                .executes(ctx -> AccessCommands.revoke(ctx.getSource(), profiles(ctx), colony(ctx))))))
                        .then(Commands.literal("list").then(playerArg()
                                .executes(ctx -> AccessCommands.list(ctx.getSource(), profiles(ctx)))))));
    }

    // ------------------------------------------------------------------
    // Argument plumbing
    // ------------------------------------------------------------------

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> playerArg() {
        return Commands.argument("player", GameProfileArgument.gameProfile());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> colonyArg() {
        return Commands.argument("colony", IntegerArgumentType.integer(0));
    }

    private static java.util.Collection<com.mojang.authlib.GameProfile> profiles(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return GameProfileArgument.getGameProfiles(ctx, "player");
    }

    private static int colony(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "colony");
    }

    private static Predicate<CommandSourceStack> op() {
        return source -> source.hasPermission(OP_LEVEL);
    }

    // ------------------------------------------------------------------
    // Simple outputs
    // ------------------------------------------------------------------

    private static void printUrl(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Colony dashboard: ").append(link(dashboardUrl())), false);
        if (Config.authEnabled && source.getEntity() instanceof ServerPlayer) {
            source.sendSuccess(() -> Component.literal("Run ")
                    .append(Component.literal("/colonyweb sync").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" to get your sign-in code.")), false);
        }
    }

    private static void printStatus(CommandSourceStack source) {
        ColonyWebService service = ColonyWebService.get();
        if (service == null) {
            reply(source, "Dashboard service is not running.");
            return;
        }
        reply(source, "Dashboard: " + (service.isWebRunning() ? "running" : "stopped")
                + " | port " + service.getPort()
                + " | viewers " + service.getSseClients()
                + " | MineColonies " + (service.isMineColoniesDetected() ? "detected" : "not detected")
                + " | auth " + (service.auth().enabled() ? "on" : "off")
                + " | sessions " + service.auth().sessionCount()
                + " | codes pending " + service.auth().pendingCodeCount());
    }

    /** The dashboard URL, honouring {@code publicHost} when the operator set one. */
    static String dashboardUrl() {
        return "http://" + resolveHost() + ":" + Config.httpPort + "/";
    }

    /** A clickable, underlined chat link (plain text is not clickable). */
    static Component link(String url) {
        return Component.literal(url).setStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Open the colony dashboard"))));
    }

    static void reply(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static String resolveHost() {
        if (Config.publicHost != null && !Config.publicHost.isBlank()) {
            return Config.publicHost;
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
