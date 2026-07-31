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
    var colonies: MutableSet<Int> = LinkedHashSet(),
    var granted: MutableSet<Int> = LinkedHashSet(),
    var admin: Boolean = false,
    var syncedAt: Long = 0L,
    var sessions: MutableList<StoredSession> = ArrayList()
) {
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
