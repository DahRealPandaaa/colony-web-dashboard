package DahRealPanda.plugins.colonyweb.map;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.Config;
import DahRealPanda.plugins.colonyweb.colony.ColonyCache;
import DahRealPanda.plugins.colonyweb.colony.ColonyDataProvider;
import DahRealPanda.plugins.colonyweb.colony.model.BuildingInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySummary;
import DahRealPanda.plugins.colonyweb.colony.model.MapInfo;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps a top-down surface map per colony, drawn a few chunks at a time on the server thread.
 *
 * <p>Mapping the area around a colony means reading every column of every chunk it covers, which
 * is far too much to do in one go on a ticking server. So a map fills in <em>incrementally</em>:
 * each scheduler pass draws a small budget of chunks, closest to the colony centre first, and
 * the browser watches the coverage climb. Chunks that are already drawn are only refreshed every
 * few minutes, so a finished map costs almost nothing to keep current.</p>
 *
 * <p>Only colonies somebody is actually looking at are mapped — {@link #info(int)} and
 * {@link #png(int)} register that interest, and it lapses shortly after the browser stops
 * asking. A server nobody has opened the map tab on therefore does no mapping work at all.</p>
 */
public final class ColonyMapService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Chunks drawn per server-thread pass — small enough to disappear into a tick. */
    private static final int CHUNKS_PER_PASS = 48;

    /** A chunk that is already on the map is redrawn no more often than this. */
    private static final long CHUNK_REFRESH_MS = 5 * 60 * 1000L;

    /** How long a colony keeps being mapped after the last request for it. */
    private static final long INTEREST_MS = 60_000L;

    /** How long a finished map is kept in memory once nobody is asking for it. */
    private static final long RETAIN_MS = 10 * 60 * 1000L;

    /** Blocks of margin kept around the outermost building. */
    private static final int PADDING = 48;

    /** Smallest map we bother with, in blocks — a brand-new colony is still worth showing. */
    private static final int MIN_SPAN = 128;

    private final ColonyCache cache;
    private final ColonyDataProvider provider;

    private final Map<Integer, ColonyMap> maps = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastRequest = new ConcurrentHashMap<>();

    /** Single thread: PNG encoding is the only off-server-thread work, and it must stay ordered. */
    private final ExecutorService encoder = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "colonyweb-map-encoder");
        thread.setDaemon(true);
        return thread;
    });

    public ColonyMapService(ColonyCache cache, ColonyDataProvider provider) {
        this.cache = cache;
        this.provider = provider;
    }

    public void stop() {
        encoder.shutdownNow();
        maps.clear();
        lastRequest.clear();
    }

    // ------------------------------------------------------------------
    // Read side — HTTP handlers, off the server thread
    // ------------------------------------------------------------------

    /** Where the colony's map sits and how much of it is drawn. Never null. */
    public MapInfo info(int colonyId) {
        MapInfo info = new MapInfo();
        if (!Config.mapEnabled) {
            info.unavailableReason = "The colony map is disabled in the server config.";
            return info;
        }
        Optional<ColonySummary> summary = summary(colonyId);
        Optional<ColonySnapshot> snapshot = cache.snapshot(colonyId);
        if (summary.isEmpty() || snapshot.isEmpty()) {
            info.unavailableReason = "This colony has not been scanned yet.";
            return info;
        }
        touch(colonyId);

        info.available = true;
        info.dimension = summary.get().dimension;
        info.centerX = summary.get().x;
        info.centerY = summary.get().y;
        info.centerZ = summary.get().z;

        ColonyMap map = maps.get(colonyId);
        int[] bounds = map != null
                ? new int[]{map.minX, map.minZ, map.width, map.height}
                : desiredBounds(summary.get(), snapshot.get());
        info.minX = bounds[0];
        info.minZ = bounds[1];
        info.width = bounds[2];
        info.height = bounds[3];
        info.chunksTotal = (bounds[2] >> 4) * (bounds[3] >> 4);

        if (map != null) {
            info.chunksMapped = map.mapped;
            info.version = map.version;
            info.renderedAt = map.renderedAt;
            info.ready = map.png != null;
        }
        return info;
    }

    /** The rendered surface image, or null when nothing has been drawn yet. */
    public byte[] png(int colonyId) {
        touch(colonyId);
        ColonyMap map = maps.get(colonyId);
        return map != null ? map.png : null;
    }

    private void touch(int colonyId) {
        lastRequest.put(colonyId, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------
    // Write side — the refresh scheduler, on the server thread
    // ------------------------------------------------------------------

    /**
     * Draw one budget of chunks for every colony somebody is currently looking at.
     *
     * <p>Must run on the server thread: it reads loaded chunks straight out of the level.</p>
     */
    public void tick() {
        if (!Config.mapEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        forget(now);
        for (Map.Entry<Integer, Long> entry : lastRequest.entrySet()) {
            if (now - entry.getValue() > INTEREST_MS) {
                continue; // still remembered, but nobody is watching it right now
            }
            try {
                pass(entry.getKey(), now);
            } catch (Throwable t) {
                LOGGER.debug("{} map pass failed for colony {}", ColonyWeb.LOG, entry.getKey(), t);
            }
        }
    }

    /**
     * Release maps nobody has looked at in a while, and any whose colony is gone.
     *
     * <p>A map is a couple of int arrays the size of the area it covers, so holding one for every
     * colony that was ever opened would be a slow leak on a busy server. The retention window is
     * long enough that flipping between tabs never throws the drawn chunks away.</p>
     */
    private void forget(long now) {
        lastRequest.entrySet().removeIf(entry -> now - entry.getValue() > RETAIN_MS);
        maps.keySet().removeIf(id -> !lastRequest.containsKey(id) || summary(id).isEmpty());
    }

    private void pass(int colonyId, long now) {
        Optional<ColonySummary> summary = summary(colonyId);
        Optional<ColonySnapshot> snapshot = cache.snapshot(colonyId);
        if (summary.isEmpty() || snapshot.isEmpty()) {
            return;
        }
        ServerLevel level = provider.levelFor(summary.get().dimension);
        if (level == null) {
            return;
        }
        ColonyMap map = ensureMap(colonyId, summary.get(), snapshot.get());
        if (drawBudget(map, level, now)) {
            schedulePng(map);
        }
    }

    /**
     * Draw up to {@link #CHUNKS_PER_PASS} chunks, skipping ones that are recent or not loaded.
     *
     * @return whether anything was drawn
     */
    private boolean drawBudget(ColonyMap map, ServerLevel level, long now) {
        int budget = CHUNKS_PER_PASS;
        int examined = 0;
        int total = map.chunkCount();
        boolean drew = false;

        while (budget > 0 && examined < total) {
            int index = map.order[map.cursor];
            map.cursor = (map.cursor + 1) % total;
            examined++;

            long stamp = map.chunkStamp[index];
            if (stamp != 0 && now - stamp < CHUNK_REFRESH_MS) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource()
                    .getChunkNow(map.chunkX + index % map.chunkCols, map.chunkZ + index / map.chunkCols);
            if (chunk == null) {
                continue; // not loaded — whatever we drew before stays on the map
            }
            SurfaceRenderer.drawChunk(map, level, chunk);
            if (stamp == 0) {
                map.mapped++;
            }
            map.chunkStamp[index] = now;
            budget--;
            drew = true;
        }
        return drew;
    }

    /** Hand copies of the raster to the encoder thread; the server thread never waits on it. */
    private void schedulePng(ColonyMap map) {
        int[] rgb = map.rgb.clone();
        int[] top = map.top.clone();
        int width = map.width;
        int height = map.height;
        encoder.execute(() -> {
            byte[] png = SurfaceRenderer.encode(width, height, rgb, top);
            if (png != null) {
                map.png = png;
                map.renderedAt = System.currentTimeMillis();
                map.version++;
            }
        });
    }

    // ------------------------------------------------------------------
    // Bounds
    // ------------------------------------------------------------------

    /** The colony's map, growing (and inheriting) it when the colony has spread past its edges. */
    private ColonyMap ensureMap(int colonyId, ColonySummary summary, ColonySnapshot snapshot) {
        int[] want = desiredBounds(summary, snapshot);
        ColonyMap current = maps.get(colonyId);
        if (current != null && current.covers(want[0], want[1], want[0] + want[2], want[1] + want[3])) {
            return current;
        }
        ColonyMap grown = new ColonyMap(want[0], want[1], want[2], want[3]);
        if (current != null) {
            grown.inherit(current);
            // Re-encode straight away: the bounds the browser is about to read are the new ones,
            // and the old image no longer lines up with them.
            schedulePng(grown);
            LOGGER.debug("{} colony {} map grew to {}x{} blocks", ColonyWeb.LOG, colonyId, want[2], want[3]);
        }
        maps.put(colonyId, grown);
        return grown;
    }

    /**
     * The block area worth mapping: everything the colony has built, padded, then clamped to
     * {@code mapRadius} around the centre so one far-flung outpost cannot blow the image up.
     *
     * <p>Pure data, so the HTTP side can work out where the map will be before it exists.</p>
     *
     * @return {@code {minX, minZ, width, height}}, chunk-aligned
     */
    private static int[] desiredBounds(ColonySummary summary, ColonySnapshot snapshot) {
        int radius = Config.mapRadius;
        int minX = summary.x;
        int maxX = summary.x;
        int minZ = summary.z;
        int maxZ = summary.z;
        boolean built = false;
        for (BuildingInfo building : snapshot.buildings) {
            minX = built ? Math.min(minX, building.x) : building.x;
            maxX = built ? Math.max(maxX, building.x) : building.x;
            minZ = built ? Math.min(minZ, building.z) : building.z;
            maxZ = built ? Math.max(maxZ, building.z) : building.z;
            built = true;
        }
        // The town hall is one of the buildings, so this is normally the colony centre
        // unchanged. It only bites when MineColonies would not give us a centre at all, and the
        // map would otherwise be anchored on (0, 0) with the colony nowhere near it.
        int anchorX = built ? Math.max(minX, Math.min(maxX, summary.x)) : summary.x;
        int anchorZ = built ? Math.max(minZ, Math.min(maxZ, summary.z)) : summary.z;

        int[] x = align(Math.max(anchorX - radius, minX - PADDING),
                Math.min(anchorX + radius, maxX + PADDING), anchorX);
        int[] z = align(Math.max(anchorZ - radius, minZ - PADDING),
                Math.min(anchorZ + radius, maxZ + PADDING), anchorZ);
        return new int[]{x[0], z[0], x[1], z[1]};
    }

    /** Snap one axis out to chunk borders, keeping at least {@link #MIN_SPAN} around the centre. */
    private static int[] align(int min, int max, int center) {
        if (max - min < MIN_SPAN) {
            min = center - MIN_SPAN / 2;
            max = center + MIN_SPAN / 2;
        }
        int from = Math.floorDiv(min, 16) * 16;
        int to = -Math.floorDiv(-max, 16) * 16;
        return new int[]{from, to - from};
    }

    private Optional<ColonySummary> summary(int colonyId) {
        return cache.summaries().stream().filter(s -> s.id == colonyId).findFirst();
    }
}
