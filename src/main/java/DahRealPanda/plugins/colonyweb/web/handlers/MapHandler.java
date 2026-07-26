package DahRealPanda.plugins.colonyweb.web.handlers;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.auth.AuthService;
import DahRealPanda.plugins.colonyweb.auth.WebUser;
import DahRealPanda.plugins.colonyweb.map.ColonyMapService;
import DahRealPanda.plugins.colonyweb.web.JsonUtil;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * Serves a colony's rendered surface map on {@code /map/{colonyId}.png}.
 *
 * <p>Access is scoped exactly like the JSON API: a colony the player may not see answers 403
 * whether or not it exists. The image is re-rendered as chunks fill in, so the browser has to
 * cache-bust it with the {@code version} from {@code /api/colony/{id}/map} — hence the
 * {@code no-cache} revalidation rather than a long max-age.</p>
 */
public final class MapHandler implements HttpHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PREFIX = "/map/";
    private static final String SUFFIX = ".png";

    private final ColonyMapService maps;
    private final AuthService auth;

    public MapHandler(ColonyMapService maps, AuthService auth) {
        this.maps = maps;
        this.auth = auth;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonUtil.sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            if (RequestAuth.rejectUnauthenticated(auth, exchange)) {
                return;
            }
            WebUser user = RequestAuth.user(auth, exchange).orElse(null);

            Integer colonyId = colonyId(exchange.getRequestURI().getPath());
            if (colonyId == null) {
                JsonUtil.sendError(exchange, 404, "Not Found");
                return;
            }
            if (!auth.canAccess(user, colonyId)) {
                JsonUtil.sendError(exchange, 403, "You do not have access to that colony.");
                return;
            }
            byte[] png = maps.png(colonyId);
            if (png == null) {
                // Requesting it is what puts the colony on the mapping queue, so this is the
                // normal answer for the first few seconds after the map tab is opened.
                JsonUtil.sendError(exchange, 404, "The colony map has not been drawn yet.");
                return;
            }
            exchange.getResponseHeaders().set("Cache-Control", "private, no-cache");
            JsonUtil.sendBytes(exchange, 200, "image/png", png);
        } catch (Exception e) {
            LOGGER.debug("{} map handler error", ColonyWeb.LOG, e);
            safeError(exchange);
        }
    }

    /** {@code /map/7.png} -> 7, or null for anything else. */
    private static Integer colonyId(String path) {
        if (!path.startsWith(PREFIX) || !path.endsWith(SUFFIX)) {
            return null;
        }
        try {
            return Integer.valueOf(path.substring(PREFIX.length(), path.length() - SUFFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void safeError(HttpExchange exchange) {
        try {
            JsonUtil.sendError(exchange, 500, "Internal Server Error");
        } catch (IOException ignored) {
            // no-op
        }
    }
}
