package DahRealPanda.plugins.untitled1.web.handlers;

import DahRealPanda.plugins.untitled1.texture.TextureService;
import DahRealPanda.plugins.untitled1.web.JsonUtil;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Serves PNG icons on {@code /textures/{key}.png}. The key is URL-encoded (the {@code #} in
 * Domum Ornamentum variant keys must be percent-encoded by the client).
 */
public final class TextureHandler implements HttpHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final TextureService textureService;

    public TextureHandler(TextureService textureService) {
        this.textureService = textureService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonUtil.sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            String prefix = "/textures/";
            if (!path.startsWith(prefix) || !path.endsWith(".png")) {
                JsonUtil.sendText(exchange, 404, "Not Found");
                return;
            }
            String encodedKey = path.substring(prefix.length(), path.length() - ".png".length());
            String key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
            byte[] png = textureService.getPng(key);
            String etag = etag(png);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=604800, stale-while-revalidate=86400");
            exchange.getResponseHeaders().set("ETag", etag);
            if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }
            JsonUtil.sendBytes(exchange, 200, "image/png", png);
        } catch (Exception e) {
            LOGGER.debug("[ColonyWeb] texture handler error", e);
            try {
                JsonUtil.sendText(exchange, 500, "Internal Server Error");
            } catch (IOException ignored) {
                // no-op
            }
        }
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
