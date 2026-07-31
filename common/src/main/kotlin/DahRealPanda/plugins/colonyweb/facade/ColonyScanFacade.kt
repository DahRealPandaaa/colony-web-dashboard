package DahRealPanda.plugins.colonyweb.facade

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.model.ColonyScan
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.ColonySummary
import DahRealPanda.plugins.colonyweb.repository.ColonyRepository
import DahRealPanda.plugins.colonyweb.service.BuildingService
import DahRealPanda.plugins.colonyweb.service.CitizenService
import DahRealPanda.plugins.colonyweb.service.ColonyMapService
import DahRealPanda.plugins.colonyweb.service.CombatService
import DahRealPanda.plugins.colonyweb.service.EquipmentService
import DahRealPanda.plugins.colonyweb.service.RecipeService
import DahRealPanda.plugins.colonyweb.service.ResearchService
import DahRealPanda.plugins.colonyweb.service.ScanHasher
import DahRealPanda.plugins.colonyweb.service.SseService
import DahRealPanda.plugins.colonyweb.service.StatsService
import DahRealPanda.plugins.colonyweb.service.WarehouseService
import DahRealPanda.plugins.colonyweb.service.WorkOrderService
import com.google.gson.JsonObject
import com.mojang.logging.LogUtils
import net.minecraft.server.level.ServerLevel
import org.slf4j.Logger
import java.util.UUID

class ColonyScanFacade(
    private val repo: ColonyRepository,
    private val buildingService: BuildingService,
    private val warehouseService: WarehouseService,
    private val workOrderService: WorkOrderService,
    private val citizenService: CitizenService,
    private val equipmentService: EquipmentService,
    private val combatService: CombatService,
    private val researchService: ResearchService,
    private val recipeService: RecipeService,
    private val statsService: StatsService,
    private val maps: ColonyMapService,
    private val sseService: SseService
) {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val RESEARCH_EVERY_N_SCANS = 5
    }

    private val colonyHashes = mutableMapOf<Int, Int>()
    private var coloniesSetHash = 0
    private var ticks = 0

    val isAvailable: Boolean get() = repo.isAvailable

    fun levelFor(dimension: String): ServerLevel = repo.levelFor(dimension)

    fun coloniesFor(playerId: UUID, playerName: String): List<Int> =
        if (isAvailable) repo.coloniesFor(playerId, playerName) else emptyList()

    fun listColonies(): List<ColonySummary> {
        val summaries = mutableListOf<ColonySummary>()
        if (!isAvailable) return summaries
        for (colony in repo.allColonies()) {
            try { summaries.add(summarize(colony)) }
            catch (t: Throwable) { LOGGER.debug("{} failed to summarize a colony", ColonyWeb.LOG, t) }
        }
        return summaries
    }

    fun tick() {
        try {
            val summaries = listColonies()
            buildingService.setSummaries(summaries)
            maps.updateSummaries(summaries)

            buildingService.retainOnly(summaries)
            citizenService.retainOnly(summaries.map { it.id })
            combatService.retainOnly(summaries.map { it.id })
            researchService.retainOnly(summaries.map { it.id })
            maps.retainOnly(summaries.map { it.id })

            broadcastColonySetChange(summaries)

            val withResearch = ticks % RESEARCH_EVERY_N_SCANS == 0
            for (summary in summaries) {
                val needsResearch = withResearch || researchService.research(summary.id) == null
                val scanResult = scan(summary.id, needsResearch)
                if (scanResult != null) publish(summary.id, scanResult)
            }

            maps.tick()
        } catch (e: Exception) { LOGGER.debug("{} scan failed", ColonyWeb.LOG, e) }
    }

    fun scan(colonyId: Int, includeResearch: Boolean): ColonyScan? {
        if (!isAvailable) return null
        val colony = repo.colonyById(colonyId) ?: return null
        return try { buildScan(colony, includeResearch) }
        catch (t: Throwable) { LOGGER.debug("{} failed to scan colony {}", ColonyWeb.LOG, colonyId, t); null }
    }

    private fun buildScan(colony: Any, includeResearch: Boolean): ColonyScan {
        val snapshot = ColonySnapshot()
        snapshot.id = repo.idOf(colony); snapshot.name = repo.nameOf(colony)
        snapshot.dimension = repo.dimensionOf(colony); snapshot.owner = repo.ownerOf(colony)
        val level = repo.levelFor(snapshot.dimension)

        val rawBuildings = repo.buildingsOf(colony)
        val buildingResult = buildingService.scan(rawBuildings, level)
        for (info in buildingResult.buildings) snapshot.buildings.add(info)

        warehouseService.scan(rawBuildings, buildingResult, level, snapshot.warehouse)
        workOrderService.scan(repo.workOrdersOf(colony), buildingResult, snapshot, snapshot.warehouse)

        val scan = ColonyScan(); scan.snapshot = snapshot

        val citizenResult = citizenService.scan(colony, buildingResult.buildingByPos)
        scan.citizens = citizenResult.citizens; scan.inventories = citizenResult.inventories

        val equipment = equipmentService.scan(citizenResult.rawCitizens)
        scan.equipment = equipment

        scan.combat = combatService.scan(colony, citizenResult.citizens, equipment,
            buildingResult.buildingByPos, buildingResult.rawBuildingByPos)

        if (includeResearch) scan.research = researchService.scan(colony)

        val builderCount = buildingService.countBuilders(buildingResult.buildings)
        statsService.fill(colony, snapshot, citizenResult.citizens, scan.combat, builderCount)

        val craftableKeys = recipeService.scan(rawBuildings)
        RecipeService.markCraftable(scan, craftableKeys)
        return scan
    }

    private fun publish(colonyId: Int, scan: ColonyScan) {
        val snapshot = scan.snapshot
        val scannedResearch = scan.research
        if (scannedResearch != null) researchService.store(colonyId, scannedResearch)

        val r = researchService.research(colonyId)
        if (r != null) {
            snapshot.stats.researchCompleted = r.completed
            snapshot.stats.researchInProgress = r.inProgress
        }

        buildingService.storeSnapshot(colonyId, snapshot)
        maps.updateSnapshot(colonyId, snapshot)
        citizenService.storeCitizens(colonyId, scan.citizens)
        combatService.store(colonyId, scan.combat)
        citizenService.storeInventories(colonyId, scan.inventories)
        citizenService.storeEquipment(colonyId, scan.equipment)

        val hash = ScanHasher.hash(scan)
        val previous = colonyHashes.put(colonyId, hash)
        if (previous == null || previous != hash) {
            val obj = JsonObject()
            obj.addProperty("type", "colony")
            obj.addProperty("id", colonyId)
            sseService.broadcast(obj)
        }
    }

    private fun broadcastColonySetChange(summaries: List<ColonySummary>) {
        val setHash = summaries.sumOf { it.id } * 31 + summaries.size
        if (setHash != coloniesSetHash) {
            coloniesSetHash = setHash
            val obj = JsonObject()
            obj.addProperty("type", "colonies")
            sseService.broadcast(obj)
        }
    }

    private fun summarize(colony: Any): ColonySummary {
        val id = repo.idOf(colony)
        val rawBuildings = repo.buildingsOf(colony)
        val center = repo.centerOf(colony)
        return ColonySummary(id = id, name = repo.nameOf(colony), dimension = repo.dimensionOf(colony),
            owner = repo.ownerOf(colony), x = center?.x ?: 0, y = center?.y ?: 0, z = center?.z ?: 0,
            buildingCount = rawBuildings.size, builderCount = buildingService.countRawBuilders(rawBuildings),
            activeWorkOrders = repo.workOrdersOf(colony).size)
    }
}
