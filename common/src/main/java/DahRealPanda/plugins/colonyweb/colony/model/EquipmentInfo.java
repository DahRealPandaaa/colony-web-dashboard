package DahRealPanda.plugins.colonyweb.colony.model;

/**
 * One equipped item — a piece of armour or a held weapon.
 *
 * <p>{@link #armorPoints} is what decides whether one guard is better protected than another,
 * and is what the roster sorts on.</p>
 */
public class EquipmentInfo extends ItemInfo {
    public String slot;        // "Head", "Chest", "Legs", "Feet", "Main hand", "Off hand"
    public int armorPoints;    // vanilla armour value, 0 for weapons and tools
    public boolean enchanted;
    public int durabilityPct = 100; // 100 when undamaged or unbreakable
}
