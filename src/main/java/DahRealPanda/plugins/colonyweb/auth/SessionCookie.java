package DahRealPanda.plugins.colonyweb.auth;

import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Optional;

/**
 * Reads and writes the dashboard's session cookie.
 *
 * <p>The cookie is {@code HttpOnly} (so page scripts — and anything injected into them —
 * cannot read the token) and {@code SameSite=Lax} (so another site cannot ride the session
 * with a cross-origin request).</p>
 */
public final class SessionCookie {
    public static final String NAME = "colonyweb_session";

    private SessionCookie() {
    }

    /** The session token sent by the browser, if any. */
    public static Optional<String> read(HttpExchange exchange) {
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return Optional.empty();
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(NAME + "=")) {
                    String value = trimmed.substring(NAME.length() + 1);
                    return value.isBlank() ? Optional.empty() : Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    /** Issue the session cookie for {@code maxAgeSeconds}. */
    public static void set(HttpExchange exchange, String token, long maxAgeSeconds) {
        exchange.getResponseHeaders().add("Set-Cookie",
                NAME + "=" + token + "; Path=/; Max-Age=" + maxAgeSeconds + "; HttpOnly; SameSite=Lax");
    }

    /** Expire the session cookie immediately. */
    public static void clear(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Set-Cookie",
                NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
    }
}
