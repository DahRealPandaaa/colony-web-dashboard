package DahRealPanda.plugins.colonyweb.auth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A Minecraft player's dashboard account: which colonies they may view, and their live
 * browser sessions.
 *
 * <p>Two sources of access are tracked separately so a re-sync never silently drops an
 * operator's explicit grant:</p>
 * <ul>
 *   <li>{@link #colonies} — mirrored from MineColonies permissions by {@code /colonyweb sync}
 *       and replaced wholesale on every sync.</li>
 *   <li>{@link #granted} — added by hand with {@code /colonyweb access grant} and only ever
 *       removed by {@code /colonyweb access revoke}.</li>
 * </ul>
 */
public class WebUser {
    public String uuid;
    public String name;

    /** Colonies the player belongs to in-game (owner/officer/member). Replaced on each sync. */
    public Set<Integer> colonies = new LinkedHashSet<>();

    /** Colonies an operator granted explicitly. Survives re-syncs. */
    public Set<Integer> granted = new LinkedHashSet<>();

    /** Server operators see every colony. */
    public boolean admin;

    /** Epoch millis of the last {@code /colonyweb sync}. */
    public long syncedAt;

    /** Active browser sessions. Only token hashes are stored, never the tokens themselves. */
    public List<StoredSession> sessions = new ArrayList<>();

    public WebUser() {
    }

    public WebUser(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    /** Every colony id this user may view (ignored when {@link #admin} is set). */
    public Set<Integer> accessibleColonies() {
        Set<Integer> all = new LinkedHashSet<>(colonies);
        all.addAll(granted);
        return all;
    }

    /** Remove sessions that have expired, and report whether anything changed. */
    public boolean purgeExpiredSessions(long now) {
        return sessions.removeIf(session -> session.expiresAt <= now);
    }
}
