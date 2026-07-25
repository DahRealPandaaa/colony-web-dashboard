package DahRealPanda.plugins.untitled1.colony.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Full detail snapshot for a single colony.
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

    /** Aggregated warehouse stock for the colony. */
    public static class Warehouse {
        public boolean present;
        public List<Stack> stacks = new ArrayList<>();
    }

    /** A single aggregated stack in the warehouse. */
    public static class Stack {
        public String itemKey;
        public String name;
        public String material; // DO material name, null when not DO
        public int count;

        public Stack() {
        }

        public Stack(String itemKey, String name, int count) {
            this.itemKey = itemKey;
            this.name = name;
            this.count = count;
        }
    }
}
