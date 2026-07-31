package DahRealPanda.plugins.colonyweb.auth

import DahRealPanda.plugins.colonyweb.support.FakeHttpExchange
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The session cookie is the credential every dashboard request carries, so this covers both the
 * parsing (a browser sends its whole cookie jar, not just ours) and the attributes that keep the
 * token out of reach of page scripts and cross-site requests.
 */
class SessionCookieTest : DescribeSpec({

    fun exchangeWithCookies(vararg headers: String): HttpExchange =
        FakeHttpExchange.withCookieHeaders(*headers)

    fun responseHeadersOf(write: (HttpExchange) -> Unit): Headers {
        val exchange = FakeHttpExchange()
        write(exchange)
        return exchange.responseHeaders
    }

    describe("reading the token from a request") {
        it("finds the token when ours is the only cookie") {
            SessionCookie.read(exchangeWithCookies("colonyweb_session=abc123")) shouldBe "abc123"
        }

        it("finds the token among the browser's other cookies") {
            SessionCookie.read(
                exchangeWithCookies("theme=dark; colonyweb_session=abc123; locale=en")
            ) shouldBe "abc123"
        }

        it("finds the token when the browser splits cookies across headers") {
            SessionCookie.read(
                exchangeWithCookies("theme=dark", "colonyweb_session=abc123")
            ) shouldBe "abc123"
        }

        it("returns null when the request carries no cookies at all") {
            SessionCookie.read(exchangeWithCookies()) shouldBe null
        }

        it("returns null when none of the cookies is ours") {
            SessionCookie.read(exchangeWithCookies("theme=dark; locale=en")) shouldBe null
        }

        // A cleared cookie arrives as an empty value; treating it as a token would send an
        // obviously-invalid credential to the auth service on every request.
        it("treats an empty value as no session") {
            SessionCookie.read(exchangeWithCookies("colonyweb_session=")) shouldBe null
        }

        it("does not mistake a cookie whose name merely starts the same") {
            SessionCookie.read(exchangeWithCookies("colonyweb_session_backup=abc123")) shouldBe null
        }

        it("does not mistake a cookie whose name ends with ours") {
            SessionCookie.read(exchangeWithCookies("other_colonyweb_session=abc123")) shouldBe null
        }

        it("keeps a token that itself contains an equals sign") {
            SessionCookie.read(exchangeWithCookies("colonyweb_session=abc=123")) shouldBe "abc=123"
        }
    }

    describe("writing the cookie") {
        it("sends the token back with a lifetime") {
            val headers = responseHeadersOf { SessionCookie.set(it, "abc123", 3600) }

            headers["Set-Cookie"]!! shouldHaveSize 1
            headers.getFirst("Set-Cookie") shouldContain "colonyweb_session=abc123"
            headers.getFirst("Set-Cookie") shouldContain "Max-Age=3600"
            headers.getFirst("Set-Cookie") shouldContain "Path=/"
        }

        // HttpOnly keeps page scripts — and anything injected into them — from reading the token;
        // SameSite=Lax keeps another site from riding the session with a cross-origin request.
        it("marks the cookie HttpOnly and SameSite=Lax") {
            val headers = responseHeadersOf { SessionCookie.set(it, "abc123", 3600) }

            headers.getFirst("Set-Cookie") shouldContain "HttpOnly"
            headers.getFirst("Set-Cookie") shouldContain "SameSite=Lax"
        }

        it("expires the cookie immediately on logout") {
            val headers = responseHeadersOf { SessionCookie.clear(it) }

            headers.getFirst("Set-Cookie") shouldContain "colonyweb_session=;"
            headers.getFirst("Set-Cookie") shouldContain "Max-Age=0"
        }

        it("keeps the security attributes when clearing, so the two headers match") {
            val headers = responseHeadersOf { SessionCookie.clear(it) }

            headers.getFirst("Set-Cookie") shouldContain "HttpOnly"
            headers.getFirst("Set-Cookie") shouldContain "SameSite=Lax"
            headers.getFirst("Set-Cookie") shouldContain "Path=/"
        }
    }
})
