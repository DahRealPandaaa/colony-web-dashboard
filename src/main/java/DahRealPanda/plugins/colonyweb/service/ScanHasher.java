package DahRealPanda.plugins.colonyweb.service;

import DahRealPanda.plugins.colonyweb.colony.ColonyScan;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;

/**
 * Reduces a scan to a single int so the scheduler can tell whether anything a viewer would
 * notice actually changed.
 *
 * <p>Values that jitter every tick are deliberately bucketed — progress to whole percent,
 * citizen positions to 8-block cells — otherwise an idle colony would emit a continuous
 * stream of SSE updates and every browser would re-fetch three times a second.</p>
 */
public final class ScanHasher {

    /** Citizens wander constantly; only movement across this many blocks counts as a change. */
    private static final int POSITION_BUCKET_SHIFT = 3;

    private ScanHasher() {
    }

    public static int hash(ColonyScan scan) {
        ColonySnapshot snapshot = scan.snapshot;
        int hash = 7;
        hash = hashBuildings(hash, snapshot);
        hash = hashWorkOrders(hash, snapshot);
        hash = hashWarehouse(hash, snapshot);
        hash = hashCitizens(hash, scan);
        hash = hashCombat(hash, scan);

        hash = hash * 31 + snapshot.stats.researchCompleted;
        hash = hash * 31 + snapshot.stats.researchInProgress;
        return hash;
    }

    private static int hashBuildings(int hash, ColonySnapshot snapshot) {
        for (var building : snapshot.buildings) {
            hash = hash * 31 + building.id;
            hash = hash * 31 + building.level;
            hash = hash * 31 + (building.beingBuilt ? 1 : 0);
            hash = hash * 31 + building.workOrderId;
            for (var resource : building.required) {
                hash = hash * 31 + (resource.itemKey == null ? 0 : resource.itemKey.hashCode());
                hash = hash * 31 + resource.needed;
                hash = hash * 31 + resource.inHut;
                hash = hash * 31 + resource.inWarehouse;
            }
        }
        return hash;
    }

    private static int hashWorkOrders(int hash, ColonySnapshot snapshot) {
        for (var workOrder : snapshot.workOrders) {
            hash = hash * 31 + workOrder.id;
            hash = hash * 31 + workOrder.currentLevel;
            hash = hash * 31 + workOrder.targetLevel;
            hash = hash * 31 + (int) Math.round(workOrder.progress * 100);
        }
        return hash;
    }

    private static int hashWarehouse(int hash, ColonySnapshot snapshot) {
        if (snapshot.warehouse == null) {
            return hash;
        }
        for (var stack : snapshot.warehouse.stacks) {
            hash = hash * 31 + (stack.itemKey == null ? 0 : stack.itemKey.hashCode());
            hash = hash * 31 + stack.count;
        }
        return hash;
    }

    private static int hashCitizens(int hash, ColonyScan scan) {
        for (var citizen : scan.citizens) {
            hash = hash * 31 + citizen.id;
            hash = hash * 31 + (citizen.jobType == null ? 0 : citizen.jobType.hashCode());
            hash = hash * 31 + citizen.skillTotal;
            hash = hash * 31 + citizen.inventoryUsed;
            hash = hash * 31 + (int) Math.round(citizen.health);
            hash = hash * 31 + (int) Math.round(citizen.happiness * 10);
            hash = hash * 31 + (citizen.x >> POSITION_BUCKET_SHIFT);
            hash = hash * 31 + (citizen.z >> POSITION_BUCKET_SHIFT);
        }
        return hash;
    }

    private static int hashCombat(int hash, ColonyScan scan) {
        hash = hash * 31 + scan.combat.guardCount;
        hash = hash * 31 + scan.combat.nightsSinceRaid;
        hash = hash * 31 + (scan.combat.underAttack ? 1 : 0);
        hash = hash * 31 + scan.combat.events.size();
        return hash;
    }
}
