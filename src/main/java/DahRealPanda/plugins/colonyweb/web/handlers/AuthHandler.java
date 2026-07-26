package DahRealPanda.plugins.colonyweb.web.handlers;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.Config;
import DahRealPanda.plugins.colonyweb.auth.AuthService;
import DahRealPanda.plugins.colonyweb.auth.SessionCookie;
import DahRealPanda.plugins.colonyweb.auth.WebUser;
import DahRealPanda.plugins.colonyweb.web.JsonUtil;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sign-in endpoints.
 *
 * <pre>
 * GET  /auth/me      current session state (always 200 — the page uses it to decide what to show)
 * POST /auth/login   {"code":"XXXX-XXXX"} -> sets the session cookie
 * POST /auth/logout  ends this browser's session
 * </pre>
 */
public final class AuthHandler implements HttpHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AuthService auth;

    public AuthHandler(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            switch (path) {
                case "/auth/me" -> me(exchange);
                case "/auth/login" -> login(exchange);
                case "/auth/logout" -> logout(exchange);
                default -> JsonUtil.sendError(exchange, 404, "Not Found");
            }
        } catch (Exception e) {
            LOGGER.debug("{} auth handler error", ColonyWeb.LOG, e);
            try {
                JsonUtil.sendError(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {
                // no-op
            }
        }
    }

    private void me(HttpExchange exchange) throws IOException {
        Optional<WebUser> user = SessionCookie.read(exchange).flatMap(auth::userForToken);
        JsonUtil.sendJson(exchange, 200, sessionState(user.orElse(null)));
    }

    private void login(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonUtil.sendError(exchange, 405, "Method Not Allowed");
            return;
        }
        if (!auth.enabled()) {
            JsonUtil.sendJson(exchange, 200, sessionState(null));
            return;
        }
        JsonObject body = JsonUtil.readJsonBody(exchange);
        String code = JsonUtil.stringField(body, "code");
        Optional<String> token = code == null ? Optional.empty() : auth.redeemCode(code);
        if (token.isEmpty()) {
            JsonUtil.sendError(exchange, 401, "That code is not valid any more. Run /colonyweb sync in-game for a new one.");
            return;
        }
        SessionCookie.set(exchange, token.get(), Config.sessionDays * 86_400L);
        JsonUtil.sendJson(exchange, 200, sessionState(auth.userForToken(token.get()).orElse(null)));
    }

    private void logout(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonUtil.sendError(exchange, 405, "Method Not Allowed");
            return;
        }
        SessionCookie.read(exchange).ifPresent(auth::revokeToken);
        SessionCookie.clear(exchange);
        JsonUtil.sendJson(exchange, 200, sessionState(null));
    }

    /** The shape {@code /auth/me}, login and logout all return. */
    private Map<String, Object> sessionState(WebUser user) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("authEnabled", auth.enabled());
        state.put("authenticated", !auth.enabled() || user != null);
        if (user != null) {
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("uuid", user.uuid);
            profile.put("name", user.name);
            profile.put("admin", user.admin);
            profile.put("colonyCount", user.accessibleColonies().size());
            state.put("user", profile);
        }
        return state;
    }
}
