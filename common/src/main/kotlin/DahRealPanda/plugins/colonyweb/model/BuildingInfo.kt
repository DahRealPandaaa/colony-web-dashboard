package DahRealPanda.plugins.colonyweb.model

/**
 * A colony building and (when applicable) the resources required to build/upgrade it.
 */
data class BuildingInfo(
    @JvmField var id: Int = 0,
    @JvmField var name: String = "",
    @JvmField var type: String = "",
    @JvmField var kind: String = "building", // "building" or "decoration"
    @JvmField var blockId: String? = null,   // registry id of the MineColonies hut block (icon source)
    @JvmField var level: Int = 0,
    @JvmField var x: Int = 0,
    @JvmField var y: Int = 0,
    @JvmField var z: Int = 0,
    @JvmField var beingBuilt: Boolean = false,
    @JvmField var workOrderId: Int = -1,
    @JvmField var required: MutableList<ResourceEntry> = ArrayList()
)
