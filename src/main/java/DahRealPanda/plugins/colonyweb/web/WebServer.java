package DahRealPanda.plugins.colonyweb.web;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.auth.AuthService;
import DahRealPanda.plugins.colonyweb.colony.ColonyCache;
import DahRealPanda.plugins.colonyweb.map.ColonyMapService;
import DahRealPanda.plugins.colonyweb.texture.TextureService;
import DahRealPanda.plugins.colonyweb.web.handlers.ApiHandler;
import DahRealPanda.plugins.colonyweb.web.handlers.AuthHandler;
import DahRealPanda.plugins.colonyweb.web.handlers.EventsHandler;
import DahRealPanda.plugins.colonyweb.web.handlers.MapHandler;
import DahRealPanda.plugins.colonyweb.web.handlers.StaticHandler;
import DahRealPanda.plugins.colonyweb.web.handlers.TextureHandler;
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
 *
 * <p>Only the static front-end shell is public — it has to be, since that is the page where
 * players type their pairing code. Everything that exposes colony data sits behind
 * {@link AuthService}.</p>
 */
public final class WebServer {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Each SSE viewer holds a thread, so the pool must comfortably exceed the viewer count. */
    private static final int HTTP_THREADS = 8;

    private final String bindAddress;
    private final int port;
    private final ColonyCache cache;
    private final ColonyMapService maps;
    private final TextureService textureService;
    private final SseBroadcaster broadcaster;
    private final AuthService auth;

    private HttpServer server;

    public WebServer(String bindAddress, int port, ColonyCache cache, ColonyMapService maps,
                     TextureService textureService, SseBroadcaster broadcaster, AuthService auth) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.cache = cache;
        this.maps = maps;
        this.textureService = textureService;
        this.broadcaster = broadcaster;
        this.auth = auth;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/auth/", new AuthHandler(auth));
        server.createContext("/api/", new ApiHandler(cache, maps, auth));
        server.createContext("/events", new EventsHandler(broadcaster, auth));
        server.createContext("/textures/", new TextureHandler(textureService, auth));
        server.createContext("/map/", new MapHandler(maps, auth));
        server.createContext("/", new StaticHandler());

        server.setExecutor(Executors.newFixedThreadPool(HTTP_THREADS, namedFactory()));
        server.start();
        LOGGER.info("{} dashboard listening on http://{}:{}/ (auth {})", ColonyWeb.LOG,
                bindAddress, port, auth.enabled() ? "required" : "disabled");
    }

    public void stop() {
        broadcaster.closeAll();
        if (server != null) {
            server.stop(0);
            server = null;
            LOGGER.info("{} dashboard stopped", ColonyWeb.LOG);
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
