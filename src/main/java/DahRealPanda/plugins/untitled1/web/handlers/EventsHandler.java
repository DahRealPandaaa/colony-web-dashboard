package DahRealPanda.plugins.untitled1.web.handlers;

import DahRealPanda.plugins.untitled1.web.SseBroadcaster;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Registers the caller as an SSE client on {@code /events}. The exchange is kept open by the
 * broadcaster; this handler never closes it on the happy path.
 */
public final class EventsHandler implements HttpHandler {
    private final SseBroadcaster broadcaster;

    public EventsHandler(SseBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            byte[] body = "Method Not Allowed".getBytes();
            exchange.sendResponseHeaders(405, body.length);
            exchange.getResponseBody().write(body);
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
