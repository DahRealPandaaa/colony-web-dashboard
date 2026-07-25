package DahRealPanda.plugins.untitled1.command;

import DahRealPanda.plugins.untitled1.Config;
import DahRealPanda.plugins.untitled1.ColonyWebService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.net.InetAddress;

/**
 * {@code /colonyweb} — prints the dashboard URL and status. Permission level 0 (anyone).
 */
public final class ColonyWebCommand {

    private ColonyWebCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("colonyweb")
                .executes(ctx -> {
                    printUrl(ctx.getSource());
                    return 1;
                })
                .then(Commands.literal("status").executes(ctx -> {
                    printStatus(ctx.getSource());
                    return 1;
                }))
                .then(Commands.literal("port").executes(ctx -> {
                    ColonyWebService svc = ColonyWebService.get();
                    int port = svc != null ? svc.getPort() : Config.httpPort;
                    ctx.getSource().sendSuccess(() -> Component.literal("Dashboard port: " + port), false);
                    return 1;
                })));
    }

    private static void printUrl(CommandSourceStack source) {
        String url = "http://" + resolveHost() + ":" + Config.httpPort + "/";
        // Build a clickable, underlined link component (plain text isn't clickable in chat).
        Component link = Component.literal(url).setStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Open the colony dashboard"))));
        Component message = Component.literal("Colony dashboard: ").append(link);
        source.sendSuccess(() -> message, false);
    }

    private static void printStatus(CommandSourceStack source) {
        ColonyWebService svc = ColonyWebService.get();
        if (svc == null) {
            source.sendSuccess(() -> Component.literal("Dashboard service is not running."), false);
            return;
        }
        String msg = "Dashboard: " + (svc.isWebRunning() ? "running" : "stopped")
                + " | port " + svc.getPort()
                + " | SSE clients " + svc.getSseClients()
                + " | MineColonies " + (svc.isMineColoniesDetected() ? "detected" : "not detected");
        source.sendSuccess(() -> Component.literal(msg), false);
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
