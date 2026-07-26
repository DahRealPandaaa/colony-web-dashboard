package DahRealPanda.plugins.colonyweb.colony.model;

/**
 * An item with a quantity — used for citizen inventories and research costs.
 */
public class ItemCount extends ItemInfo {
    public int count;
    public int slot = -1; // inventory slot, -1 when not slot-bound
}
