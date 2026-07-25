package DahRealPanda.plugins.untitled1.colony;

import DahRealPanda.plugins.untitled1.colony.model.ColonySnapshot;
import DahRealPanda.plugins.untitled1.colony.model.ColonySummary;

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

    public void retainOnly(List<ColonySummary> current) {
        snapshots.keySet().removeIf(id -> current.stream().noneMatch(s -> s.id == id));
    }
}
