package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeAny
import DahRealPanda.plugins.colonyweb.model.CitizenInfo
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.ColonyStats
import DahRealPanda.plugins.colonyweb.model.CombatInfo
import DahRealPanda.plugins.colonyweb.util.ScanCoercion

class StatsService {
    companion object {
        private const val NO_HAPPINESS = -1.0
    }

    fun fill(colony: Any, snapshot: ColonySnapshot, citizens: List<CitizenInfo>,
             combat: CombatInfo, builderCount: Int) {
        val stats = snapshot.stats
        fillCitizens(colony, stats, citizens)
        fillBuildings(stats, snapshot, builderCount)
        fillWarehouse(stats, snapshot)
        stats.guards = combat.guardCount
        stats.raided = combat.underAttack
        stats.nightsSinceRaid = combat.nightsSinceRaid
    }

    private fun fillCitizens(colony: Any, stats: ColonyStats, citizens: List<CitizenInfo>) {
        stats.citizens = citizens.size
        val citizenManager = invokeAny(colony, "getCitizenManager").orElse(null)
        stats.maxCitizens = ScanCoercion.intOf(invokeAny(citizenManager, "getMaxCitizens").orElse(null), 0)
        var happinessSum = 0.0
        var saturationSum = 0.0
        for (citizen in citizens) {
            happinessSum += citizen.happiness
            saturationSum += citizen.saturation
            if (citizen.child) stats.children++
            else if (citizen.jobType == null) stats.unemployed++
        }
        if (citizens.isNotEmpty()) {
            stats.happiness = happinessSum / citizens.size
            stats.saturation = saturationSum / citizens.size
        }
        val overall = ScanCoercion.doubleOf(invokeAny(colony, "getOverallHappiness").orElse(null), NO_HAPPINESS)
        if (overall >= 0) stats.happiness = overall
    }

    private fun fillBuildings(stats: ColonyStats, snapshot: ColonySnapshot, builderCount: Int) {
        for (building in snapshot.buildings) {
            if ("decoration" == building.kind) stats.decorations++
            else stats.buildings++
        }
        stats.workOrders = snapshot.workOrders.size
        stats.builders = builderCount
    }

    private fun fillWarehouse(stats: ColonyStats, snapshot: ColonySnapshot) {
        stats.warehouseTypes = snapshot.warehouse.stacks.size
        for (stack in snapshot.warehouse.stacks) {
            stats.warehouseItems += stack.count
        }
    }
}
