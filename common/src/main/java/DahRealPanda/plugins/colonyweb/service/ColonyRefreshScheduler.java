package DahRealPanda.plugins.colonyweb.service;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.Config;
import DahRealPanda.plugins.colonyweb.auth.AuthService;
import DahRealPanda.plugins.colonyweb.colony.ColonyCache;
import DahRealPanda.plugins.colonyweb.colony.ColonyDataProvider;
import DahRealPanda.plugins.colonyweb.colony.ColonyScan;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySummary;
import DahRealPanda.plugins.colonyweb.map.ColonyMapService;
import DahRealPanda.plugins.colonyweb.web.JsonUtil;
import DahRealPanda.plugins.colonyweb.web.SseBroadcaster;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically re-scans every colony on the server thread, publishes the results to the cache,
 * and pushes an SSE event for each colony that actually changed.
 *
 * <p>The colony map rides along on the same pass, for the same reason: both need the world, and
 * the world may only be read from the server thread.</p>
 */
public final class ColonyRefreshScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Walk the research tree only every Nth scan — it is expensive and changes slowly. */
    private static final int RESEARCH_EVERY_N_SCANS = 5;

    /** Roughly how often to prune dead SSE sockets and expired sessions, in seconds. */
    private static final int HOUSEKEEPING_SECONDS = 30;

    private final MinecraftServer server;
    private final ColonyDataProvider provider;
    private final ColonyCache cache;
    private final ColonyMapService maps;
    private final SseBroadcaster broadcaster;
    private final AuthService auth;

    private final Map<Integer, Integer> colonyHashes = new HashMap<>();
    private int coloniesSetHash;
    private int ticks;

    private ScheduledExecutorService scheduler;

    public ColonyRefreshScheduler(MinecraftServer server, ColonyDataProvider provider, ColonyCache cache,
                                  ColonyMapService maps, SseBroadcaster broadcaster, AuthService auth) {
        this.server = server;
        this.provider = provider;
        this.cache = cache;
        this.maps = maps;
        this.broadcaster = broadcaster;
        this.auth = auth;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "colonyweb-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        int interval = Math.max(1, Config.refreshIntervalSeconds);
        // Scanning touches the world, so every pass hops onto the server thread.
        scheduler.scheduleAtFixedRate(this::tick, interval, interval, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void tick() {
        try {
            server.execute(this::scanAndBroadcast);
        } catch (Exception e) {
            LOGGER.debug("{} failed to schedule scan", ColonyWeb.LOG, e);
        }
    }

    /** Runs on the server thread. */
    private void scanAndBroadcast() {
        try {
            List<ColonySummary> summaries = provider.listColonies();
            cache.setSummaries(summaries);
            cache.retainOnly(summaries);
            broadcastColonySetChange(summaries);

            boolean withResearch = ticks % RESEARCH_EVERY_N_SCANS == 0;
            for (ColonySummary summary : summaries) {
                boolean needsResearch = withResearch || cache.research(summary.id).isEmpty();
                provider.scan(summary.id, needsResearch)
                        .ifPresent(scan -> publish(summary.id, scan));
            }

            // Drawing the map reads loaded chunks, so it rides the same server-thread pass.
            maps.tick();

            if (++ticks % housekeepingEveryNScans() == 0) {
                broadcaster.heartbeat();
                auth.purgeExpiredSessions();
            }
        } catch (Exception e) {
            LOGGER.debug("{} scan failed", ColonyWeb.LOG, e);
        }
    }

    /** Tell browsers to reload the colony list when colonies are created or deleted. */
    private void broadcastColonySetChange(List<ColonySummary> summaries) {
        int setHash = summaries.stream().mapToInt(summary -> summary.id).sum() * 31 + summaries.size();
        if (setHash != coloniesSetHash) {
            coloniesSetHash = setHash;
            broadcaster.broadcast(JsonUtil.toJson(Map.of("type", "colonies")));
        }
    }

    /** Store one colony's scan and notify viewers when it differs from the previous pass. */
    private void publish(int colonyId, ColonyScan scan) {
        ColonySnapshot snapshot = scan.snapshot;
        if (scan.research != null) {
            cache.putResearch(colonyId, scan.research);
        }
        // On passes that skip the research walk, keep the previous counts on the overview.
        cache.research(colonyId).ifPresent(research -> {
            snapshot.stats.researchCompleted = research.completed;
            snapshot.stats.researchInProgress = research.inProgress;
        });

        cache.putSnapshot(colonyId, snapshot);
        cache.putCitizens(colonyId, scan.citizens);
        cache.putCombat(colonyId, scan.combat);
        cache.putInventories(colonyId, scan.inventories);
        cache.putEquipment(colonyId, scan.equipment);

        int hash = ScanHasher.hash(scan);
        Integer previous = colonyHashes.put(colonyId, hash);
        if (previous == null || previous != hash) {
            broadcaster.broadcast(JsonUtil.toJson(Map.of("type", "colony", "id", colonyId)));
        }
    }

    private int housekeepingEveryNScans() {
        return Math.max(1, HOUSEKEEPING_SECONDS / Math.max(1, Config.refreshIntervalSeconds));
    }
}
