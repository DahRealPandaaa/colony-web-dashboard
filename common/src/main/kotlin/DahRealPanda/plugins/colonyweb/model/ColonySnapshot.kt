package DahRealPanda.plugins.colonyweb.model

/**
 * Full detail snapshot for a single colony.
 *
 * Citizens, research and combat data live in their own cached payloads (and their own
 * endpoints) so this document stays small enough to re-fetch on every live update.
 */
data class ColonySnapshot(
    @JvmField var id: Int = 0,
    @JvmField var name: String = "",
    @JvmField var dimension: String = "",
    @JvmField var owner: String = "",
    @JvmField var builders: MutableList<BuilderInfo> = ArrayList(),
    @JvmField var workOrders: MutableList<WorkOrderInfo> = ArrayList(),
    @JvmField var buildings: MutableList<BuildingInfo> = ArrayList(),
    @JvmField var warehouse: Warehouse = Warehouse(),
    @JvmField var stats: ColonyStats = ColonyStats()
) {
    /** Aggregated warehouse stock for the colony. */
    data class Warehouse(
        @JvmField var present: Boolean = false,
        @JvmField var stacks: MutableList<Stack> = ArrayList()
    )

    /** A single aggregated stack in the warehouse. */
    data class Stack(
        @JvmField var count: Int = 0,
        @JvmField var maxStackSize: Int = 0 // maximum items per stack for this specific item
    ) : ItemInfo()
}
