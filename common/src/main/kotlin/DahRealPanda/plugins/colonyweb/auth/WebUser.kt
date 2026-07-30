package DahRealPanda.plugins.colonyweb.auth

import java.util.LinkedHashSet

/**
 * A Minecraft player's dashboard account: which colonies they may view, and their live
 * browser sessions.
 *
 * Two sources of access are tracked separately so a re-sync never silently drops an
 * operator's explicit grant:
 *
 *  * [colonies] — mirrored from MineColonies permissions by `/colonyweb sync`
 *    and replaced wholesale on every sync.
 *  * [granted] — added by hand with `/colonyweb access grant` and only ever
 *    removed by `/colonyweb access revoke`.
 */
data class WebUser(
    var uuid: String = "",
    var name: String = "",
    /** Colonies the player belongs to in-game (owner/officer/member). Replaced on each sync. */
    var colonies: MutableSet<Int> = LinkedHashSet(),
    /** Colonies an operator granted explicitly. Survives re-syncs. */
    var granted: MutableSet<Int> = LinkedHashSet(),
    /** Server operators see every colony. */
    var admin: Boolean = false,
    /** Epoch millis of the last `/colonyweb sync`. */
    var syncedAt: Long = 0L,
    /** Active browser sessions. Only token hashes are stored, never the tokens themselves. */
    var sessions: MutableList<StoredSession> = ArrayList()
) {
    /** Every colony id this user may view (ignored when [admin] is set). */
    fun accessibleColonies(): Set<Int> {
        val all = LinkedHashSet(colonies)
        all.addAll(granted)
        return all
    }

    /** Remove sessions that have expired, and report whether anything changed. */
    fun purgeExpiredSessions(now: Long): Boolean {
        return sessions.removeIf { session -> session.expiresAt <= now }
    }
}
