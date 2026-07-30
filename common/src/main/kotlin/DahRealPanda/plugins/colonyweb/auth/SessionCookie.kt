package DahRealPanda.plugins.colonyweb.auth

import com.sun.net.httpserver.HttpExchange

/**
 * Reads and writes the dashboard's session cookie.
 *
 * The cookie is `HttpOnly` (so page scripts — and anything injected into them —
 * cannot read the token) and `SameSite=Lax` (so another site cannot ride the session
 * with a cross-origin request).
 */
object SessionCookie {
    const val NAME = "colonyweb_session"

    /** The session token sent by the browser, if any. */
    @JvmStatic
    fun read(exchange: HttpExchange): String? {
        val headers = exchange.requestHeaders["Cookie"] ?: return null
        for (header in headers) {
            for (part in header.split(";")) {
                val trimmed = part.trim()
                if (trimmed.startsWith("$NAME=")) {
                    val value = trimmed.substring(NAME.length + 1)
                    return if (value.isBlank()) null else value
                }
            }
        }
        return null
    }

    /** Issue the session cookie for [maxAgeSeconds]. */
    @JvmStatic
    fun set(exchange: HttpExchange, token: String, maxAgeSeconds: Long) {
        exchange.responseHeaders.add(
            "Set-Cookie",
            "$NAME=$token; Path=/; Max-Age=$maxAgeSeconds; HttpOnly; SameSite=Lax"
        )
    }

    /** Expire the session cookie immediately. */
    @JvmStatic
    fun clear(exchange: HttpExchange) {
        exchange.responseHeaders.add(
            "Set-Cookie",
            "$NAME=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"
        )
    }
}
