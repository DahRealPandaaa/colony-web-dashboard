package DahRealPanda.plugins.colonyweb;

import DahRealPanda.plugins.colonyweb.auth.AuthService;
import DahRealPanda.plugins.colonyweb.colony.ColonyCache;
import DahRealPanda.plugins.colonyweb.colony.ColonyDataProvider;
import DahRealPanda.plugins.colonyweb.map.ColonyMapService;
import DahRealPanda.plugins.colonyweb.service.ColonyRefreshScheduler;
import DahRealPanda.plugins.colonyweb.texture.TextureService;
import DahRealPanda.plugins.colonyweb.texture.VanillaAssetProvider;
import DahRealPanda.plugins.colonyweb.web.SseBroadcaster;
import DahRealPanda.plugins.colonyweb.web.WebServer;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Wires the dashboard runtime together and owns its lifecycle: data directory, texture
 * pipeline, authentication, web server and the periodic colony scan.
 *
 * <p>Held as a singleton so the {@code /colonyweb} command can reach the live instance.</p>
 */
public final class ColonyWebService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The Minecraft version whose client assets back vanilla item icons. */
    private static final String ASSET_VERSION = "1.20.1";

    /** Everything the mod persists lives here, under the server directory. */
    private static final String DATA_DIR = "colonyweb";

    private static volatile ColonyWebService instance;

    private final ColonyCache cache = new ColonyCache();
    private final SseBroadcaster broadcaster = new SseBroadcaster();
    private final ColonyDataProvider provider;
    private final ColonyMapService maps;
    private final AuthService auth;
    private final VanillaAssetProvider vanillaAssets;
    private final WebServer webServer;
    private final ColonyRefreshScheduler scheduler;

    private ColonyWebService(MinecraftServer server) {
        Path dataDir = server.getServerDirectory().toPath().resolve(DATA_DIR);

        this.provider = new ColonyDataProvider(server);
        this.maps = new ColonyMapService(cache, provider);
        this.auth = new AuthService(dataDir);
        this.vanillaAssets = new VanillaAssetProvider(ASSET_VERSION, dataDir);

        TextureService textures = new TextureService(dataDir, vanillaAssets);
        this.webServer = new WebServer(Config.bindAddress, Config.httpPort, cache, maps, textures,
                broadcaster, auth);
        this.scheduler = new ColonyRefreshScheduler(server, provider, cache, maps, broadcaster, auth);
    }

    public static ColonyWebService get() {
        return instance;
    }

    /** Start everything on server start. */
    public static void start(MinecraftServer server) {
        stop();
        ColonyWebService service = new ColonyWebService(server);
        instance = service;
        service.startInternal();
    }

    /** Stop everything on server stop. */
    public static void stop() {
        ColonyWebService service = instance;
        if (service != null) {
            service.stopInternal();
            instance = null;
        }
    }

    private void startInternal() {
        if (Config.autoDownloadVanillaAssets) {
            CompletableFuture.runAsync(vanillaAssets::ensureDownloaded);
        }
        try {
            webServer.start();
        } catch (Exception e) {
            LOGGER.error("{} failed to start web server on {}:{}", ColonyWeb.LOG,
                    Config.bindAddress, Config.httpPort, e);
        }
        scheduler.start();
    }

    private void stopInternal() {
        scheduler.stop();
        webServer.stop();
        maps.stop();
    }

    // ------------------------------------------------------------------
    // Accessors used by the /colonyweb command.
    // ------------------------------------------------------------------

    public AuthService auth() {
        return auth;
    }

    public ColonyDataProvider provider() {
        return provider;
    }

    public boolean isWebRunning() {
        return webServer.isRunning();
    }

    public int getPort() {
        return webServer.getPort();
    }

    public int getSseClients() {
        return broadcaster.clientCount();
    }

    public boolean isMineColoniesDetected() {
        return provider.available();
    }
}
