package DahRealPanda.plugins.colonyweb.auth

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.repository.AuthStore
import com.mojang.logging.LogUtils
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import java.util.LinkedHashSet
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AuthService(dataDir: Path) {
    private class PendingCode(val uuid: String, val expiresAt: Long)

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()

        /** Excludes vowels and look-alike characters so codes are easy to read off chat. */
        private const val CODE_ALPHABET = "23456789BCDFGHJKLMNPQRSTVWXZ"
        private const val CODE_HALF_LENGTH = 4
        private const val TOKEN_BYTES = 32

        private val RANDOM = SecureRandom()
    }

    private val store: AuthStore = AuthStore(dataDir.resolve("auth.json"))
    private val users: MutableMap<String, WebUser> = ConcurrentHashMap(store.load())
    private val pendingCodes: MutableMap<String, PendingCode> = ConcurrentHashMap()

    init {
        purgeExpiredSessions()
        LOGGER.info(
            "{} auth {} — {} known account(s)",
            ColonyWeb.LOG,
            if (enabled()) "enabled" else "DISABLED (open dashboard)",
            users.size
        )
    }

    fun enabled(): Boolean = Config.authEnabled

    fun issueCode(uuid: java.util.UUID, name: String, colonies: Collection<Int>, admin: Boolean): String {
        val user = users.computeIfAbsent(key(uuid)) { id -> WebUser(uuid = id, name = name) }
        user.name = name
        user.colonies = LinkedHashSet(colonies)
        user.admin = admin
        user.syncedAt = System.currentTimeMillis()

        pendingCodes.values.removeIf { pending -> pending.uuid == key(uuid) }
        val code = randomCode()
        pendingCodes[normalize(code)] = PendingCode(
            key(uuid),
            System.currentTimeMillis() + Config.loginCodeMinutes * 60_000L
        )

        save()
        return code
    }

    fun redeemCode(rawCode: String): String? {
        purgeExpiredCodes()
        val pending = pendingCodes.remove(normalize(rawCode)) ?: return null
        if (pending.expiresAt <= System.currentTimeMillis()) return null
        val user = users[pending.uuid] ?: return null
        val token = randomToken()
        val now = System.currentTimeMillis()
        user.sessions.add(StoredSession(hash(token), now, now + Config.sessionDays * 86_400_000L))
        save()
        LOGGER.info("{} {} signed in to the dashboard", ColonyWeb.LOG, user.name)
        return token
    }

    fun userForToken(token: String?): WebUser? {
        if (token.isNullOrBlank()) return null
        val tokenHash = hash(token)
        val now = System.currentTimeMillis()
        for (user in users.values) {
            for (session in user.sessions) {
                if (session.expiresAt > now && session.tokenHash == tokenHash) {
                    return user
                }
            }
        }
        return null
    }

    fun revokeToken(token: String?) {
        if (token.isNullOrBlank()) return
        val tokenHash = hash(token)
        var changed = false
        for (user in users.values) {
            changed = changed or user.sessions.removeIf { session -> session.tokenHash == tokenHash }
        }
        if (changed) save()
    }

    fun revokeAllSessions(uuid: java.util.UUID): Int {
        val user = users[key(uuid)]
        pendingCodes.values.removeIf { pending -> pending.uuid == key(uuid) }
        if (user == null) return 0
        val count = user.sessions.size
        user.sessions.clear()
        save()
        return count
    }

    fun canAccess(user: WebUser?, colonyId: Int): Boolean {
        if (!enabled()) return true
        return user != null && (user.admin || user.accessibleColonies().contains(colonyId))
    }

    fun grant(uuid: java.util.UUID, name: String?, colonyId: Int): Boolean {
        val user = users.computeIfAbsent(key(uuid)) { id -> WebUser(uuid = id, name = name ?: "") }
        if (!name.isNullOrBlank()) {
            user.name = name
        }
        val added = user.granted.add(colonyId)
        if (added) save()
        return added
    }

    fun revokeGrant(uuid: java.util.UUID, colonyId: Int): Boolean {
        val user = users[key(uuid)] ?: return false
        if (!user.granted.remove(colonyId)) return false
        save()
        return true
    }

    fun user(uuid: java.util.UUID): WebUser? = users[key(uuid)]

    fun allUsers(): List<WebUser> = ArrayList(users.values)

    fun purgeExpiredSessions() {
        val now = System.currentTimeMillis()
        var changed = false
        for (user in users.values) {
            changed = changed or user.purgeExpiredSessions(now)
        }
        purgeExpiredCodes()
        if (changed) save()
    }

    fun pendingCodeCount(): Int {
        purgeExpiredCodes()
        return pendingCodes.size
    }

    fun sessionCount(): Int =
        users.values.sumOf { user -> user.sessions.size }

    private fun purgeExpiredCodes() {
        val now = System.currentTimeMillis()
        pendingCodes.values.removeIf { pending -> pending.expiresAt <= now }
    }

    private fun save() {
        store.save(users)
    }

    private fun key(uuid: java.util.UUID): String =
        uuid.toString().lowercase(Locale.ROOT)

    private fun normalize(code: String?): String =
        code?.replace("-", "")?.replace(" ", "")?.uppercase(Locale.ROOT) ?: ""

    private fun randomCode(): String {
        val sb = StringBuilder(CODE_HALF_LENGTH * 2 + 1)
        for (i in 0 until CODE_HALF_LENGTH * 2) {
            if (i == CODE_HALF_LENGTH) sb.append('-')
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)])
        }
        return sb.toString()
    }

    private fun randomToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        RANDOM.nextBytes(bytes)
        return HexFormat.of().formatHex(bytes)
    }

    private fun hash(value: String): String {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
            return HexFormat.of().formatHex(digest)
        } catch (e: Exception) {
            throw IllegalStateException("SHA-256 is unavailable", e)
        }
    }
}
