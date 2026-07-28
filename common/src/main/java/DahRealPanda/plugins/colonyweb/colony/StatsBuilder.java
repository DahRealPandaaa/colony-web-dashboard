package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.BuildingInfo;
import DahRealPanda.plugins.colonyweb.colony.model.CitizenInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.colony.model.ColonyStats;
import DahRealPanda.plugins.colonyweb.colony.model.CombatInfo;

import java.util.List;

import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invokeAny;
import static DahRealPanda.plugins.colonyweb.colony.Scan.doubleOf;
import static DahRealPanda.plugins.colonyweb.colony.Scan.intOf;

/**
 * Rolls a finished scan up into the headline numbers on the overview tab.
 */
public final class StatsBuilder {

    /** Sentinel for "the colony did not expose an overall happiness value". */
    private static final double NO_HAPPINESS = -1;

    public void fill(Object colony, ColonySnapshot snapshot, List<CitizenInfo> citizens,
                     CombatInfo combat) {
        ColonyStats stats = snapshot.stats;

        fillCitizens(colony, stats, citizens);
        fillBuildings(stats, snapshot);
        fillWarehouse(stats, snapshot);

        stats.guards = combat.guardCount;
        stats.raided = combat.underAttack;
        stats.nightsSinceRaid = combat.nightsSinceRaid;
    }

    private void fillCitizens(Object colony, ColonyStats stats, List<CitizenInfo> citizens) {
        stats.citizens = citizens.size();
        Object citizenManager = invokeAny(colony, "getCitizenManager").orElse(null);
        stats.maxCitizens = intOf(invokeAny(citizenManager, "getMaxCitizens").orElse(null), 0);

        double happinessSum = 0;
        double saturationSum = 0;
        for (CitizenInfo citizen : citizens) {
            happinessSum += citizen.happiness;
            saturationSum += citizen.saturation;
            if (citizen.child) {
                stats.children++;
            } else if (citizen.jobType == null) {
                stats.unemployed++;
            }
        }
        if (!citizens.isEmpty()) {
            stats.happiness = happinessSum / citizens.size();
            stats.saturation = saturationSum / citizens.size();
        }
        // The colony tracks its own overall happiness; prefer it over our average.
        double overall = doubleOf(invokeAny(colony, "getOverallHappiness").orElse(null), NO_HAPPINESS);
        if (overall >= 0) {
            stats.happiness = overall;
        }
    }

    private void fillBuildings(ColonyStats stats, ColonySnapshot snapshot) {
        for (BuildingInfo building : snapshot.buildings) {
            if ("decoration".equals(building.kind)) {
                stats.decorations++;
            } else {
                stats.buildings++;
            }
        }
        stats.workOrders = snapshot.workOrders.size();
        // Counted from the scanned buildings, whose types are already resolved against the hut
        // block when the type registry lookup comes back empty.
        stats.builders = BuildingScanner.countBuilders(snapshot.buildings);
    }

    private void fillWarehouse(ColonyStats stats, ColonySnapshot snapshot) {
        stats.warehouseTypes = snapshot.warehouse.stacks.size();
        for (ColonySnapshot.Stack stack : snapshot.warehouse.stacks) {
            stats.warehouseItems += stack.count;
        }
    }
}
