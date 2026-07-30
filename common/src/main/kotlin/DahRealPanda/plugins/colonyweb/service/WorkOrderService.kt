package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invoke
import DahRealPanda.plugins.colonyweb.model.BuilderInfo
import DahRealPanda.plugins.colonyweb.model.BuildingInfo
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.ResourceEntry
import DahRealPanda.plugins.colonyweb.model.WorkOrderInfo
import DahRealPanda.plugins.colonyweb.util.ScanCoercion
import DahRealPanda.plugins.colonyweb.util.Text
import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import org.slf4j.Logger

class WorkOrderService {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
    }

    fun scan(workOrders: Collection<Any>, buildingResult: BuildingResult,
             snapshot: ColonySnapshot, warehouse: ColonySnapshot.Warehouse) {
        for (workOrder in workOrders) {
            try {
                snapshot.workOrders.add(read(workOrder, buildingResult, snapshot, warehouse))
            } catch (t: Throwable) {
                LOGGER.debug("{} failed to read a work order", ColonyWeb.LOG, t)
            }
        }
    }

    private fun read(workOrder: Any, buildingResult: BuildingResult,
                     snapshot: ColonySnapshot, warehouse: ColonySnapshot.Warehouse): WorkOrderInfo {
        val info = WorkOrderInfo()
        info.id = ScanCoercion.intOf(invoke(workOrder, "getID").orElse(null), -1)

        val target = ScanCoercion.blockPosOf(ScanCoercion.firstNonNull(
            invoke(workOrder, "getLocation").orElse(null),
            invoke(workOrder, "getBuildingLocation").orElse(null)))
        if (target != null) {
            info.x = target.x; info.y = target.y; info.z = target.z
        }
        info.currentLevel = ScanCoercion.intOf(invoke(workOrder, "getCurrentLevel").orElse(null), 0)
        info.targetLevel = ScanCoercion.intOf(invoke(workOrder, "getTargetLevel").orElse(null), 0)
        info.action = actionOf(workOrder, info.currentLevel, info.targetLevel)

        val structureName = nameOf(workOrder)
        var targetBuilding = if (target != null) buildingResult.buildingByPos[target] else null
        var decoration = false
        if (targetBuilding == null && target != null) {
            targetBuilding = addDecoration(target, structureName, info.currentLevel, buildingResult, snapshot)
            decoration = true
        }
        if (targetBuilding != null) {
            info.buildingType = targetBuilding.type
            info.buildingName = if (decoration && structureName != null) structureName else targetBuilding.name
            targetBuilding.beingBuilt = true
            targetBuilding.workOrderId = info.id
        } else if (structureName != null) {
            info.buildingName = structureName
        }

        linkBuilder(workOrder, info, targetBuilding, buildingResult, snapshot, warehouse)
        return info
    }

    private fun addDecoration(pos: BlockPos, name: String?, level: Int,
                              buildingResult: BuildingResult, snapshot: ColonySnapshot): BuildingInfo {
        val decoration = BuildingInfo()
        decoration.id = pos.hashCode()
        decoration.kind = "decoration"
        decoration.type = "decoration"
        decoration.name = name ?: "Decoration"
        decoration.level = level
        decoration.x = pos.x; decoration.y = pos.y; decoration.z = pos.z
        snapshot.buildings.add(decoration)
        buildingResult.buildingByPos[pos] = decoration
        return decoration
    }

    private fun linkBuilder(workOrder: Any, info: WorkOrderInfo, targetBuilding: BuildingInfo?,
                            buildingResult: BuildingResult, snapshot: ColonySnapshot,
                            warehouse: ColonySnapshot.Warehouse) {
        val claimedBy = ScanCoercion.blockPosOf(ScanCoercion.firstNonNull(
            invoke(workOrder, "getClaimedBy").orElse(null),
            invoke(workOrder, "getClaimedByBuilding").orElse(null)))
        if (claimedBy == null || BlockPos.ZERO == claimedBy) return
        val builderBuilding = buildingResult.rawBuildingByPos[claimedBy]
        val builder = ensureBuilder(snapshot, claimedBy, builderBuilding)
        builder.assignedWorkOrderId = info.id
        info.builderId = builder.id
        info.builderName = builder.name

        if (builderBuilding == null) return
        info.progress = progressOf(workOrder, builderBuilding)
        if (targetBuilding != null) {
            targetBuilding.required.addAll(neededResources(builderBuilding, warehouse))
        }
    }

    private fun ensureBuilder(snapshot: ColonySnapshot, hutPos: BlockPos, rawBuilding: Any?): BuilderInfo {
        for (existing in snapshot.builders) {
            if (existing.hutX == hutPos.x && existing.hutY == hutPos.y && existing.hutZ == hutPos.z) return existing
        }
        val builder = BuilderInfo()
        builder.id = hutPos.hashCode()
        builder.hutX = hutPos.x; builder.hutY = hutPos.y; builder.hutZ = hutPos.z
        builder.name = builderName(rawBuilding)
        snapshot.builders.add(builder)
        return builder
    }

    private fun builderName(rawBuilding: Any?): String {
        val citizens = invoke(rawBuilding, "getAllAssignedCitizen").orElse(null)
        if (citizens is Collection<*> && citizens.isNotEmpty()) {
            val name = ScanCoercion.stringOf(invoke(citizens.iterator().next(), "getName").orElse(null), "")
            if (name.isNotBlank()) return name
        }
        return "Builder"
    }

    private fun progressOf(workOrder: Any, builderBuilding: Any): Double {
        val total = ScanCoercion.intOf(invoke(workOrder, "getAmountOfResources").orElse(null), 0)
        val needed = invoke(builderBuilding, "getNeededResources").orElse(null)
        if (total <= 0 || needed !is Map<*, *>) return 0.0
        var remaining = 0
        for (resource in (needed as Map<Any, Any>).values) {
            remaining += ScanCoercion.intOf(invoke(resource, "getAmount").orElse(null), 0)
        }
        return maxOf(0.0, minOf(1.0, 1.0 - remaining.toDouble() / total.toDouble()))
    }

    private fun neededResources(builderBuilding: Any, warehouse: ColonySnapshot.Warehouse): List<ResourceEntry> {
        val resources = mutableListOf<ResourceEntry>()
        val needed = ScanCoercion.firstNonNull(
            invoke(builderBuilding, "getNeededResources").orElse(null),
            invoke(builderBuilding, "getRequiredResources").orElse(null))
        if (needed !is Map<*, *>) return resources
        for (raw in (needed as Map<Any, Any>).values) {
            try {
                val stack = ScanCoercion.itemStackOf(invoke(raw, "getItemStack").orElse(null))
                val amount = ScanCoercion.intOf(ScanCoercion.firstNonNull(
                    invoke(raw, "getAmount").orElse(null),
                    invoke(raw, "getNeededAmount").orElse(null)), 0)
                if (stack == null || stack.isEmpty || amount <= 0) continue
                val entry = ScanCoercion.fillItem(ResourceEntry(), stack)
                entry.needed = amount
                entry.maxStackSize = maxOf(1, stack.maxStackSize)
                entry.inHut = ScanCoercion.intOf(invoke(raw, "getAvailable").orElse(null), 0)
                entry.inWarehouse = warehouse.stacks.filter { it.itemKey == entry.itemKey }.sumOf { it.count }
                val shortfall = maxOf(0, amount - entry.inHut)
                entry.deliverable = shortfall > 0 && entry.inWarehouse >= shortfall
                resources.add(entry)
            } catch (t: Throwable) {
                LOGGER.debug("{} failed to read a needed resource", ColonyWeb.LOG, t)
            }
        }
        return resources
    }

    private fun actionOf(workOrder: Any, current: Int, target: Int): String {
        val type = invoke(workOrder, "getWorkOrderType").orElse(null)
        if (type != null) {
            val name = type.toString().uppercase()
            when {
                name.contains("BUILD") -> return if (current <= 0) "BUILD" else "UPGRADE"
                name.contains("UPGRADE") -> return "UPGRADE"
                name.contains("REPAIR") -> return "REPAIR"
                name.contains("REMOVE") -> return "REMOVE"
            }
        }
        return if (target > current && current > 0) "UPGRADE" else "BUILD"
    }

    private fun nameOf(workOrder: Any): String? {
        val path = Text.componentString(ScanCoercion.firstNonNull(
            invoke(workOrder, "getStructureName").orElse(null),
            invoke(workOrder, "getStructurePath").orElse(null)))
        if (path != null && path.isNotBlank()) {
            val humanized = Text.humanize(path)
            if (humanized.isNotBlank()) return humanized
        }
        val display = Text.componentString(ScanCoercion.firstNonNull(
            invoke(workOrder, "getCustomName").orElse(null),
            invoke(workOrder, "getDisplayName").orElse(null),
            invoke(workOrder, "getName").orElse(null)))
        if (display != null && display.isNotBlank()) {
            return if (display.contains(" ")) display else Text.humanize(display)
        }
        val key = Text.componentString(invoke(workOrder, "getTranslationKey").orElse(null))
        return if (key != null && key.isNotBlank()) Text.humanize(key) else "Decoration"
    }
}
