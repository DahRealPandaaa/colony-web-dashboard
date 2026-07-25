package DahRealPanda.plugins.untitled1.web.handlers;

import DahRealPanda.plugins.untitled1.web.JsonUtil;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * Serves static front-end assets from the {@code webroot/} classpath resources.
 */
public final class StaticHandler implements HttpHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String ROOT = "webroot";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonUtil.sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }
            // Reject path traversal.
            if (path.contains("..")) {
                JsonUtil.sendText(exchange, 400, "Bad Request");
                return;
            }
            String resource = ROOT + path;
            byte[] bytes = read(resource);
            if (bytes == null) {
                JsonUtil.sendText(exchange, 404, "Not Found");
                return;
            }
            JsonUtil.sendBytes(exchange, 200, contentType(path), bytes);
        } catch (Exception e) {
            LOGGER.debug("[ColonyWeb] static handler error", e);
            try {
                JsonUtil.sendText(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {
                // no-op
            }
        }
    }

    private byte[] read(String resource) throws IOException {
        ClassLoader cl = StaticHandler.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            return in != null ? in.readAllBytes() : null;
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }
}
