package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.CitizenInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySummary;
import DahRealPanda.plugins.colonyweb.colony.model.CombatInfo;
import DahRealPanda.plugins.colonyweb.colony.model.EquipmentInfo;
import DahRealPanda.plugins.colonyweb.colony.model.ItemCount;
import DahRealPanda.plugins.colonyweb.colony.model.ResearchInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe holder for the latest colony scan results. Written on the server thread by the
 * refresh scheduler and read off-thread by HTTP handlers (as immutable DTOs).
 */
public final class ColonyCache {
    private volatile List<ColonySummary> summaries = new ArrayList<>();
    private final Map<Integer, ColonySnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<Integer, List<CitizenInfo>> citizens = new ConcurrentHashMap<>();
    private final Map<Integer, CombatInfo> combat = new ConcurrentHashMap<>();
    private final Map<Integer, ResearchInfo> research = new ConcurrentHashMap<>();
    /** colony id -> citizen id -> that citizen's carried items. */
    private final Map<Integer, Map<Integer, List<ItemCount>>> inventories = new ConcurrentHashMap<>();
    /** colony id -> citizen id -> what that citizen is wearing and holding. */
    private final Map<Integer, Map<Integer, List<EquipmentInfo>>> equipment = new ConcurrentHashMap<>();

    public List<ColonySummary> summaries() {
        return summaries;
    }

    public void setSummaries(List<ColonySummary> summaries) {
        this.summaries = summaries;
    }

    public Optional<ColonySnapshot> snapshot(int colonyId) {
        return Optional.ofNullable(snapshots.get(colonyId));
    }

    public void putSnapshot(int colonyId, ColonySnapshot snapshot) {
        snapshots.put(colonyId, snapshot);
    }

    public Optional<List<CitizenInfo>> citizens(int colonyId) {
        return Optional.ofNullable(citizens.get(colonyId));
    }

    public Optional<CitizenInfo> citizen(int colonyId, int citizenId) {
        return citizens(colonyId).flatMap(list ->
                list.stream().filter(c -> c.id == citizenId).findFirst());
    }

    public void putCitizens(int colonyId, List<CitizenInfo> list) {
        citizens.put(colonyId, list);
    }

    public Optional<CombatInfo> combat(int colonyId) {
        return Optional.ofNullable(combat.get(colonyId));
    }

    public void putCombat(int colonyId, CombatInfo info) {
        combat.put(colonyId, info);
    }

    public Optional<ResearchInfo> research(int colonyId) {
        return Optional.ofNullable(research.get(colonyId));
    }

    public void putResearch(int colonyId, ResearchInfo info) {
        research.put(colonyId, info);
    }

    public List<ItemCount> inventory(int colonyId, int citizenId) {
        Map<Integer, List<ItemCount>> byCitizen = inventories.get(colonyId);
        if (byCitizen == null) {
            return List.of();
        }
        List<ItemCount> items = byCitizen.get(citizenId);
        return items != null ? items : List.of();
    }

    public void putInventories(int colonyId, Map<Integer, List<ItemCount>> byCitizen) {
        inventories.put(colonyId, byCitizen);
    }

    public List<EquipmentInfo> equipment(int colonyId, int citizenId) {
        Map<Integer, List<EquipmentInfo>> byCitizen = equipment.get(colonyId);
        if (byCitizen == null) {
            return List.of();
        }
        List<EquipmentInfo> items = byCitizen.get(citizenId);
        return items != null ? items : List.of();
    }

    public void putEquipment(int colonyId, Map<Integer, List<EquipmentInfo>> byCitizen) {
        equipment.put(colonyId, byCitizen);
    }

    /** Drop everything cached for colonies that no longer exist. */
    public void retainOnly(List<ColonySummary> current) {
        List<Map<Integer, ?>> caches =
                List.of(snapshots, citizens, combat, research, inventories, equipment);
        for (Map<Integer, ?> cache : caches) {
            cache.keySet().removeIf(id -> current.stream().noneMatch(s -> s.id == id));
        }
    }
}
