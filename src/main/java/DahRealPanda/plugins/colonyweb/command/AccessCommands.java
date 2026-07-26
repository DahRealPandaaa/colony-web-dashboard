package DahRealPanda.plugins.colonyweb.command;

import DahRealPanda.plugins.colonyweb.ColonyWebService;
import DahRealPanda.plugins.colonyweb.Config;
import DahRealPanda.plugins.colonyweb.auth.AuthService;
import DahRealPanda.plugins.colonyweb.auth.WebUser;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bodies of the {@code /colonyweb sync}, {@code access} and {@code logout} subcommands.
 *
 * <p>These run on the server thread, which is exactly where colony membership has to be read
 * from — so a sync always reflects the colonies as they stand right now.</p>
 */
final class AccessCommands {

    private AccessCommands() {
    }

    // ------------------------------------------------------------------
    // Sync — mirror a player's colonies and hand them a pairing code
    // ------------------------------------------------------------------

    /** {@code /colonyweb sync} — the player signs themselves in. */
    static int syncSelf(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            ColonyWebCommand.reply(source, "Only a player can sync themselves. Use /colonyweb sync <player>.");
            return 0;
        }
        ColonyWebService service = ColonyWebService.get();
        if (service == null) {
            ColonyWebCommand.reply(source, "Dashboard service is not running.");
            return 0;
        }
        if (!service.auth().enabled()) {
            source.sendSuccess(() -> Component.literal("Authentication is turned off — the dashboard is open to anyone: ")
                    .append(ColonyWebCommand.link(ColonyWebCommand.dashboardUrl())), false);
            return 1;
        }
        String code = sync(service, player.getUUID(), player.getGameProfile().getName(), isOperator(player));
        sendCode(source, code, colonyCount(service.auth(), player.getUUID()));
        return 1;
    }

    /** {@code /colonyweb sync <player>} — an operator issues a code for someone else. */
    static int syncOther(CommandSourceStack source, Collection<GameProfile> profiles) {
        ColonyWebService service = ColonyWebService.get();
        if (service == null || !service.auth().enabled()) {
            ColonyWebCommand.reply(source, "Authentication is not active.");
            return 0;
        }
        for (GameProfile profile : profiles) {
            boolean admin = source.getServer().getPlayerList().isOp(profile);
            String code = sync(service, profile.getId(), profile.getName(), admin);
            ColonyWebCommand.reply(source, profile.getName() + "'s sign-in code: " + code
                    + " (valid " + Config.loginCodeMinutes + " min, "
                    + colonyCount(service.auth(), profile.getId()) + " colonies)");

            // Deliver it privately when they are online, so it is not read off someone's screen.
            ServerPlayer online = source.getServer().getPlayerList().getPlayer(profile.getId());
            if (online != null) {
                sendCode(online.createCommandSourceStack(), code, colonyCount(service.auth(), profile.getId()));
            }
        }
        return profiles.size();
    }

    private static String sync(ColonyWebService service, UUID uuid, String name, boolean admin) {
        List<Integer> colonies = service.provider().coloniesFor(uuid, name);
        return service.auth().issueCode(uuid, name, colonies, admin);
    }

    private static void sendCode(CommandSourceStack source, String code, int colonies) {
        String url = ColonyWebCommand.dashboardUrl();
        source.sendSuccess(() -> Component.literal("Open ").append(ColonyWebCommand.link(url))
                .append(Component.literal(" and enter this code:")), false);
        source.sendSuccess(() -> Component.literal("  " + code).setStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to copy")))), false);
        source.sendSuccess(() -> Component.literal("Valid for " + Config.loginCodeMinutes
                + " minutes · unlocks " + colonies + " colony/colonies.").withStyle(ChatFormatting.GRAY), false);
    }

    // ------------------------------------------------------------------
    // Explicit grants
    // ------------------------------------------------------------------

    static int grant(CommandSourceStack source, Collection<GameProfile> profiles, int colonyId) {
        AuthService auth = auth(source);
        if (auth == null) {
            return 0;
        }
        for (GameProfile profile : profiles) {
            boolean added = auth.grant(profile.getId(), profile.getName(), colonyId);
            ColonyWebCommand.reply(source, added
                    ? "Granted " + profile.getName() + " access to colony " + colonyId + "."
                    : profile.getName() + " already had access to colony " + colonyId + ".");
        }
        return profiles.size();
    }

    static int revoke(CommandSourceStack source, Collection<GameProfile> profiles, int colonyId) {
        AuthService auth = auth(source);
        if (auth == null) {
            return 0;
        }
        for (GameProfile profile : profiles) {
            boolean removed = auth.revokeGrant(profile.getId(), colonyId);
            ColonyWebCommand.reply(source, removed
                    ? "Revoked " + profile.getName() + "'s grant for colony " + colonyId + "."
                    : profile.getName() + " had no explicit grant for colony " + colonyId + "."
                      + " (They may still reach it as a colony member — check /colonyweb access list.)");
        }
        return profiles.size();
    }

    static int list(CommandSourceStack source, Collection<GameProfile> profiles) {
        AuthService auth = auth(source);
        if (auth == null) {
            return 0;
        }
        for (GameProfile profile : profiles) {
            Optional<WebUser> found = auth.user(profile.getId());
            if (found.isEmpty()) {
                ColonyWebCommand.reply(source, profile.getName() + " has never run /colonyweb sync.");
                continue;
            }
            WebUser user = found.get();
            ColonyWebCommand.reply(source, profile.getName() + (user.admin ? " (operator — sees every colony)" : ""));
            ColonyWebCommand.reply(source, "  member of: " + describe(user.colonies));
            ColonyWebCommand.reply(source, "  granted:   " + describe(user.granted));
            ColonyWebCommand.reply(source, "  sessions:  " + user.sessions.size());
        }
        return profiles.size();
    }

    static int logout(CommandSourceStack source, Collection<GameProfile> profiles) {
        AuthService auth = auth(source);
        if (auth == null) {
            return 0;
        }
        for (GameProfile profile : profiles) {
            int closed = auth.revokeAllSessions(profile.getId());
            ColonyWebCommand.reply(source, "Signed " + profile.getName() + " out of " + closed + " session(s).");
        }
        return profiles.size();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static AuthService auth(CommandSourceStack source) {
        ColonyWebService service = ColonyWebService.get();
        if (service == null) {
            ColonyWebCommand.reply(source, "Dashboard service is not running.");
            return null;
        }
        return service.auth();
    }

    private static int colonyCount(AuthService auth, UUID uuid) {
        return auth.user(uuid).map(user -> user.accessibleColonies().size()).orElse(0);
    }

    private static boolean isOperator(ServerPlayer player) {
        return player.getServer() != null && player.getServer().getPlayerList().isOp(player.getGameProfile());
    }

    private static String describe(Collection<Integer> ids) {
        return ids.isEmpty() ? "none"
                : ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
