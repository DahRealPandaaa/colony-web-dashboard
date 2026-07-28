package DahRealPanda.plugins.colonyweb.web.handlers;

import DahRealPanda.plugins.colonyweb.auth.AuthService;
import DahRealPanda.plugins.colonyweb.auth.SessionCookie;
import DahRealPanda.plugins.colonyweb.auth.WebUser;
import DahRealPanda.plugins.colonyweb.web.JsonUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Optional;

/**
 * The one place handlers turn a request into a signed-in player.
 */
final class RequestAuth {
    static final String SIGN_IN_HINT = "Sign in with a code from /colonyweb sync.";

    private RequestAuth() {
    }

    /** The player behind this request, or empty when the browser has no valid session. */
    static Optional<WebUser> user(AuthService auth, HttpExchange exchange) {
        return SessionCookie.read(exchange).flatMap(auth::userForToken);
    }

    /**
     * Guard an endpoint that requires a session.
     *
     * @return true when the request was rejected (a 401 has already been written, and the
     *         caller must stop); false when it may proceed.
     */
    static boolean rejectUnauthenticated(AuthService auth, HttpExchange exchange) throws IOException {
        if (!auth.enabled() || user(auth, exchange).isPresent()) {
            return false;
        }
        JsonUtil.sendError(exchange, 401, SIGN_IN_HINT);
        return true;
    }
}
