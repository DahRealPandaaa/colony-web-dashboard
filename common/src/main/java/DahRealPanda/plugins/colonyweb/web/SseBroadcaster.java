package DahRealPanda.plugins.colonyweb.web;

import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Tracks connected SSE clients and pushes {@code update} events to them.
 */
public final class SseBroadcaster {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Set<Client> clients = new CopyOnWriteArraySet<>();

    /** Register a new SSE client from an active exchange. The exchange stays open. */
    public void register(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        Client client = new Client(exchange);
        clients.add(client);
        // Prime the stream so proxies flush headers.
        client.send(": connected\n\n");
        LOGGER.debug("[ColonyWeb] SSE client connected ({} total)", clients.size());
    }

    public int clientCount() {
        return clients.size();
    }

    /** Broadcast a JSON payload as an {@code update} event. */
    public void broadcast(String jsonData) {
        String frame = "event: update\ndata: " + jsonData + "\n\n";
        for (Client c : clients) {
            if (!c.send(frame)) {
                clients.remove(c);
            }
        }
    }

    /** Send a keep-alive comment to detect dead sockets. */
    public void heartbeat() {
        for (Client c : clients) {
            if (!c.send(": ping\n\n")) {
                clients.remove(c);
            }
        }
    }

    /** Close all client connections. */
    public void closeAll() {
        for (Client c : clients) {
            c.close();
        }
        clients.clear();
    }

    private static final class Client {
        private final HttpExchange exchange;
        private final OutputStream out;

        Client(HttpExchange exchange) {
            this.exchange = exchange;
            this.out = exchange.getResponseBody();
        }

        synchronized boolean send(String text) {
            try {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
                return true;
            } catch (IOException e) {
                close();
                return false;
            }
        }

        void close() {
            try {
                exchange.close();
            } catch (Exception ignored) {
                // no-op
            }
        }
    }
}
