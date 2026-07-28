package DahRealPanda.plugins.colonyweb.colony.model;

/**
 * A single required resource line for a building/upgrade (resource-scroll parity).
 */
public class ResourceEntry extends ItemInfo {
    public int needed;          // total amount needed
    public int maxStackSize;    // maximum items per stack for this specific item
    public int inHut;           // amount currently in the building hut inventory
    public int inWarehouse;     // amount available across colony warehouse(s)
    public boolean deliverable; // true when warehouse can cover the shortfall
}
