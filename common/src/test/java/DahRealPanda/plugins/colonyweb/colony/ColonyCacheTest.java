package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.CitizenInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySummary;
import DahRealPanda.plugins.colonyweb.colony.model.CombatInfo;
import DahRealPanda.plugins.colonyweb.colony.model.EquipmentInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ItemCount;
import DahRealPanda.plugins.colonyweb.colony.model.ResearchInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hand-off between the scan that runs on the server thread and the HTTP threads that serve
 * it.
 *
 * <p>Every read has to answer safely for a colony that was never scanned, or that has since been
 * abandoned, because a browser can ask for any id at any time.</p>
 */
class ColonyCacheTest {

    private static ColonySummary summary(int id) {
        ColonySummary summary = new ColonySummary();
        summary.id = id;
        return summary;
    }

    private static CitizenInfo citizen(int id) {
        CitizenInfo citizen = new CitizenInfo();
        citizen.id = id;
        return citizen;
    }

    @Nested
    @DisplayName("reading what was stored")
    class Reads {

        @Test
        @DisplayName("a stored snapshot comes back")
        void snapshotRoundTrip() {
            ColonyCache cache = new ColonyCache();
            ColonySnapshot snapshot = new ColonySnapshot();
            snapshot.id = 1;

            cache.putSnapshot(1, snapshot);

            assertSame(snapshot, cache.snapshot(1).orElseThrow());
        }

        @Test
        @DisplayName("a stored citizen can be found by id")
        void citizenById() {
            ColonyCache cache = new ColonyCache();
            cache.putCitizens(1, List.of(citizen(10), citizen(11)));

            assertEquals(11, cache.citizen(1, 11).orElseThrow().id);
        }

        @Test
        @DisplayName("summaries default to empty and can be replaced")
        void summaries() {
            ColonyCache cache = new ColonyCache();
            assertTrue(cache.summaries().isEmpty());

            cache.setSummaries(List.of(summary(1)));

            assertEquals(1, cache.summaries().size());
        }

        @Test
        @DisplayName("combat and research round-trip")
        void combatAndResearch() {
            ColonyCache cache = new ColonyCache();
            CombatInfo combat = new CombatInfo();
            ResearchInfo research = new ResearchInfo();

            cache.putCombat(1, combat);
            cache.putResearch(1, research);

            assertSame(combat, cache.combat(1).orElseThrow());
            assertSame(research, cache.research(1).orElseThrow());
        }

        @Test
        @DisplayName("inventories and equipment are keyed by colony and citizen")
        void perCitizenPayloads() {
            ColonyCache cache = new ColonyCache();
            ItemCount bread = new ItemCount();
            EquipmentInfo helmet = new EquipmentInfo();

            cache.putInventories(1, Map.of(10, List.of(bread)));
            cache.putEquipment(1, Map.of(10, List.of(helmet)));

            assertEquals(List.of(bread), cache.inventory(1, 10));
            assertEquals(List.of(helmet), cache.equipment(1, 10));
        }
    }

    @Nested
    @DisplayName("asking for something that is not there")
    class Misses {

        @Test
        @DisplayName("an unknown colony has no snapshot, combat or research")
        void unknownColony() {
            ColonyCache cache = new ColonyCache();

            assertTrue(cache.snapshot(99).isEmpty());
            assertTrue(cache.citizens(99).isEmpty());
            assertTrue(cache.combat(99).isEmpty());
            assertTrue(cache.research(99).isEmpty());
        }

        @Test
        @DisplayName("an unknown citizen in a known colony is absent")
        void unknownCitizen() {
            ColonyCache cache = new ColonyCache();
            cache.putCitizens(1, List.of(citizen(10)));

            assertTrue(cache.citizen(1, 999).isEmpty());
        }

        @Test
        @DisplayName("a citizen in a colony that was never scanned is absent")
        void citizenInUnknownColony() {
            assertTrue(new ColonyCache().citizen(99, 10).isEmpty());
        }

        @Test
        @DisplayName("inventory and equipment fall back to an empty list, never null")
        void emptyListsRatherThanNull() {
            ColonyCache cache = new ColonyCache();
            cache.putInventories(1, Map.of(10, List.of(new ItemCount())));

            assertEquals(List.of(), cache.inventory(99, 10), "unknown colony");
            assertEquals(List.of(), cache.inventory(1, 999), "unknown citizen");
            assertEquals(List.of(), cache.equipment(1, 10), "colony never had equipment stored");
        }
    }

    @Nested
    @DisplayName("forgetting colonies that no longer exist")
    class Eviction {

        @Test
        @DisplayName("everything cached for a deleted colony is dropped")
        void dropsDeletedColony() {
            ColonyCache cache = new ColonyCache();
            cache.putSnapshot(1, new ColonySnapshot());
            cache.putCitizens(1, List.of(citizen(10)));
            cache.putCombat(1, new CombatInfo());
            cache.putResearch(1, new ResearchInfo());
            cache.putInventories(1, Map.of(10, List.of(new ItemCount())));
            cache.putEquipment(1, Map.of(10, List.of(new EquipmentInfo())));

            cache.retainOnly(List.of());

            assertTrue(cache.snapshot(1).isEmpty());
            assertTrue(cache.citizens(1).isEmpty());
            assertTrue(cache.combat(1).isEmpty());
            assertTrue(cache.research(1).isEmpty());
            assertEquals(List.of(), cache.inventory(1, 10));
            assertEquals(List.of(), cache.equipment(1, 10));
        }

        @Test
        @DisplayName("a colony that still exists is kept")
        void keepsLiveColony() {
            ColonyCache cache = new ColonyCache();
            cache.putSnapshot(1, new ColonySnapshot());
            cache.putSnapshot(2, new ColonySnapshot());

            cache.retainOnly(List.of(summary(1)));

            assertTrue(cache.snapshot(1).isPresent());
            assertTrue(cache.snapshot(2).isEmpty());
        }

        @Test
        @DisplayName("retaining against an unchanged list is a no-op")
        void idempotent() {
            ColonyCache cache = new ColonyCache();
            cache.putSnapshot(1, new ColonySnapshot());

            cache.retainOnly(List.of(summary(1)));
            cache.retainOnly(List.of(summary(1)));

            assertTrue(cache.snapshot(1).isPresent());
        }

        @Test
        @DisplayName("retaining on an empty cache does not throw")
        void emptyCache() {
            new ColonyCache().retainOnly(List.of(summary(1)));
        }
    }
}
