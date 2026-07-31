package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeAny
import DahRealPanda.plugins.colonyweb.model.BuildingInfo
import DahRealPanda.plugins.colonyweb.model.CitizenInfo
import DahRealPanda.plugins.colonyweb.model.CombatInfo
import DahRealPanda.plugins.colonyweb.model.EquipmentInfo
import DahRealPanda.plugins.colonyweb.util.ScanCoercion
import DahRealPanda.plugins.colonyweb.util.Text
import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

class CombatService {
    private val data = ConcurrentHashMap<Int, CombatInfo>()

    fun combat(colonyId: Int): CombatInfo? = data[colonyId]
    fun store(colonyId: Int, info: CombatInfo) { data[colonyId] = info }
    fun retainOnly(current: List<Int>) { data.keys.removeIf { it !in current } }
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private val GUARD_JOBS = setOf(
            "knight", "ranger", "druid", "archer", "guard", "samurai", "combat",
            "archertraining", "combattraining", "knighttraining")
        private val GUARD_NAME = Regex("""(?i)\bguards?\b""")
        private val GUARD_BUILDINGS = setOf("guardtower", "barracks", "barrackstower", "archery", "combatacademy")
    }

    fun scan(colony: Any, citizens: List<CitizenInfo>,
             equipment: Map<Int, List<EquipmentInfo>>,
             buildingByPos: Map<BlockPos, BuildingInfo>,
             rawBuildingByPos: Map<BlockPos, Any>): CombatInfo {
        val info = CombatInfo()
        try {
            readRaiders(colony, info)
            readGraves(colony, info)
            readEvents(colony, info)
            readPosts(buildingByPos, rawBuildingByPos, info)
            readGuards(citizens, equipment, buildingByPos, info)
        } catch (t: Throwable) {
            LOGGER.debug("[ColonyWeb] combat scan failed", t)
        }
        return info
    }

    private fun readRaiders(colony: Any, info: CombatInfo) {
        val raiders = invokeAny(colony, "getRaiderManager").orElse(null) ?: return
        info.underAttack = ScanCoercion.boolOf(invokeAny(raiders, "isRaided").orElse(null), false)
        info.raidsPossible = ScanCoercion.boolOf(invokeAny(raiders, "canHaveRaiderEvents").orElse(null), false)
        info.spiesEnabled = ScanCoercion.boolOf(invokeAny(raiders, "areSpiesEnabled").orElse(null), false)
        info.nightsSinceRaid = ScanCoercion.intOf(invokeAny(raiders, "getNightsSinceLastRaid").orElse(null), 0)
        info.raidLevel = ScanCoercion.intOf(ScanCoercion.firstNonNull(
            invokeAny(raiders, "getColonyRaidLevel").orElse(null),
            invokeAny(raiders, "getColonyRaidLevelHelper").orElse(null)), 0)
    }

    private fun readGraves(colony: Any, info: CombatInfo) {
        val graveManager = ScanCoercion.firstNonNull(
            invokeAny(colony, "getGraveManager").orElse(null),
            invokeAny(colony, "getGraveyardManager").orElse(null))
        val graves = invokeAny(graveManager, "getGraves").orElse(null)
        if (graves is Map<*, *>) info.graves = graves.size
        else if (graves is Collection<*>) info.graves = graves.size
    }

    private fun readEvents(colony: Any, info: CombatInfo) {
        val manager = invokeAny(colony, "getEventManager").orElse(null)
        val events = invokeAny(manager, "getEvents").orElse(null)
        val values: Collection<*> = when (events) {
            is Map<*, *> -> events.values
            is Collection<*> -> events
            else -> return
        }
        for (raw in values) {
            val event = CombatInfo.Event()
            event.id = ScanCoercion.intOf(invokeAny(raw, "getID").orElse(null), -1)
            event.name = Text.displayName(ScanCoercion.firstNonNull(
                invokeAny(raw, "getEventTypeID").orElse(null),
                invokeAny(raw, "getName").orElse(null)), "Colony event")
            event.status = Text.humanize(invokeAny(raw, "getStatus").orElse("").toString())
            val pos = ScanCoercion.blockPosOf(invokeAny(raw, "getPosition").orElse(null))
            if (pos != null) { event.x = pos.x; event.y = pos.y; event.z = pos.z }
            info.events.add(event)
        }
    }

    private fun readPosts(buildingByPos: Map<BlockPos, BuildingInfo>,
                          rawBuildingByPos: Map<BlockPos, Any>, info: CombatInfo) {
        for ((pos, building) in buildingByPos) {
            if (!isGuardBuilding(building.type)) continue
            val raw = rawBuildingByPos[pos] ?: continue
            val post = CombatInfo.Post()
            post.id = building.id; post.name = building.name; post.type = building.type
            post.blockId = building.blockId ?: ""; post.level = building.level
            post.x = building.x; post.y = building.y; post.z = building.z
            val assigned = invokeAny(raw, "getAllAssignedCitizen").orElse(null)
            post.assigned = if (assigned is Collection<*>) assigned.size else 0
            post.capacity = ScanCoercion.intOf(ScanCoercion.firstNonNull(
                invokeAny(raw, "getMaxInhabitants").orElse(null),
                invokeAny(raw, "getGuardSlots").orElse(null)), post.assigned)
            info.posts.add(post)
            info.guardCapacity += post.capacity
        }
        info.posts.sortWith { a, b ->
            Text.stringOrEmpty(a.name).compareTo(Text.stringOrEmpty(b.name), ignoreCase = true)
        }
    }

    private fun readGuards(citizens: List<CitizenInfo>,
                           equipment: Map<Int, List<EquipmentInfo>>,
                           buildingByPos: Map<BlockPos, BuildingInfo>,
                           info: CombatInfo) {
        val buildingById = mutableMapOf<Int, BuildingInfo>()
        buildingByPos.values.forEach { building -> buildingById[building.id] = building }
        var levelSum = 0.0
        var healthSum = 0.0
        for (citizen in citizens) {
            if (!isGuardJob(citizen.jobType, citizen.job)) continue
            val guard = CombatInfo.Guard()
            guard.id = citizen.id; guard.name = citizen.name; guard.job = citizen.job
            guard.jobType = citizen.jobType ?: ""
            guard.level = bestCombatLevel(citizen)
            guard.health = citizen.health; guard.maxHealth = citizen.maxHealth
            guard.spawned = citizen.spawned
            guard.x = citizen.x; guard.y = citizen.y; guard.z = citizen.z
            guard.building = citizen.workBuilding; guard.buildingId = citizen.workBuildingId
            val post = buildingById[citizen.workBuildingId]
            if (post != null) {
                guard.building = post.name; guard.buildingLevel = post.level
            }
            applyEquipment(guard, equipment[citizen.id])
            info.guards.add(guard)
            levelSum += guard.level
            healthSum += if (guard.maxHealth > 0) guard.health / guard.maxHealth else 0.0
        }
        info.guardCount = info.guards.size
        if (info.guardCount > 0) {
            info.averageGuardLevel = levelSum / info.guardCount
            info.averageHealthPct = healthSum / info.guardCount * 100.0
        }
        info.guards.sortWith { a, b ->
            if (a.armorPoints != b.armorPoints) b.armorPoints - a.armorPoints
            else b.level - a.level
        }
    }

    private fun applyEquipment(guard: CombatInfo.Guard, equipment: List<EquipmentInfo>?) {
        if (equipment.isNullOrEmpty()) return
        guard.equipment = equipment.toMutableList()
        for (item in equipment) {
            guard.armorPoints += item.armorPoints
            if ("Main hand" == item.slot) guard.weapon = item.name
        }
    }

    private fun bestCombatLevel(citizen: CitizenInfo): Int {
        var best = 0
        for (skill in citizen.skills) {
            if ("primary" == skill.role) return skill.level
            best = maxOf(best, skill.level)
        }
        return best
    }

    private fun isGuardJob(jobType: String?, jobName: String?): Boolean {
        val path = Text.pathOf(jobType).lowercase().replace("_", "")
        if (GUARD_JOBS.contains(path)) return true
        return jobName != null && GUARD_NAME.containsMatchIn(jobName)
    }

    private fun isGuardBuilding(type: String): Boolean {
        val path = Text.pathOf(type).lowercase().replace("_", "")
        return GUARD_BUILDINGS.contains(path)
    }
}
