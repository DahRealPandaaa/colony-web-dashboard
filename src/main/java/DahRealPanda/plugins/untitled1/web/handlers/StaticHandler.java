package DahRealPanda.plugins.untitled1.web.handlers;

import DahRealPanda.plugins.untitled1.web.JsonUtil;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
            String etag = etag(bytes);
            exchange.getResponseHeaders().set("Cache-Control", cacheControl(path));
            exchange.getResponseHeaders().set("ETag", etag);
            if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
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

    private static String cacheControl(String path) {
        if (path.endsWith(".html")) {
            return "no-cache";
        }
        if (path.endsWith(".js") || path.endsWith(".css")) {
            return "public, max-age=86400, stale-while-revalidate=604800";
        }
        return "public, max-age=604800, stale-while-revalidate=86400";
    }

    private static String etag(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return "\"" + HexFormat.of().formatHex(digest) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
