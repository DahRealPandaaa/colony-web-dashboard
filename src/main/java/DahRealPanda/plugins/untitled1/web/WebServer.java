package DahRealPanda.plugins.untitled1.web;

import DahRealPanda.plugins.untitled1.colony.ColonyCache;
import DahRealPanda.plugins.untitled1.texture.TextureService;
import DahRealPanda.plugins.untitled1.web.handlers.ApiHandler;
import DahRealPanda.plugins.untitled1.web.handlers.EventsHandler;
import DahRealPanda.plugins.untitled1.web.handlers.StaticHandler;
import DahRealPanda.plugins.untitled1.web.handlers.TextureHandler;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedded HTTP server hosting the dashboard, JSON API, SSE stream and texture icons.
 */
public final class WebServer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String bindAddress;
    private final int port;
    private final ColonyCache cache;
    private final TextureService textureService;
    private final SseBroadcaster broadcaster;

    private HttpServer server;

    public WebServer(String bindAddress, int port, ColonyCache cache,
                     TextureService textureService, SseBroadcaster broadcaster) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.cache = cache;
        this.textureService = textureService;
        this.broadcaster = broadcaster;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/api/", new ApiHandler(cache));
        server.createContext("/events", new EventsHandler(broadcaster));
        server.createContext("/textures/", new TextureHandler(textureService));
        server.createContext("/", new StaticHandler());

        server.setExecutor(Executors.newFixedThreadPool(4, namedFactory()));
        server.start();
        LOGGER.info("[ColonyWeb] dashboard listening on http://{}:{}/", bindAddress, port);
    }

    public void stop() {
        broadcaster.closeAll();
        if (server != null) {
            server.stop(0);
            server = null;
            LOGGER.info("[ColonyWeb] dashboard stopped");
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public int getPort() {
        return port;
    }

    private static ThreadFactory namedFactory() {
        AtomicInteger counter = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "colonyweb-http-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
