package DahRealPanda.plugins.colonyweb.auth

/**
 * A persisted browser session. The session token itself is never stored — only its SHA-256
 * hash — so a leaked [auth.json] cannot be replayed as a login.
 */
class StoredSession {
    var tokenHash: String = ""
    var createdAt: Long = 0L
    var expiresAt: Long = 0L

    constructor()

    constructor(tokenHash: String, createdAt: Long, expiresAt: Long) {
        this.tokenHash = tokenHash
        this.createdAt = createdAt
        this.expiresAt = expiresAt
    }
}
