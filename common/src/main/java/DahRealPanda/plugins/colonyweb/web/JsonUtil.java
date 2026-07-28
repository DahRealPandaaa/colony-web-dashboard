package DahRealPanda.plugins.colonyweb.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shared Gson instance and small HTTP request/response helpers.
 */
public final class JsonUtil {
    public static final Gson GSON = new GsonBuilder().create();

    /** Refuse absurd request bodies outright — every endpoint here takes a tiny JSON object. */
    private static final int MAX_BODY_BYTES = 8 * 1024;

    private JsonUtil() {
    }

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    /** Parse a small JSON request body, returning an empty object on anything unparseable. */
    public static JsonObject readJsonBody(HttpExchange exchange) {
        try (InputStream in = exchange.getRequestBody()) {
            String body = new String(in.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return new JsonObject();
            }
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    /** Read a string field from a parsed body, or null when absent / not a string. */
    public static String stringField(JsonObject body, String field) {
        if (body == null || !body.has(field) || !body.get(field).isJsonPrimitive()) {
            return null;
        }
        return body.get(field).getAsString();
    }

    /** Write a JSON body with the given status. */
    public static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Write a JSON {@code {"error": "..."}} body — the shape the front-end expects. */
    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message));
    }

    /** Write raw bytes with a content type. */
    public static void sendBytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Write a short plain-text response. */
    public static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
