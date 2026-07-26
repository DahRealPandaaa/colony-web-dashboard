package DahRealPanda.plugins.colonyweb.auth;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.Config;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns dashboard authentication: pairing codes issued in-game, browser sessions, and which
 * colonies each player is allowed to see.
 *
 * <p>The flow is deliberately password-free — a player proves who they are by already being
 * logged into the Minecraft server:</p>
 * <ol>
 *   <li>{@code /colonyweb sync} issues a short, single-use code bound to their UUID.</li>
 *   <li>They type the code into the dashboard, which exchanges it for a session cookie.</li>
 *   <li>Every API request is then resolved back to that player and filtered to their colonies.</li>
 * </ol>
 *
 * <p>All methods are safe to call from both the server thread (commands) and HTTP threads.</p>
 */
public final class AuthService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Excludes vowels and look-alike characters so codes are easy to read off chat. */
    private static final String CODE_ALPHABET = "23456789BCDFGHJKLMNPQRSTVWXZ";
    private static final int CODE_HALF_LENGTH = 4;
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthStore store;
    private final Map<String, WebUser> users;
    /** Outstanding pairing codes, keyed by the normalized code. Never persisted. */
    private final Map<String, PendingCode> pendingCodes = new ConcurrentHashMap<>();

    public AuthService(Path dataDir) {
        this.store = new AuthStore(dataDir);
        this.users = new ConcurrentHashMap<>(store.load());
        purgeExpiredSessions();
        LOGGER.info("{} auth {} — {} known account(s)", ColonyWeb.LOG,
                enabled() ? "enabled" : "DISABLED (open dashboard)", users.size());
    }

    /** @return false when the operator turned authentication off in the config. */
    public boolean enabled() {
        return Config.authEnabled;
    }

    // ------------------------------------------------------------------
    // Pairing codes
    // ------------------------------------------------------------------

    /**
     * Record a player's colony access and issue a fresh single-use pairing code. Any code
     * previously issued to this player is invalidated.
     *
     * @return the code to type into the dashboard, formatted {@code XXXX-XXXX}
     */
    public String issueCode(UUID uuid, String name, Collection<Integer> colonies, boolean admin) {
        WebUser user = users.computeIfAbsent(key(uuid), id -> new WebUser(id, name));
        user.name = name;
        user.colonies = new LinkedHashSet<>(colonies);
        user.admin = admin;
        user.syncedAt = System.currentTimeMillis();

        pendingCodes.values().removeIf(pending -> pending.uuid.equals(key(uuid)));
        String code = randomCode();
        pendingCodes.put(normalize(code),
                new PendingCode(key(uuid), System.currentTimeMillis() + Config.loginCodeMinutes * 60_000L));

        save();
        return code;
    }

    /**
     * Exchange a pairing code for a session token. The code is consumed either way.
     *
     * @return the raw session token to hand to the browser, or empty when the code is unknown
     *         or expired
     */
    public Optional<String> redeemCode(String rawCode) {
        purgeExpiredCodes();
        PendingCode pending = pendingCodes.remove(normalize(rawCode));
        if (pending == null || pending.expiresAt <= System.currentTimeMillis()) {
            return Optional.empty();
        }
        WebUser user = users.get(pending.uuid);
        if (user == null) {
            return Optional.empty();
        }
        String token = randomToken();
        long now = System.currentTimeMillis();
        user.sessions.add(new StoredSession(hash(token), now, now + Config.sessionDays * 86_400_000L));
        save();
        LOGGER.info("{} {} signed in to the dashboard", ColonyWeb.LOG, user.name);
        return Optional.of(token);
    }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    /** Resolve a session token back to its account, or empty when unknown/expired. */
    public Optional<WebUser> userForToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hash(token);
        long now = System.currentTimeMillis();
        for (WebUser user : users.values()) {
            for (StoredSession session : user.sessions) {
                if (session.expiresAt > now && session.tokenHash.equals(tokenHash)) {
                    return Optional.of(user);
                }
            }
        }
        return Optional.empty();
    }

    /** Drop just the session behind this token (a single browser signing out). */
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String tokenHash = hash(token);
        boolean changed = false;
        for (WebUser user : users.values()) {
            changed |= user.sessions.removeIf(session -> session.tokenHash.equals(tokenHash));
        }
        if (changed) {
            save();
        }
    }

    /** Sign a player out everywhere and cancel any pending code. */
    public int revokeAllSessions(UUID uuid) {
        WebUser user = users.get(key(uuid));
        pendingCodes.values().removeIf(pending -> pending.uuid.equals(key(uuid)));
        if (user == null) {
            return 0;
        }
        int count = user.sessions.size();
        user.sessions.clear();
        save();
        return count;
    }

    // ------------------------------------------------------------------
    // Access control
    // ------------------------------------------------------------------

    /** @return true when this user may see the given colony. */
    public boolean canAccess(WebUser user, int colonyId) {
        if (!enabled()) {
            return true;
        }
        return user != null && (user.admin || user.accessibleColonies().contains(colonyId));
    }

    /** Add an explicit grant that survives future syncs. */
    public boolean grant(UUID uuid, String name, int colonyId) {
        WebUser user = users.computeIfAbsent(key(uuid), id -> new WebUser(id, name));
        if (name != null && !name.isBlank()) {
            user.name = name;
        }
        boolean added = user.granted.add(colonyId);
        if (added) {
            save();
        }
        return added;
    }

    /** Remove an explicit grant. Colonies the player belongs to in-game are unaffected. */
    public boolean revokeGrant(UUID uuid, int colonyId) {
        WebUser user = users.get(key(uuid));
        if (user == null || !user.granted.remove(colonyId)) {
            return false;
        }
        save();
        return true;
    }

    public Optional<WebUser> user(UUID uuid) {
        return Optional.ofNullable(users.get(key(uuid)));
    }

    /** All known accounts, for {@code /colonyweb access list}. */
    public List<WebUser> allUsers() {
        return new ArrayList<>(users.values());
    }

    // ------------------------------------------------------------------
    // Housekeeping
    // ------------------------------------------------------------------

    /** Drop expired sessions and codes. Called on load and periodically by the scheduler. */
    public void purgeExpiredSessions() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (WebUser user : users.values()) {
            changed |= user.purgeExpiredSessions(now);
        }
        purgeExpiredCodes();
        if (changed) {
            save();
        }
    }

    /** Number of pairing codes waiting to be typed in — surfaced by {@code /colonyweb status}. */
    public int pendingCodeCount() {
        purgeExpiredCodes();
        return pendingCodes.size();
    }

    public int sessionCount() {
        return users.values().stream().mapToInt(user -> user.sessions.size()).sum();
    }

    private void purgeExpiredCodes() {
        long now = System.currentTimeMillis();
        pendingCodes.values().removeIf(pending -> pending.expiresAt <= now);
    }

    private void save() {
        store.save(users);
    }

    // ------------------------------------------------------------------
    // Crypto / formatting helpers
    // ------------------------------------------------------------------

    private static String key(UUID uuid) {
        return uuid.toString().toLowerCase(Locale.ROOT);
    }

    /** Codes are compared case-insensitively and ignoring the dash, so typing is forgiving. */
    private static String normalize(String code) {
        return code == null ? "" : code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_HALF_LENGTH * 2 + 1);
        for (int i = 0; i < CODE_HALF_LENGTH * 2; i++) {
            if (i == CODE_HALF_LENGTH) {
                sb.append('-');
            }
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** An outstanding pairing code. Held in memory only — a restart cancels pending logins. */
    private record PendingCode(String uuid, long expiresAt) {
    }
}
