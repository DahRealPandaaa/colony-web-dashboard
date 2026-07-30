package DahRealPanda.plugins.colonyweb.auth;

import DahRealPanda.plugins.colonyweb.testsupport.FakeHttpExchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing the session cookie out of a request, and the flags set when issuing one.
 *
 * <p>The cookie carries the only credential the dashboard has, so the flags on it are part of
 * the security model rather than cosmetic: {@code HttpOnly} keeps injected page scripts from
 * reading the token and {@code SameSite=Lax} keeps another site from riding the session.</p>
 */
class SessionCookieTest {

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("finds the token in a lone cookie")
        void singleCookie() {
            FakeHttpExchange exchange = FakeHttpExchange.get("/api/colonies")
                    .withHeader("Cookie", SessionCookie.NAME + "=abc123");

            assertEquals(Optional.of("abc123"), SessionCookie.read(exchange));
        }

        @Test
        @DisplayName("finds the token among other cookies")
        void amongOthers() {
            FakeHttpExchange exchange = FakeHttpExchange.get("/api/colonies")
                    .withHeader("Cookie", "theme=dark; " + SessionCookie.NAME + "=abc123; lang=en");

            assertEquals(Optional.of("abc123"), SessionCookie.read(exchange));
        }

        @Test
        @DisplayName("finds the token when the browser sends several Cookie headers")
        void multipleHeaders() {
            FakeHttpExchange exchange = FakeHttpExchange.get("/api/colonies")
                    .withHeader("Cookie", "theme=dark")
                    .withHeader("Cookie", SessionCookie.NAME + "=abc123");

            assertEquals(Optional.of("abc123"), SessionCookie.read(exchange));
        }

        @Test
        @DisplayName("no Cookie header at all means no session")
        void noHeader() {
            assertTrue(SessionCookie.read(FakeHttpExchange.get("/api/colonies")).isEmpty());
        }

        @Test
        @DisplayName("an unrelated cookie is not mistaken for the session")
        void unrelatedCookie() {
            FakeHttpExchange exchange = FakeHttpExchange.get("/api/colonies")
                    .withHeader("Cookie", "theme=dark");

            assertTrue(SessionCookie.read(exchange).isEmpty());
        }

        @Test
        @DisplayName("a cookie whose name merely ends with ours is not matched")
        void suffixNameIsNotMatched() {
            FakeHttpExchange exchange = FakeHttpExchange.get("/api/colonies")
                    .withHeader("Cookie", "evil_" + SessionCookie.NAME + "=stolen");

            assertTrue(SessionCookie.read(exchange).isEmpty(),
                    "matching on a suffix would let any cookie impersonate the session");
        }

        @ParameterizedTest
        @DisplayName("an empty value is treated as no session")
        @ValueSource(strings = {"colonyweb_session=", "colonyweb_session=   ", "theme=dark; colonyweb_session="})
        void blankValue(String header) {
            FakeHttpExchange exchange = FakeHttpExchange.get("/api/colonies").withHeader("Cookie", header);

            assertTrue(SessionCookie.read(exchange).isEmpty());
        }

        @Test
        @DisplayName("surrounding whitespace is tolerated")
        void trimsWhitespace() {
            FakeHttpExchange exchange = FakeHttpExchange.get("/api/colonies")
                    .withHeader("Cookie", "  theme=dark ;   " + SessionCookie.NAME + "=abc123  ");

            assertEquals(Optional.of("abc123"), SessionCookie.read(exchange));
        }

        @Test
        @DisplayName("the first matching cookie wins")
        void firstMatchWins() {
            FakeHttpExchange exchange = FakeHttpExchange.get("/api/colonies")
                    .withHeader("Cookie", SessionCookie.NAME + "=first; " + SessionCookie.NAME + "=second");

            assertEquals(Optional.of("first"), SessionCookie.read(exchange));
        }
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("issuing a session sets the hardening flags")
        void setIsHardened() {
            FakeHttpExchange exchange = FakeHttpExchange.get("/auth/login");

            SessionCookie.set(exchange, "abc123", 2_592_000L);

            String cookie = exchange.setCookies().get(0);
            assertTrue(cookie.startsWith(SessionCookie.NAME + "=abc123"), cookie);
            assertTrue(cookie.contains("Max-Age=2592000"), cookie);
            assertTrue(cookie.contains("HttpOnly"), "page scripts must not be able to read the token");
            assertTrue(cookie.contains("SameSite=Lax"), "another site must not be able to ride the session");
            assertTrue(cookie.contains("Path=/"), cookie);
        }

        @Test
        @DisplayName("clearing expires the cookie immediately")
        void clearExpires() {
            FakeHttpExchange exchange = FakeHttpExchange.get("/auth/logout");

            SessionCookie.clear(exchange);

            String cookie = exchange.setCookies().get(0);
            assertTrue(cookie.contains("Max-Age=0"), cookie);
            assertTrue(cookie.startsWith(SessionCookie.NAME + "="), cookie);
            assertTrue(cookie.contains("HttpOnly"), cookie);
        }

        @Test
        @DisplayName("a cookie this handler wrote is one the reader understands")
        void roundTrip() {
            FakeHttpExchange write = FakeHttpExchange.get("/auth/login");
            SessionCookie.set(write, "round-trip-token", 60);

            String issued = write.setCookies().get(0);
            String pair = issued.substring(0, issued.indexOf(';'));
            FakeHttpExchange read = FakeHttpExchange.get("/api/colonies").withHeader("Cookie", pair);

            assertEquals(Optional.of("round-trip-token"), SessionCookie.read(read));
        }
    }
}
