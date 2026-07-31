package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.ColonyScan
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot

object ScanHasher {
    private const val POSITION_BUCKET_SHIFT = 3

    @JvmStatic
    fun hash(scan: ColonyScan): Int {
        val snapshot = scan.snapshot
        var hash = 7
        hash = hashBuildings(hash, snapshot)
        hash = hashWorkOrders(hash, snapshot)
        hash = hashWarehouse(hash, snapshot)
        hash = hashCitizens(hash, scan)
        hash = hashCombat(hash, scan)

        hash = hash * 31 + snapshot.stats.researchCompleted
        hash = hash * 31 + snapshot.stats.researchInProgress
        return hash
    }

    private fun hashBuildings(hash: Int, snapshot: ColonySnapshot): Int {
        var h = hash
        for (building in snapshot.buildings) {
            h = h * 31 + building.id
            h = h * 31 + building.level
            h = h * 31 + (if (building.beingBuilt) 1 else 0)
            h = h * 31 + building.workOrderId
            for (resource in building.required) {
                h = h * 31 + (resource.itemKey?.hashCode() ?: 0)
                h = h * 31 + resource.needed
                h = h * 31 + resource.inHut
                h = h * 31 + resource.inWarehouse
            }
        }
        return h
    }

    private fun hashWorkOrders(hash: Int, snapshot: ColonySnapshot): Int {
        var h = hash
        for (workOrder in snapshot.workOrders) {
            h = h * 31 + workOrder.id
            h = h * 31 + workOrder.currentLevel
            h = h * 31 + workOrder.targetLevel
            h = h * 31 + (workOrder.progress * 100).toInt()
        }
        return h
    }

    private fun hashWarehouse(hash: Int, snapshot: ColonySnapshot): Int {
        var h = hash
        val warehouse = snapshot.warehouse ?: return h
        for (stack in warehouse.stacks) {
            h = h * 31 + (stack.itemKey?.hashCode() ?: 0)
            h = h * 31 + stack.count
        }
        return h
    }

    private fun hashCitizens(hash: Int, scan: ColonyScan): Int {
        var h = hash
        for (citizen in scan.citizens) {
            h = h * 31 + citizen.id
            h = h * 31 + (citizen.jobType?.hashCode() ?: 0)
            h = h * 31 + citizen.skillTotal
            h = h * 31 + citizen.inventoryUsed
            h = h * 31 + (citizen.health.toInt())
            h = h * 31 + (citizen.happiness * 10).toInt()
            h = h * 31 + (citizen.x shr POSITION_BUCKET_SHIFT)
            h = h * 31 + (citizen.z shr POSITION_BUCKET_SHIFT)
        }
        return h
    }

    private fun hashCombat(hash: Int, scan: ColonyScan): Int {
        var h = hash
        h = h * 31 + scan.combat.guardCount
        h = h * 31 + scan.combat.nightsSinceRaid
        h = h * 31 + (if (scan.combat.underAttack) 1 else 0)
        h = h * 31 + scan.combat.events.size
        return h
    }
}
