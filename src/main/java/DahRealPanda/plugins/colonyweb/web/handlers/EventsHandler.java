package DahRealPanda.plugins.colonyweb.web.handlers;

import DahRealPanda.plugins.colonyweb.auth.AuthService;
import DahRealPanda.plugins.colonyweb.web.JsonUtil;
import DahRealPanda.plugins.colonyweb.web.SseBroadcaster;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Registers the caller as an SSE client on {@code /events}. The exchange is kept open by the
 * broadcaster; this handler never closes it on the happy path.
 *
 * <p>Update events only ever say "colony N changed" — the browser then re-fetches through the
 * access-checked API — but the stream still requires a session so an anonymous client cannot
 * learn which colonies exist or when they are active.</p>
 */
public final class EventsHandler implements HttpHandler {
    private final SseBroadcaster broadcaster;
    private final AuthService auth;

    public EventsHandler(SseBroadcaster broadcaster, AuthService auth) {
        this.broadcaster = broadcaster;
        this.auth = auth;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonUtil.sendError(exchange, 405, "Method Not Allowed");
            exchange.close();
            return;
        }
        if (RequestAuth.rejectUnauthenticated(auth, exchange)) {
            exchange.close();
            return;
        }
        try {
            broadcaster.register(exchange);
        } catch (IOException e) {
            exchange.close();
        }
    }
}
