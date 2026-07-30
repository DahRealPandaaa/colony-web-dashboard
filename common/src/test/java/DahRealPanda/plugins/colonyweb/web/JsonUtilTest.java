package DahRealPanda.plugins.colonyweb.web;

import DahRealPanda.plugins.colonyweb.testsupport.FakeHttpExchange;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON request/response plumbing every endpoint sits on.
 *
 * <p>Request bodies arrive from a browser and are therefore hostile input: the parser has to
 * turn anything it cannot understand into an empty object rather than an exception, and has to
 * refuse a body large enough to be a denial-of-service attempt.</p>
 */
class JsonUtilTest {

    @Nested
    @DisplayName("reading a request body")
    class ReadingBodies {

        @Test
        @DisplayName("parses a normal object")
        void parsesObject() {
            JsonObject body = JsonUtil.readJsonBody(FakeHttpExchange.postJson("{\"code\":\"ABCD-EFGH\"}"));

            assertEquals("ABCD-EFGH", body.get("code").getAsString());
        }

        @ParameterizedTest
        @DisplayName("anything unparseable becomes an empty object")
        @ValueSource(strings = {
                "",
                "   ",
                "not json at all",
                "{",
                "{\"unterminated\": ",
                "\u0000\u0001binary",
        })
        void unparseableIsEmpty(String body) {
            assertTrue(JsonUtil.readJsonBody(FakeHttpExchange.postJson(body)).entrySet().isEmpty());
        }

        @ParameterizedTest
        @DisplayName("valid JSON that is not an object becomes an empty object")
        @ValueSource(strings = {"[1,2,3]", "\"a string\"", "42", "true", "null"})
        void nonObjectIsEmpty(String body) {
            assertTrue(JsonUtil.readJsonBody(FakeHttpExchange.postJson(body)).entrySet().isEmpty());
        }

        @Test
        @DisplayName("a body past the size limit is truncated, so it cannot be parsed whole")
        void oversizedBodyIsRefused() {
            // Well past the 8 KiB cap. Truncation leaves the JSON unterminated, which the parser
            // then rejects — the point is that the server never buffers the whole thing.
            String padding = "x".repeat(64 * 1024);
            JsonObject body = JsonUtil.readJsonBody(
                    FakeHttpExchange.postJson("{\"code\":\"" + padding + "\"}"));

            assertTrue(body.entrySet().isEmpty());
        }

        @Test
        @DisplayName("a body exactly at the limit still parses")
        void bodyAtLimitParses() {
            // 8 KiB total, so the closing brace is the last byte that fits.
            String prefix = "{\"code\":\"";
            String suffix = "\"}";
            String padding = "x".repeat(8 * 1024 - prefix.length() - suffix.length());
            JsonObject body = JsonUtil.readJsonBody(FakeHttpExchange.postJson(prefix + padding + suffix));

            assertEquals(padding, body.get("code").getAsString());
        }

        @Test
        @DisplayName("a UTF-8 body is decoded as UTF-8")
        void utf8Body() {
            JsonObject body = JsonUtil.readJsonBody(
                    FakeHttpExchange.postBytes("{\"name\":\"Ö\u00e9\u2014\"}".getBytes(StandardCharsets.UTF_8)));

            assertEquals("Ö\u00e9\u2014", body.get("name").getAsString());
        }
    }

    @Nested
    @DisplayName("stringField")
    class StringField {

        @Test
        @DisplayName("returns a present string")
        void present() {
            JsonObject body = JsonUtil.readJsonBody(FakeHttpExchange.postJson("{\"code\":\"ABCD\"}"));

            assertEquals("ABCD", JsonUtil.stringField(body, "code"));
        }

        @Test
        @DisplayName("a missing field is null")
        void missing() {
            assertNull(JsonUtil.stringField(new JsonObject(), "code"));
        }

        @Test
        @DisplayName("a null body is null rather than an exception")
        void nullBody() {
            assertNull(JsonUtil.stringField(null, "code"));
        }

        @Test
        @DisplayName("a field that is an object or array is null")
        void wrongType() {
            JsonObject body = JsonUtil.readJsonBody(
                    FakeHttpExchange.postJson("{\"code\":{\"nested\":1},\"list\":[1]}"));

            assertNull(JsonUtil.stringField(body, "code"));
            assertNull(JsonUtil.stringField(body, "list"));
        }

        @Test
        @DisplayName("a JSON null is null")
        void jsonNull() {
            JsonObject body = JsonUtil.readJsonBody(FakeHttpExchange.postJson("{\"code\":null}"));

            assertNull(JsonUtil.stringField(body, "code"));
        }

        @Test
        @DisplayName("a number is read as its string form, which keeps numeric codes usable")
        void numberIsCoerced() {
            JsonObject body = JsonUtil.readJsonBody(FakeHttpExchange.postJson("{\"code\":1234}"));

            assertEquals("1234", JsonUtil.stringField(body, "code"));
        }
    }

    @Nested
    @DisplayName("sending a response")
    class SendingResponses {

        @Test
        @DisplayName("writes the body, status, length and no-store headers")
        void sendsJson() throws Exception {
            FakeHttpExchange exchange = FakeHttpExchange.get("/auth/me");

            JsonUtil.sendJson(exchange, 200, Map.of("ok", true));

            assertEquals(200, exchange.responseCode());
            assertEquals("{\"ok\":true}", exchange.responseText());
            assertEquals(exchange.responseBytes().length, exchange.responseLength());
            assertEquals("application/json; charset=utf-8",
                    exchange.getResponseHeaders().getFirst("Content-Type"));
            assertEquals("no-store", exchange.getResponseHeaders().getFirst("Cache-Control"),
                    "session state must never be cached");
        }

        @Test
        @DisplayName("a non-ASCII body is measured in bytes, not characters")
        void lengthIsInBytes() throws Exception {
            FakeHttpExchange exchange = FakeHttpExchange.get("/auth/me");

            JsonUtil.sendJson(exchange, 200, Map.of("name", "\u2014"));

            // An em dash is three UTF-8 bytes; a length in characters would truncate the response.
            assertEquals(exchange.responseText().getBytes(StandardCharsets.UTF_8).length,
                    exchange.responseLength());
        }
    }

    @Nested
    @DisplayName("toJson")
    class ToJson {

        @Test
        @DisplayName("serialises a plain map")
        void map() {
            assertEquals("{\"a\":1}", JsonUtil.toJson(Map.of("a", 1)));
        }

        @Test
        @DisplayName("nulls are omitted, which is what the browser expects")
        void nullsOmitted() {
            String json = JsonUtil.toJson(new Payload());

            assertTrue(json.contains("present"));
            assertFalse(json.contains("absent"), "Gson's default is to drop nulls: " + json);
        }
    }

    /** A DTO shaped like the mod's own: public fields, some of them unset. */
    private static final class Payload {
        final String present = "x";
        final String absent = null;
    }
}
