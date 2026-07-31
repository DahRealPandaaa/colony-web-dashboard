package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invoke
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.platform.Platform
import DahRealPanda.plugins.colonyweb.service.DomumOrnamentumResolver
import DahRealPanda.plugins.colonyweb.util.ScanCoercion
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import java.util.LinkedHashMap

class WarehouseService {
    private val countedContainers = hashSetOf<BlockPos>()
    private val byKey = LinkedHashMap<String, ColonySnapshot.Stack>()

    fun scan(rawBuildings: Collection<Any>, buildingResult: BuildingResult, level: ServerLevel?,
             warehouse: ColonySnapshot.Warehouse) {
        for ((pos, building) in buildingResult.buildingByPos) {
            if (!BuildingService().isWarehouse(building)) continue
            warehouse.present = true
            val raw = buildingResult.rawBuildingByPos[pos] ?: continue
            addWarehouse(level, raw, pos, warehouse)
        }
    }

    private fun addWarehouse(level: ServerLevel?, building: Any, hutPos: BlockPos,
                             warehouse: ColonySnapshot.Warehouse) {
        for (handler in rackInventories(level, building, hutPos)) {
            for (slot in 0 until handler.getSlots()) {
                val stack = handler.getStackInSlot(slot)
                if (stack != null && !stack.isEmpty) add(warehouse, stack)
            }
        }
    }

    private fun add(warehouse: ColonySnapshot.Warehouse, stack: ItemStack) {
        val key = DomumOrnamentumResolver.textureKeyFor(stack)
        var aggregate = byKey[key]
        if (aggregate == null) {
            aggregate = ScanCoercion.fillItem(ColonySnapshot.Stack(), stack)
            aggregate.maxStackSize = maxOf(1, stack.maxStackSize)
            byKey[key] = aggregate
            warehouse.stacks.add(aggregate)
        }
        aggregate.count += stack.count
    }

    private fun rackInventories(level: ServerLevel?, building: Any, hutPos: BlockPos): List<DahRealPanda.plugins.colonyweb.platform.ItemSlots> {
        val handlers = mutableListOf<DahRealPanda.plugins.colonyweb.platform.ItemSlots>()
        if (level == null) return handlers
        val platform = Platform.get()
        for (pos in containerPositions(building, hutPos)) {
            if (!countedContainers.add(pos)) continue
            val blockEntity = level.getBlockEntity(pos)
            if (blockEntity == null || !blockEntity.javaClass.simpleName.lowercase().contains("rack")) continue
            val ownInventory = invoke(blockEntity, "getInventory").orElse(null)
            val slots = platform.asItemSlots(ownInventory) ?: platform.itemSlots(blockEntity)
            if (slots != null) handlers.add(slots)
        }
        return handlers
    }

    private fun containerPositions(building: Any, hutPos: BlockPos): Set<BlockPos> {
        val positions = linkedSetOf<BlockPos>()
        val containers = invoke(building, "getContainers").orElse(null)
        if (containers is Collection<*>) {
            for (entry in containers) {
                val pos = ScanCoercion.blockPosOf(entry)
                if (pos != null) positions.add(pos.immutable())
            }
        }
        if (positions.isEmpty()) positions.add(hutPos.immutable())
        return positions
    }

    companion object {
        fun countIn(snapshot: ColonySnapshot, itemKey: String): Int {
            return snapshot.warehouse.stacks.filter { it.itemKey == itemKey }.sumOf { it.count }
        }
    }
}
