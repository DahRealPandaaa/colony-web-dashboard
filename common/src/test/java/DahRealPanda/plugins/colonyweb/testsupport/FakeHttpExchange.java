package DahRealPanda.plugins.colonyweb.testsupport;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * An {@link HttpExchange} that records what a handler did to it.
 *
 * <p>{@code HttpExchange} is an abstract class rather than an interface, and the JDK's own
 * implementation is package private, so exercising the cookie and JSON helpers without standing
 * up a real {@code HttpServer} means providing one of these. It keeps the request body, the two
 * header maps and whatever was written to the response, which is everything the handlers under
 * test actually touch.</p>
 */
public final class FakeHttpExchange extends HttpExchange {

    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final InputStream requestBody;
    private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
    private final String method;
    private final URI uri;

    private int responseCode = -1;
    private long responseLength = Long.MIN_VALUE;
    private boolean closed;

    private FakeHttpExchange(String method, String uri, byte[] body) {
        this.method = method;
        this.uri = URI.create(uri);
        this.requestBody = new ByteArrayInputStream(body);
    }

    public static FakeHttpExchange get(String uri) {
        return new FakeHttpExchange("GET", uri, new byte[0]);
    }

    public static FakeHttpExchange postJson(String body) {
        return new FakeHttpExchange("POST", "/auth/login", body.getBytes(StandardCharsets.UTF_8));
    }

    public static FakeHttpExchange postBytes(byte[] body) {
        return new FakeHttpExchange("POST", "/auth/login", body);
    }

    /** Add a request header, as a browser would send it. */
    public FakeHttpExchange withHeader(String name, String value) {
        requestHeaders.add(name, value);
        return this;
    }

    public int responseCode() {
        return responseCode;
    }

    public long responseLength() {
        return responseLength;
    }

    public boolean isClosed() {
        return closed;
    }

    public String responseText() {
        return responseBody.toString(StandardCharsets.UTF_8);
    }

    public byte[] responseBytes() {
        return responseBody.toByteArray();
    }

    /** Every {@code Set-Cookie} header the handler added, in order. */
    public java.util.List<String> setCookies() {
        java.util.List<String> values = responseHeaders.get("Set-Cookie");
        return values == null ? java.util.List.of() : values;
    }

    // ------------------------------------------------------------------
    // HttpExchange
    // ------------------------------------------------------------------

    @Override
    public Headers getRequestHeaders() {
        return requestHeaders;
    }

    @Override
    public Headers getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public URI getRequestURI() {
        return uri;
    }

    @Override
    public String getRequestMethod() {
        return method;
    }

    @Override
    public HttpContext getHttpContext() {
        return null;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public InputStream getRequestBody() {
        return requestBody;
    }

    @Override
    public OutputStream getResponseBody() {
        return responseBody;
    }

    @Override
    public void sendResponseHeaders(int rCode, long responseLength) {
        this.responseCode = rCode;
        this.responseLength = responseLength;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return new InetSocketAddress("127.0.0.1", 12345);
    }

    @Override
    public int getResponseCode() {
        return responseCode;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return new InetSocketAddress("127.0.0.1", 8723);
    }

    @Override
    public String getProtocol() {
        return "HTTP/1.1";
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }

    @Override
    public void setAttribute(String name, Object value) {
        // Not used by any handler under test.
    }

    @Override
    public void setStreams(InputStream i, OutputStream o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpPrincipal getPrincipal() {
        return null;
    }
}
