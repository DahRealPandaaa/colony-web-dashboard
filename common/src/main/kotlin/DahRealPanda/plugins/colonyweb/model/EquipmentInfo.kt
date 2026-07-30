package DahRealPanda.plugins.colonyweb.model

/**
 * One equipped item — a piece of armour or a held weapon.
 *
 * [armorPoints] is what decides whether one guard is better protected than another,
 * and is what the roster sorts on.
 */
data class EquipmentInfo(
    @JvmField var slot: String = "",        // "Head", "Chest", "Legs", "Feet", "Main hand", "Off hand"
    @JvmField var armorPoints: Int = 0,     // vanilla armour value, 0 for weapons and tools
    @JvmField var enchanted: Boolean = false,
    @JvmField var durabilityPct: Int = 100  // 100 when undamaged or unbreakable
) : ItemInfo()
