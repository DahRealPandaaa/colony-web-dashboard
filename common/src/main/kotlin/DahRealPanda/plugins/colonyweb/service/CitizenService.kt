package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.BuildingInfo
import DahRealPanda.plugins.colonyweb.model.CitizenInfo
import DahRealPanda.plugins.colonyweb.model.EquipmentInfo
import DahRealPanda.plugins.colonyweb.model.ItemCount
import DahRealPanda.plugins.colonyweb.platform.Platform
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeAny
import DahRealPanda.plugins.colonyweb.util.ScanCoercion
import DahRealPanda.plugins.colonyweb.util.Text
import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import org.slf4j.Logger
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

class CitizenResult(
    val citizens: List<CitizenInfo>,
    val inventories: Map<Int, List<ItemCount>>,
    val rawCitizens: List<Any>
)

class CitizenService {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val SKILL_ENUM = "com.minecolonies.api.entity.citizen.Skill"
    }

    private val citizens = ConcurrentHashMap<Int, List<CitizenInfo>>()
    private val inventories = ConcurrentHashMap<Int, Map<Int, List<ItemCount>>>()
    private val equipment = ConcurrentHashMap<Int, Map<Int, List<EquipmentInfo>>>()

    fun citizens(colonyId: Int): List<CitizenInfo>? = citizens[colonyId]
    fun citizen(colonyId: Int, citizenId: Int): CitizenInfo? =
        citizens(colonyId)?.firstOrNull { it.id == citizenId }

    fun storeCitizens(colonyId: Int, list: List<CitizenInfo>) { citizens[colonyId] = list }
    fun storeInventories(colonyId: Int, map: Map<Int, List<ItemCount>>) { inventories[colonyId] = map }
    fun storeEquipment(colonyId: Int, map: Map<Int, List<EquipmentInfo>>) { equipment[colonyId] = map }

    fun inventory(colonyId: Int, citizenId: Int): List<ItemCount> =
        inventories[colonyId]?.get(citizenId) ?: emptyList()

    fun equipment(colonyId: Int, citizenId: Int): List<EquipmentInfo> =
        equipment[colonyId]?.get(citizenId) ?: emptyList()

    fun retainOnly(current: List<Int>) {
        citizens.keys.removeIf { it !in current }
        inventories.keys.removeIf { it !in current }
        equipment.keys.removeIf { it !in current }
    }

    fun scan(colony: Any, buildingByPos: Map<BlockPos, BuildingInfo>): CitizenResult {
        val citizensList = mutableListOf<CitizenInfo>()
        val inventoriesMap = mutableMapOf<Int, MutableList<ItemCount>>()
        val rawList = mutableListOf<Any>()

        val manager = invokeAny(colony, "getCitizenManager").orElse(null)
            ?: return CitizenResult(citizensList, emptyMap(), rawList)
        val raw = invokeAny(manager, "getCitizens").orElse(null)
        if (raw !is Collection<*>) return CitizenResult(citizensList, emptyMap(), rawList)

        for (citizen in raw) {
            if (citizen == null) continue
            try {
                val info = readCitizen(colony, citizen, buildingByPos)
                citizensList.add(info)
                inventoriesMap[info.id] = readInventory(citizen, info)
                rawList.add(citizen)
            } catch (t: Throwable) {
                LOGGER.debug("[ColonyWeb] failed to read a citizen", t)
            }
        }
        citizensList.sortWith { a, b ->
            val byJob = jobRank(a) - jobRank(b)
            if (byJob != 0) byJob
            else ScanCoercion.stringOf(a.name, "").compareTo(ScanCoercion.stringOf(b.name, ""), ignoreCase = true)
        }
        return CitizenResult(citizensList, inventoriesMap, rawList)
    }

    private fun jobRank(c: CitizenInfo): Int {
        if (c.jobType != null) return 0
        return if (c.child) 1 else 2
    }

    private fun readCitizen(colony: Any, citizen: Any, buildingByPos: Map<BlockPos, BuildingInfo>): CitizenInfo {
        val info = CitizenInfo()
        info.id = ScanCoercion.intOf(invokeAny(citizen, "getId").orElse(null), -1)
        info.name = ScanCoercion.stringOf(invokeAny(citizen, "getName").orElse(null), "Citizen ${info.id}")
        info.child = ScanCoercion.boolOf(invokeAny(citizen, "isChild").orElse(null), false)
        info.female = ScanCoercion.boolOf(invokeAny(citizen, "isFemale").orElse(null), false)
        info.saturation = ScanCoercion.doubleOf(invokeAny(citizen, "getSaturation").orElse(null), 0.0)
        readJob(citizen, info)
        readBuildings(citizen, info, buildingByPos)
        readEntity(citizen, info)
        readHappiness(colony, citizen, info)
        readSkills(citizen, info)
        readStatus(citizen, info)
        return info
    }

    private fun readJob(citizen: Any, info: CitizenInfo) {
        val job = invokeAny(citizen, "getJob").orElse(null)
        if (job == null) { info.job = if (info.child) "Child" else "Unemployed"; return }
        val entry = invokeAny(job, "getJobRegistryEntry").orElse(null)
        val key = ScanCoercion.firstNonNull(
            invokeAny(entry, "getKey").orElse(null), invokeAny(entry, "getRegistryName").orElse(null))
        if (key != null) {
            info.jobType = key.toString()
            info.job = Text.humanize(Text.pathOf(info.jobType))
        } else {
            val name = Text.componentString(invokeAny(job, "getName").orElse(null))
            if (name != null && name.isNotBlank()) info.job = Text.displayName(name, "Worker")
            else info.job = Text.humanize(job.javaClass.simpleName.replaceFirst("^Job".toRegex(), ""))
        }
        info.primarySkill = skillName(ScanCoercion.firstNonNull(
            invokeAny(job, "getPrimarySkill").orElse(null),
            invokeAny(invokeAny(citizen, "getWorkBuilding").orElse(null), "getPrimarySkill").orElse(null))) ?: ""
        info.secondarySkill = skillName(ScanCoercion.firstNonNull(
            invokeAny(job, "getSecondarySkill").orElse(null),
            invokeAny(invokeAny(citizen, "getWorkBuilding").orElse(null), "getSecondarySkill").orElse(null))) ?: ""
    }

    private fun skillName(skill: Any?): String? =
        if (skill == null) null else Text.humanize(skill.toString())

    private fun readBuildings(citizen: Any, info: CitizenInfo, buildingByPos: Map<BlockPos, BuildingInfo>) {
        val work = invokeAny(citizen, "getWorkBuilding").orElse(null)
        val workPos = ScanCoercion.blockPosOf(invokeAny(work, "getID").orElse(null))
        val workInfo = if (workPos != null) buildingByPos[workPos] else null
        if (workInfo != null) {
            info.workBuilding = workInfo.name; info.workBuildingId = workInfo.id; info.jobIcon = workInfo.blockId
        }
        val home = invokeAny(citizen, "getHomeBuilding").orElse(null)
        val homePos = ScanCoercion.blockPosOf(invokeAny(home, "getID").orElse(null))
        val homeInfo = if (homePos != null) buildingByPos[homePos] else null
        if (homeInfo != null) { info.homeBuilding = homeInfo.name; info.homeBuildingId = homeInfo.id }
    }

    private fun readEntity(citizen: Any, info: CitizenInfo) {
        var entity = invokeAny(citizen, "getEntity").orElse(null)
        if (entity is Optional<*>) entity = entity.orElse(null)
        if (entity is LivingEntity) {
            info.spawned = true
            info.health = entity.health.toDouble(); info.maxHealth = entity.maxHealth.toDouble()
            val pos = entity.blockPosition(); info.x = pos.x; info.y = pos.y; info.z = pos.z
            return
        }
        val last = ScanCoercion.blockPosOf(ScanCoercion.firstNonNull(
            invokeAny(citizen, "getLastPosition").orElse(null), invokeAny(citizen, "getPosition").orElse(null)))
        if (last != null) { info.x = last.x; info.y = last.y; info.z = last.z }
        info.maxHealth = 20.0
    }

    private fun readHappiness(colony: Any, citizen: Any, info: CitizenInfo) {
        val handler = invokeAny(citizen, "getCitizenHappinessHandler").orElse(null) ?: return
        info.happiness = ScanCoercion.doubleOf(invokeAny(handler, "getHappiness", colony, citizen).orElse(null), 0.0)
        val modifiers = invokeAny(handler, "getModifiers").orElse(null)
        if (modifiers !is Collection<*>) return
        for (modifier in modifiers) {
            val id = if (modifier is String) modifier else invokeAny(modifier, "getId").orElse(null) ?: continue
            val factor = if (modifier is String) ScanCoercion.firstNonNull(
                invokeAny(handler, "getModifierFactor", id.toString(), citizen).orElse(null),
                invokeAny(invokeAny(handler, "getModifier", id.toString()).orElse(null), "getFactor", citizen).orElse(null))
            else invokeAny(modifier, "getFactor", citizen).orElse(null)
            info.modifiers.add(CitizenInfo.Modifier(Text.displayName(id.toString(), id.toString()),
                ScanCoercion.doubleOf(factor, 1.0)))
        }
    }

    private fun readSkills(citizen: Any, info: CitizenInfo) {
        val handler = invokeAny(citizen, "getCitizenSkillHandler").orElse(null) ?: return
        val skillClass = MineColoniesReflect.resolve(SKILL_ENUM).orElse(null)
        val constants = skillClass?.enumConstants
        val skills = invokeAny(handler, "getSkills").orElse(null)
        val skillMap = if (skills is Map<*, *>) skills else null
        val keys = mutableListOf<Any>()
        if (constants != null) keys.addAll(constants)
        else if (skillMap != null) keys.addAll(skillMap.keys.filterNotNull())
        for (skill in keys) {
            var level = ScanCoercion.intOf(invokeAny(handler, "getLevel", skill).orElse(null), 0)
            var xp = 0.0
            val data = skillMap?.get(skill)
            if (data != null) {
                if (level == 0) level = ScanCoercion.intOf(ScanCoercion.firstNonNull(
                    invokeAny(data, "getLevel").orElse(null), invokeAny(data, "getA").orElse(null)), 0)
                xp = ScanCoercion.doubleOf(ScanCoercion.firstNonNull(
                    invokeAny(data, "getExperience").orElse(null), invokeAny(data, "getB").orElse(null)), 0.0)
            }
            val entry = CitizenInfo.Skill(Text.humanize(skill.toString()), level, xp)
            if (entry.name.equals(info.primarySkill, ignoreCase = true)) entry.role = "primary"
            else if (entry.name.equals(info.secondarySkill, ignoreCase = true)) entry.role = "secondary"
            info.skills.add(entry); info.skillTotal += level
        }
    }

    private fun readStatus(citizen: Any, info: CitizenInfo) {
        val status = invokeAny(citizen, "getStatus").orElse(null) ?: return
        val id = ScanCoercion.firstNonNull(invokeAny(status, "getId").orElse(null), status)
        info.status = Text.displayName(id.toString(), "Unknown")
    }

    private fun readInventory(citizen: Any, info: CitizenInfo): MutableList<ItemCount> {
        val out = mutableListOf<ItemCount>()
        val inventory = invokeAny(citizen, "getInventory").orElse(null)
        val handler = Platform.get().asItemSlots(inventory) ?: return out
        info.inventorySize = handler.getSlots()
        for (slot in 0 until handler.getSlots()) {
            val stack = handler.getStackInSlot(slot)
            if (stack == null || stack.isEmpty) continue
            out.add(ScanCoercion.itemCount(stack, stack.count, slot))
        }
        info.inventoryUsed = out.size
        return out
    }
}
