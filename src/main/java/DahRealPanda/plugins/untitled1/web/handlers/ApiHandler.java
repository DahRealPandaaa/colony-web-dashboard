package DahRealPanda.plugins.untitled1.web.handlers;

import DahRealPanda.plugins.untitled1.colony.ColonyCache;
import DahRealPanda.plugins.untitled1.colony.model.ColonySnapshot;
import DahRealPanda.plugins.untitled1.web.JsonUtil;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Optional;

/**
 * Serves {@code /api/colonies} and {@code /api/colony/{id}} from the cached scan results.
 */
public final class ApiHandler implements HttpHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ColonyCache cache;

    public ApiHandler(ColonyCache cache) {
        this.cache = cache;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonUtil.sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/colonies")) {
                JsonUtil.sendJson(exchange, 200, cache.summaries());
                return;
            }
            if (path.startsWith("/api/colony/")) {
                String idPart = path.substring("/api/colony/".length());
                int id;
                try {
                    id = Integer.parseInt(idPart);
                } catch (NumberFormatException e) {
                    JsonUtil.sendText(exchange, 400, "Invalid colony id");
                    return;
                }
                Optional<ColonySnapshot> snap = cache.snapshot(id);
                if (snap.isPresent()) {
                    JsonUtil.sendJson(exchange, 200, snap.get());
                } else {
                    JsonUtil.sendText(exchange, 404, "Unknown colony");
                }
                return;
            }
            JsonUtil.sendText(exchange, 404, "Not Found");
        } catch (Exception e) {
            LOGGER.debug("[ColonyWeb] api handler error", e);
            safeError(exchange);
        }
    }

    private void safeError(HttpExchange exchange) {
        try {
            JsonUtil.sendText(exchange, 500, "Internal Server Error");
        } catch (IOException ignored) {
            // no-op
        }
    }
}
