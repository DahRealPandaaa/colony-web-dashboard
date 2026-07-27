package DahRealPanda.plugins.colonyweb.colony.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Full detail snapshot for a single colony.
 *
 * <p>Citizens, research and combat data live in their own cached payloads (and their own
 * endpoints) so this document stays small enough to re-fetch on every live update.</p>
 */
public class ColonySnapshot {
    public int id;
    public String name;
    public String dimension;
    public String owner;

    public List<BuilderInfo> builders = new ArrayList<>();
    public List<WorkOrderInfo> workOrders = new ArrayList<>();
    public List<BuildingInfo> buildings = new ArrayList<>();
    public Warehouse warehouse = new Warehouse();
    public ColonyStats stats = new ColonyStats();

    /** Aggregated warehouse stock for the colony. */
    public static class Warehouse {
        public boolean present;
        public List<Stack> stacks = new ArrayList<>();
    }

    /** A single aggregated stack in the warehouse. */
    public static class Stack extends ItemInfo {
        public int count;
        public int maxStackSize; // maximum items per stack for this specific item

        public Stack() {
        }

        public Stack(String itemKey, String name, int count) {
            this.itemKey = itemKey;
            this.name = name;
            this.count = count;
        }
    }
}
