package DahRealPanda.plugins.colonyweb.support

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpPrincipal
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI

/**
 * A hand-written [HttpExchange] carrying nothing but headers.
 *
 * `HttpExchange` lives in the `jdk.httpserver` module, which is not open for reflection, so a
 * mocking library cannot subclass it without command-line flags on every test JVM. Only the two
 * header bags matter to
 * [DahRealPanda.plugins.colonyweb.auth.SessionCookie] anyway, and everything else throws so a
 * test that strays past cookies fails loudly rather than against a silent default.
 */
class FakeHttpExchange(
    private val requestHeaders: Headers = Headers(),
    private val responseHeaders: Headers = Headers(),
) : HttpExchange() {

    override fun getRequestHeaders(): Headers = requestHeaders

    override fun getResponseHeaders(): Headers = responseHeaders

    override fun getRequestURI(): URI = URI.create("/")

    override fun getRequestMethod(): String = "GET"

    override fun getHttpContext(): HttpContext = unsupported("getHttpContext")

    override fun close() = Unit

    override fun getRequestBody(): InputStream = InputStream.nullInputStream()

    override fun getResponseBody(): OutputStream = OutputStream.nullOutputStream()

    override fun sendResponseHeaders(rCode: Int, responseLength: Long) = unsupported("sendResponseHeaders")

    override fun getRemoteAddress(): InetSocketAddress = InetSocketAddress.createUnresolved("localhost", 0)

    override fun getResponseCode(): Int = -1

    override fun getLocalAddress(): InetSocketAddress = InetSocketAddress.createUnresolved("localhost", 0)

    override fun getProtocol(): String = "HTTP/1.1"

    override fun getAttribute(name: String?): Any? = null

    override fun setAttribute(name: String?, value: Any?) = Unit

    override fun setStreams(i: InputStream?, o: OutputStream?) = unsupported("setStreams")

    override fun getPrincipal(): HttpPrincipal = unsupported("getPrincipal")

    private fun unsupported(member: String): Nothing =
        throw UnsupportedOperationException("FakeHttpExchange.$member is not part of this fixture")

    companion object {
        /** An exchange whose request carries the given `Cookie` headers, as a browser would send them. */
        fun withCookieHeaders(vararg headers: String): FakeHttpExchange {
            val requestHeaders = Headers()
            if (headers.isNotEmpty()) {
                requestHeaders["Cookie"] = headers.toList()
            }
            return FakeHttpExchange(requestHeaders = requestHeaders)
        }
    }
}
