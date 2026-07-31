package DahRealPanda.plugins.colonyweb.model

/**
 * An item with a quantity — used for citizen inventories and research costs.
 */
data class ItemCount(
    @JvmField var count: Int = 0,
    @JvmField var slot: Int = -1 // inventory slot, -1 when not slot-bound
) : ItemInfo()
