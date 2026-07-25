package DahRealPanda.plugins.untitled1.colony.model;

/**
 * A single required resource line for a building/upgrade (resource-scroll parity).
 */
public class ResourceEntry {
    public String itemKey;      // texture key: namespace:path (+ optional #hash)
    public String name;         // display name
    public int needed;          // total amount needed
    public int inHut;           // amount currently in the building hut inventory
    public int inWarehouse;     // amount available across colony warehouse(s)
    public boolean deliverable; // true when warehouse can cover the shortfall

    public ResourceEntry() {
    }

    public ResourceEntry(String itemKey, String name, int needed, int inHut, int inWarehouse, boolean deliverable) {
        this.itemKey = itemKey;
        this.name = name;
        this.needed = needed;
        this.inHut = inHut;
        this.inWarehouse = inWarehouse;
        this.deliverable = deliverable;
    }
}
