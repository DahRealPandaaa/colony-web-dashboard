package DahRealPanda.plugins.colonyweb.auth;

/**
 * A persisted browser session. The session token itself is never stored — only its SHA-256
 * hash — so a leaked {@code auth.json} cannot be replayed as a login.
 */
public class StoredSession {
    public String tokenHash;
    public long createdAt;
    public long expiresAt;

    public StoredSession() {
    }

    public StoredSession(String tokenHash, long createdAt, long expiresAt) {
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
