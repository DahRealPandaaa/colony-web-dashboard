package DahRealPanda.plugins.colonyweb.service;

import DahRealPanda.plugins.colonyweb.colony.ColonyScan;
import DahRealPanda.plugins.colonyweb.colony.model.BuildingInfo;
import DahRealPanda.plugins.colonyweb.colony.model.CitizenInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.colony.model.ResourceEntry;
import DahRealPanda.plugins.colonyweb.colony.model.WorkOrderInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The change detector behind the live update stream.
 *
 * <p>Two properties matter and pull against each other. Anything a viewer would notice has to
 * change the hash, or the dashboard silently goes stale; anything that merely jitters must not,
 * or an idle colony pushes an update several times a second and every browser re-fetches.</p>
 */
class ScanHasherTest {

    private static ColonyScan scan() {
        ColonyScan scan = new ColonyScan();
        scan.snapshot = new ColonySnapshot();
        return scan;
    }

    private static ColonyScan scanWithBuilding() {
        ColonyScan scan = scan();
        BuildingInfo building = new BuildingInfo();
        building.id = 1;
        building.level = 3;
        scan.snapshot.buildings.add(building);
        return scan;
    }

    private static ColonyScan scanWithCitizen(int x, int z) {
        ColonyScan scan = scan();
        CitizenInfo citizen = new CitizenInfo();
        citizen.id = 42;
        citizen.x = x;
        citizen.z = z;
        scan.citizens = List.of(citizen);
        return scan;
    }

    @Nested
    @DisplayName("stability")
    class Stability {

        @Test
        @DisplayName("an empty scan hashes consistently")
        void emptyIsStable() {
            assertEquals(ScanHasher.hash(scan()), ScanHasher.hash(scan()));
        }

        @Test
        @DisplayName("two identical scans hash the same")
        void identicalScansMatch() {
            assertEquals(ScanHasher.hash(scanWithBuilding()), ScanHasher.hash(scanWithBuilding()));
        }

        @Test
        @DisplayName("hashing does not mutate the scan")
        void isPure() {
            ColonyScan scan = scanWithBuilding();
            int first = ScanHasher.hash(scan);

            assertEquals(first, ScanHasher.hash(scan));
        }
    }

    @Nested
    @DisplayName("changes a viewer would notice")
    class NoticeableChanges {

        @Test
        @DisplayName("a building levelling up changes the hash")
        void buildingLevel() {
            ColonyScan before = scanWithBuilding();
            ColonyScan after = scanWithBuilding();
            after.snapshot.buildings.get(0).level = 4;

            assertNotEquals(ScanHasher.hash(before), ScanHasher.hash(after));
        }

        @Test
        @DisplayName("a building entering the build queue changes the hash")
        void beingBuilt() {
            ColonyScan before = scanWithBuilding();
            ColonyScan after = scanWithBuilding();
            after.snapshot.buildings.get(0).beingBuilt = true;

            assertNotEquals(ScanHasher.hash(before), ScanHasher.hash(after));
        }

        @Test
        @DisplayName("a required resource being delivered changes the hash")
        void resourceCounts() {
            ColonyScan before = scanWithBuilding();
            ColonyScan after = scanWithBuilding();
            for (ColonyScan scan : List.of(before, after)) {
                ResourceEntry entry = new ResourceEntry();
                entry.itemKey = "minecraft:oak_planks";
                entry.needed = 64;
                scan.snapshot.buildings.get(0).required.add(entry);
            }
            after.snapshot.buildings.get(0).required.get(0).inHut = 32;

            assertNotEquals(ScanHasher.hash(before), ScanHasher.hash(after));
        }

        @Test
        @DisplayName("a new warehouse stack changes the hash")
        void warehouseStock() {
            ColonyScan before = scan();
            ColonyScan after = scan();
            after.snapshot.warehouse.stacks.add(new ColonySnapshot.Stack("minecraft:stone", "Stone", 64));

            assertNotEquals(ScanHasher.hash(before), ScanHasher.hash(after));
        }

        @Test
        @DisplayName("a warehouse count changing changes the hash")
        void warehouseCount() {
            ColonyScan before = scan();
            ColonyScan after = scan();
            before.snapshot.warehouse.stacks.add(new ColonySnapshot.Stack("minecraft:stone", "Stone", 64));
            after.snapshot.warehouse.stacks.add(new ColonySnapshot.Stack("minecraft:stone", "Stone", 65));

            assertNotEquals(ScanHasher.hash(before), ScanHasher.hash(after));
        }

        @Test
        @DisplayName("a raid starting changes the hash")
        void raidStarting() {
            ColonyScan before = scan();
            ColonyScan after = scan();
            after.combat.underAttack = true;

            assertNotEquals(ScanHasher.hash(before), ScanHasher.hash(after));
        }

        @Test
        @DisplayName("finishing a research changes the hash")
        void research() {
            ColonyScan before = scan();
            ColonyScan after = scan();
            after.snapshot.stats.researchCompleted = 1;

            assertNotEquals(ScanHasher.hash(before), ScanHasher.hash(after));
        }

        @Test
        @DisplayName("a citizen changing job changes the hash")
        void citizenJob() {
            ColonyScan before = scanWithCitizen(0, 0);
            ColonyScan after = scanWithCitizen(0, 0);
            after.citizens.get(0).jobType = "minecolonies:builder";

            assertNotEquals(ScanHasher.hash(before), ScanHasher.hash(after));
        }
    }

    @Nested
    @DisplayName("jitter that must be ignored")
    class IgnoredJitter {

        @Test
        @DisplayName("a citizen shuffling inside one 8-block cell does not change the hash")
        void positionWithinBucket() {
            assertEquals(ScanHasher.hash(scanWithCitizen(0, 0)), ScanHasher.hash(scanWithCitizen(7, 7)));
        }

        @Test
        @DisplayName("a citizen crossing into the next cell does change the hash")
        void positionAcrossBucket() {
            assertNotEquals(ScanHasher.hash(scanWithCitizen(0, 0)), ScanHasher.hash(scanWithCitizen(8, 0)));
        }

        @Test
        @DisplayName("bucketing works either side of the origin")
        void negativePositions() {
            // Arithmetic shift floors, so -1 and -8 share a bucket while -9 does not.
            assertEquals(ScanHasher.hash(scanWithCitizen(-1, 0)), ScanHasher.hash(scanWithCitizen(-8, 0)));
            assertNotEquals(ScanHasher.hash(scanWithCitizen(-8, 0)), ScanHasher.hash(scanWithCitizen(-9, 0)));
        }

        @Test
        @DisplayName("build progress moving less than a percent does not change the hash")
        void progressWithinPercent() {
            ColonyScan before = scan();
            ColonyScan after = scan();
            WorkOrderInfo first = new WorkOrderInfo();
            first.id = 1;
            first.progress = 0.5000;
            WorkOrderInfo second = new WorkOrderInfo();
            second.id = 1;
            second.progress = 0.5004;
            before.snapshot.workOrders.add(first);
            after.snapshot.workOrders.add(second);

            assertEquals(ScanHasher.hash(before), ScanHasher.hash(after));
        }

        @Test
        @DisplayName("build progress crossing a whole percent does change the hash")
        void progressAcrossPercent() {
            ColonyScan before = scan();
            ColonyScan after = scan();
            WorkOrderInfo first = new WorkOrderInfo();
            first.id = 1;
            first.progress = 0.50;
            WorkOrderInfo second = new WorkOrderInfo();
            second.id = 1;
            second.progress = 0.51;
            before.snapshot.workOrders.add(first);
            after.snapshot.workOrders.add(second);

            assertNotEquals(ScanHasher.hash(before), ScanHasher.hash(after));
        }
    }

    @Nested
    @DisplayName("missing data")
    class MissingData {

        @Test
        @DisplayName("a snapshot with no warehouse hashes rather than throwing")
        void nullWarehouse() {
            ColonyScan scan = scan();
            scan.snapshot.warehouse = null;

            assertEquals(ScanHasher.hash(scan), ScanHasher.hash(scan));
        }

        @Test
        @DisplayName("null item keys and job types are tolerated")
        void nullStrings() {
            ColonyScan scan = scanWithBuilding();
            scan.snapshot.buildings.get(0).required.add(new ResourceEntry());
            scan.snapshot.warehouse.stacks.add(new ColonySnapshot.Stack());
            CitizenInfo citizen = new CitizenInfo();
            scan.citizens = List.of(citizen);

            assertEquals(ScanHasher.hash(scan), ScanHasher.hash(scan));
        }
    }
}
