package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.model.BuildingInfo
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.ColonySummary
import DahRealPanda.plugins.colonyweb.model.MapInfo
import DahRealPanda.plugins.colonyweb.renderer.ColonyMap
import DahRealPanda.plugins.colonyweb.renderer.SurfaceRenderer
import DahRealPanda.plugins.colonyweb.repository.ColonyRepository
import com.mojang.logging.LogUtils
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.chunk.LevelChunk
import org.slf4j.Logger
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ColonyMapService(private val repo: ColonyRepository) {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val CHUNKS_PER_PASS = 48
        private const val CHUNK_REFRESH_MS = 5 * 60 * 1000L
        private const val INTEREST_MS = 60_000L
        private const val RETAIN_MS = 10 * 60 * 1000L
        private const val PADDING = 48
        private const val MIN_SPAN = 128
    }

    @Volatile
    private var summaries: List<ColonySummary> = emptyList()
    private val snapshots = ConcurrentHashMap<Int, ColonySnapshot>()
    private val maps = ConcurrentHashMap<Int, ColonyMap>()
    private val lastRequest = ConcurrentHashMap<Int, Long>()
    private val encoder: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "colonyweb-map-encoder").also { it.isDaemon = true }
    }

    fun updateSummaries(list: List<ColonySummary>) { summaries = list }
    fun updateSnapshot(colonyId: Int, snapshot: ColonySnapshot) { snapshots[colonyId] = snapshot }
    fun retainOnly(current: List<Int>) { snapshots.keys.removeIf { it !in current } }

    fun stop() { encoder.shutdownNow(); maps.clear(); lastRequest.clear() }

    fun info(colonyId: Int): MapInfo {
        val info = MapInfo()
        if (!Config.mapEnabled) { info.unavailableReason = "The colony map is disabled in the server config."; return info }
        val summary = summary(colonyId)
        val snapshot = snapshots[colonyId]
        if (!summary.isPresent || snapshot == null) { info.unavailableReason = "This colony has not been scanned yet."; return info }
        touch(colonyId)
        info.available = true
        val s = summary.get()
        info.dimension = s.dimension; info.centerX = s.x; info.centerY = s.y; info.centerZ = s.z
        val map = maps[colonyId]
        val bounds = if (map != null) intArrayOf(map.minX, map.minZ, map.width, map.height)
        else desiredBounds(s, snapshot)
        info.minX = bounds[0]; info.minZ = bounds[1]; info.width = bounds[2]; info.height = bounds[3]
        info.chunksTotal = (bounds[2] shr 4) * (bounds[3] shr 4)
        if (map != null) { info.chunksMapped = map.mapped; info.version = map.version; info.renderedAt = map.renderedAt; info.ready = map.png != null }
        return info
    }

    fun png(colonyId: Int): ByteArray? { touch(colonyId); return maps[colonyId]?.png }

    private fun touch(colonyId: Int) { lastRequest[colonyId] = System.currentTimeMillis() }

    fun tick() {
        if (!Config.mapEnabled) return
        val now = System.currentTimeMillis()
        forget(now)
        for ((id, requestedAt) in lastRequest) {
            if (now - requestedAt > INTEREST_MS) continue
            try { pass(id, now) } catch (t: Throwable) { LOGGER.debug("{} map pass failed for colony {}", ColonyWeb.LOG, id, t) }
        }
    }

    private fun forget(now: Long) {
        lastRequest.entries.removeIf { now - it.value > RETAIN_MS }
        maps.keys.removeIf { !lastRequest.containsKey(it) || summary(it).isEmpty }
    }

    private fun pass(colonyId: Int, now: Long) {
        val s = summary(colonyId)
        val snapshot = snapshots[colonyId]
        if (!s.isPresent || snapshot == null) return
        val level = repo.levelFor(s.get().dimension)
        val map = ensureMap(colonyId, s.get(), snapshot)
        if (drawBudget(map, level, now)) schedulePng(map)
    }

    private fun drawBudget(map: ColonyMap, level: ServerLevel, now: Long): Boolean {
        var budget = CHUNKS_PER_PASS; var examined = 0; val total = map.chunkCount(); var drew = false
        while (budget > 0 && examined < total) {
            val index = map.order[map.cursor]; map.cursor = (map.cursor + 1) % total; examined++
            val stamp = map.chunkStamp[index]
            if (stamp != 0L && now - stamp < CHUNK_REFRESH_MS) continue
            val chunk = level.chunkSource.getChunkNow(map.chunkX + index % map.chunkCols, map.chunkZ + index / map.chunkCols) ?: continue
            SurfaceRenderer.drawChunk(map, level, chunk)
            if (stamp == 0L) map.mapped++
            map.chunkStamp[index] = now; budget--; drew = true
        }
        return drew
    }

    private fun schedulePng(map: ColonyMap) {
        val rgb = map.rgb.clone(); val top = map.top.clone(); val width = map.width; val height = map.height
        encoder.execute {
            val png = SurfaceRenderer.encode(width, height, rgb, top)
            if (png != null) { map.png = png; map.renderedAt = System.currentTimeMillis(); map.version++ }
        }
    }

    private fun ensureMap(colonyId: Int, summary: ColonySummary, snapshot: ColonySnapshot): ColonyMap {
        val want = desiredBounds(summary, snapshot)
        val current = maps[colonyId]
        if (current != null && current.covers(want[0], want[1], want[0] + want[2], want[1] + want[3])) return current
        val grown = ColonyMap(want[0], want[1], want[2], want[3])
        if (current != null) { grown.inherit(current); schedulePng(grown) }
        maps[colonyId] = grown
        return grown
    }

    private fun desiredBounds(summary: ColonySummary, snapshot: ColonySnapshot): IntArray {
        val radius = Config.mapRadius
        var minX = summary.x; var maxX = summary.x; var minZ = summary.z; var maxZ = summary.z; var built = false
        for (building in snapshot.buildings) {
            minX = if (built) minOf(minX, building.x) else building.x
            maxX = if (built) maxOf(maxX, building.x) else building.x
            minZ = if (built) minOf(minZ, building.z) else building.z
            maxZ = if (built) maxOf(maxZ, building.z) else building.z
            built = true
        }
        val anchorX = if (built) maxOf(minX, minOf(maxX, summary.x)) else summary.x
        val anchorZ = if (built) maxOf(minZ, minOf(maxZ, summary.z)) else summary.z
        val x = align(maxOf(anchorX - radius, minX - PADDING), minOf(anchorX + radius, maxX + PADDING), anchorX)
        val z = align(maxOf(anchorZ - radius, minZ - PADDING), minOf(anchorZ + radius, maxZ + PADDING), anchorZ)
        return intArrayOf(x[0], z[0], x[1], z[1])
    }

    private fun align(min: Int, max: Int, center: Int): IntArray {
        var m = min; var M = max
        if (M - m < MIN_SPAN) { m = center - MIN_SPAN / 2; M = center + MIN_SPAN / 2 }
        return intArrayOf(Math.floorDiv(m, 16) * 16, -Math.floorDiv(-M, 16) * 16 - Math.floorDiv(m, 16) * 16)
    }

    private fun summary(colonyId: Int): Optional<ColonySummary> =
        summaries.stream().filter { it.id == colonyId }.findFirst()
}
