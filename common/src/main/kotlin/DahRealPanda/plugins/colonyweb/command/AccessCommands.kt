package DahRealPanda.plugins.colonyweb.command

import DahRealPanda.plugins.colonyweb.ColonyWebService
import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.auth.WebUser
import com.mojang.authlib.GameProfile
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer

/**
 * Bodies of the `/colonyweb sync`, `access` and `logout` subcommands.
 *
 * These run on the server thread, which is exactly where colony membership has to be read
 * from — so a sync always reflects the colonies as they stand right now.
 */
internal object AccessCommands {
    // ------------------------------------------------------------------
    // Sync — mirror a player's colonies and hand them a pairing code
    // ------------------------------------------------------------------

    /** `/colonyweb sync` — the player signs themselves in. */
    fun syncSelf(source: CommandSourceStack): Int {
        val player = source.entity as? ServerPlayer
            ?: run {
                ColonyWebCommand.reply(source, "Only a player can sync themselves. Use /colonyweb sync <player>.")
                return 0
            }
        val service = ColonyWebService.get() ?: run {
            ColonyWebCommand.reply(source, "Dashboard service is not running.")
            return 0
        }
        if (!service.auth.enabled()) {
            source.sendSuccess({
                Component.literal("Authentication is turned off — the dashboard is open to anyone: ")
                    .append(ColonyWebCommand.link(ColonyWebCommand.dashboardUrl()))
            }, false)
            return 1
        }
        val code = sync(service, player.uuid, player.gameProfile.name, isOperator(player))
        sendCode(source, code, colonyCount(service.auth, player.uuid))
        return 1
    }

    /** `/colonyweb sync <player>` — an operator issues a code for someone else. */
    fun syncOther(source: CommandSourceStack, profiles: Collection<GameProfile>): Int {
        val service = ColonyWebService.get() ?: run {
            ColonyWebCommand.reply(source, "Authentication is not active.")
            return 0
        }
        if (!service.auth.enabled()) {
            ColonyWebCommand.reply(source, "Authentication is not active.")
            return 0
        }
        for (profile in profiles) {
            val admin = source.server?.playerList?.isOp(profile) ?: false
            val code = sync(service, profile.id, profile.name, admin)
            ColonyWebCommand.reply(source, profile.name + "'s sign-in code: " + code
                + " (valid " + Config.loginCodeMinutes + " min, "
                + colonyCount(service.auth, profile.id) + " colonies)")

            // Deliver it privately when they are online, so it is not read off someone's screen.
            val online = source.server?.playerList?.getPlayer(profile.id)
            if (online != null) {
                sendCode(online.createCommandSourceStack(), code, colonyCount(service.auth, profile.id))
            }
        }
        return profiles.size
    }

    private fun sync(service: ColonyWebService, uuid: java.util.UUID, name: String, admin: Boolean): String {
        val colonies = service.scanFacade.coloniesFor(uuid, name)
        return service.auth.issueCode(uuid, name, colonies, admin)
    }

    private fun sendCode(source: CommandSourceStack, code: String, colonies: Int) {
        val url = ColonyWebCommand.dashboardUrl()
        source.sendSuccess({
            Component.literal("Open ").append(ColonyWebCommand.link(url))
                .append(Component.literal(" and enter this code:"))
        }, false)
        source.sendSuccess({
            Component.literal("  $code").setStyle(
                Style.EMPTY
                    .withColor(ChatFormatting.GOLD)
                    .withBold(true)
                    .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                    .withHoverEvent(
                        HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to copy")
                        )
                    )
            )
        }, false)
        source.sendSuccess({
            Component.literal("Valid for " + Config.loginCodeMinutes
                + " minutes · unlocks " + colonies + " colony/colonies.")
                .withStyle(ChatFormatting.GRAY)
        }, false)
    }

    // ------------------------------------------------------------------
    // Explicit grants
    // ------------------------------------------------------------------

    fun grant(source: CommandSourceStack, profiles: Collection<GameProfile>, colonyId: Int): Int {
        val auth = auth(source) ?: return 0
        for (profile in profiles) {
            val added = auth.grant(profile.id, profile.name, colonyId)
            ColonyWebCommand.reply(source, if (added)
                "Granted " + profile.name + " access to colony " + colonyId + "."
            else
                profile.name + " already had access to colony " + colonyId + ".")
        }
        return profiles.size
    }

    fun revoke(source: CommandSourceStack, profiles: Collection<GameProfile>, colonyId: Int): Int {
        val auth = auth(source) ?: return 0
        for (profile in profiles) {
            val removed = auth.revokeGrant(profile.id, colonyId)
            ColonyWebCommand.reply(source, if (removed)
                "Revoked " + profile.name + "'s grant for colony " + colonyId + "."
            else
                profile.name + " had no explicit grant for colony " + colonyId + "."
                    + " (They may still reach it as a colony member — check /colonyweb access list.)")
        }
        return profiles.size
    }

    fun list(source: CommandSourceStack, profiles: Collection<GameProfile>): Int {
        val auth = auth(source) ?: return 0
        for (profile in profiles) {
            val found = auth.user(profile.id)
            if (found == null) {
                ColonyWebCommand.reply(source, profile.name + " has never run /colonyweb sync.")
                continue
            }
            ColonyWebCommand.reply(source, profile.name + if (found.admin) " (operator — sees every colony)" else "")
            ColonyWebCommand.reply(source, "  member of: " + describe(found.colonies))
            ColonyWebCommand.reply(source, "  granted:   " + describe(found.granted))
            ColonyWebCommand.reply(source, "  sessions:  " + found.sessions.size)
        }
        return profiles.size
    }

    fun logout(source: CommandSourceStack, profiles: Collection<GameProfile>): Int {
        val auth = auth(source) ?: return 0
        for (profile in profiles) {
            val closed = auth.revokeAllSessions(profile.id)
            ColonyWebCommand.reply(source, "Signed " + profile.name + " out of " + closed + " session(s).")
        }
        return profiles.size
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun auth(source: CommandSourceStack): AuthService? {
        val service = ColonyWebService.get()
        if (service == null) {
            ColonyWebCommand.reply(source, "Dashboard service is not running.")
            return null
        }
        return service.auth
    }

    private fun colonyCount(auth: AuthService, uuid: java.util.UUID): Int {
        return auth.user(uuid)?.accessibleColonies()?.size ?: 0
    }

    private fun isOperator(player: ServerPlayer): Boolean {
        return player.server != null && player.server!!.playerList.isOp(player.gameProfile)
    }

    private fun describe(ids: Collection<Int>): String {
        return if (ids.isEmpty()) "none"
        else ids.joinToString(", ")
    }
}
