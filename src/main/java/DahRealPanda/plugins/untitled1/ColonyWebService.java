package DahRealPanda.plugins.untitled1;

import DahRealPanda.plugins.untitled1.colony.ColonyCache;
import DahRealPanda.plugins.untitled1.colony.ColonyDataProvider;
import DahRealPanda.plugins.untitled1.colony.model.ColonySnapshot;
import DahRealPanda.plugins.untitled1.colony.model.ColonySummary;
import DahRealPanda.plugins.untitled1.texture.TextureService;
import DahRealPanda.plugins.untitled1.texture.VanillaAssetProvider;
import DahRealPanda.plugins.untitled1.web.JsonUtil;
import DahRealPanda.plugins.untitled1.web.SseBroadcaster;
import DahRealPanda.plugins.untitled1.web.WebServer;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Owns the dashboard runtime: vanilla asset download, texture service, web server, SSE
 * broadcaster, and the periodic colony scan + change-detection that drives live updates.
 */
public final class ColonyWebService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile ColonyWebService instance;

    private final MinecraftServer server;
    private final ColonyCache cache = new ColonyCache();
    private final SseBroadcaster broadcaster = new SseBroadcaster();
    private final ColonyDataProvider provider;
    private final VanillaAssetProvider vanillaAssets;
    private final TextureService textureService;
    private final WebServer webServer;

    private ScheduledExecutorService scheduler;

    // Change-detection state.
    private final Map<Integer, Integer> colonyHashes = new HashMap<>();
    private int coloniesSetHash;
    private int ticks;

    private ColonyWebService(MinecraftServer server) {
        this.server = server;
        this.provider = new ColonyDataProvider(server);

        Path cacheDir = server.getServerDirectory().toPath().resolve("colonyweb-cache");
        this.vanillaAssets = new VanillaAssetProvider("1.20.1", cacheDir);
        this.textureService = new TextureService("1.20.1", cacheDir, vanillaAssets);
        this.webServer = new WebServer(Config.bindAddress, Config.httpPort, cache, textureService, broadcaster);
    }

    public static ColonyWebService get() {
        return instance;
    }

    /** Start everything on server start. */
    public static void start(MinecraftServer server) {
        stop();
        ColonyWebService svc = new ColonyWebService(server);
        instance = svc;
        svc.startInternal();
    }

    /** Stop everything on server stop. */
    public static void stop() {
        ColonyWebService svc = instance;
        if (svc != null) {
            svc.stopInternal();
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
            LOGGER.error("[ColonyWeb] failed to start web server on {}:{}", Config.bindAddress, Config.httpPort, e);
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "colonyweb-scheduler");
            t.setDaemon(true);
            return t;
        });
        int interval = Math.max(1, Config.refreshIntervalSeconds);
        // Scanning touches the world, so hop onto the server thread each tick.
        scheduler.scheduleAtFixedRate(this::tick, interval, interval, TimeUnit.SECONDS);
    }

    private void stopInternal() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        webServer.stop();
    }

    private void tick() {
        try {
            server.execute(this::scanAndBroadcast);
        } catch (Exception e) {
            LOGGER.debug("[ColonyWeb] failed to schedule scan", e);
        }
    }

    /** Runs on the server thread: rescans colonies, updates the cache, emits SSE deltas. */
    private void scanAndBroadcast() {
        try {
            List<ColonySummary> summaries = provider.listColonies();
            cache.setSummaries(summaries);
            cache.retainOnly(summaries);

            int setHash = summaries.stream().mapToInt(s -> s.id).sum() * 31 + summaries.size();
            if (setHash != coloniesSetHash) {
                coloniesSetHash = setHash;
                broadcaster.broadcast(JsonUtil.toJson(Map.of("type", "colonies")));
            }

            for (ColonySummary summary : summaries) {
                provider.snapshot(summary.id).ifPresent(snapshot -> {
                    cache.putSnapshot(summary.id, snapshot);
                    int hash = hashSnapshot(snapshot);
                    Integer prev = colonyHashes.put(summary.id, hash);
                    if (prev == null || prev != hash) {
                        broadcaster.broadcast(JsonUtil.toJson(Map.of("type", "colony", "id", summary.id)));
                    }
                });
            }

            // Periodic heartbeat (~ every 30s) to prune dead SSE sockets.
            if (++ticks % Math.max(1, 30 / Math.max(1, Config.refreshIntervalSeconds)) == 0) {
                broadcaster.heartbeat();
            }
        } catch (Exception e) {
            LOGGER.debug("[ColonyWeb] scan failed", e);
        }
    }

    /** Lightweight hash of the volatile parts of a snapshot (buckets progress to whole %). */
    private int hashSnapshot(ColonySnapshot snap) {
        int h = 7;
        for (var b : snap.buildings) {
            h = h * 31 + b.id;
            h = h * 31 + b.level;
            h = h * 31 + (b.beingBuilt ? 1 : 0);
            h = h * 31 + b.workOrderId;
            for (var r : b.required) {
                h = h * 31 + (r.itemKey == null ? 0 : r.itemKey.hashCode());
                h = h * 31 + r.needed;
                h = h * 31 + r.inHut;
                h = h * 31 + r.inWarehouse;
            }
        }
        for (var wo : snap.workOrders) {
            h = h * 31 + wo.id;
            h = h * 31 + wo.currentLevel;
            h = h * 31 + wo.targetLevel;
            h = h * 31 + (int) Math.round(wo.progress * 100);
        }
        if (snap.warehouse != null) {
            for (var s : snap.warehouse.stacks) {
                h = h * 31 + (s.itemKey == null ? 0 : s.itemKey.hashCode());
                h = h * 31 + s.count;
            }
        }
        return h;
    }

    // ------------------------------------------------------------------
    // Status accessors (used by the /colonyweb command).
    // ------------------------------------------------------------------

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
