package DahRealPanda.plugins.colonyweb.auth

/**
 * A persisted browser session. The session token itself is never stored — only its SHA-256
 * hash — so a leaked [auth.json] cannot be replayed as a login.
 */
class StoredSession {
    val tokenHash: String
    val createdAt: Long
    val expiresAt: Long

    constructor() {
        tokenHash = ""
        createdAt = 0L
        expiresAt = 0L
    }

    constructor(tokenHash: String, createdAt: Long, expiresAt: Long) {
        this.tokenHash = tokenHash
        this.createdAt = createdAt
        this.expiresAt = expiresAt
    }
}
