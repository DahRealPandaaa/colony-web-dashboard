package DahRealPanda.plugins.colonyweb.model

/**
 * A single required resource line for a building/upgrade (resource-scroll parity).
 */
data class ResourceEntry(
    @JvmField var needed: Int = 0,          // total amount needed
    @JvmField var maxStackSize: Int = 0,    // maximum items per stack for this specific item
    @JvmField var inHut: Int = 0,           // amount currently in the building hut inventory
    @JvmField var inWarehouse: Int = 0,     // amount available across colony warehouse(s)
    @JvmField var deliverable: Boolean = false // true when warehouse can cover the shortfall
) : ItemInfo()
