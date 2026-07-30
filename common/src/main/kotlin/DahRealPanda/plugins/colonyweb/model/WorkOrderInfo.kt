package DahRealPanda.plugins.colonyweb.model

/**
 * A work order describing what is being built/upgraded and by whom.
 */
data class WorkOrderInfo(
    @JvmField var id: Int = 0,
    @JvmField var buildingName: String = "",
    @JvmField var buildingType: String = "",
    @JvmField var x: Int = 0,
    @JvmField var y: Int = 0,
    @JvmField var z: Int = 0,
    @JvmField var currentLevel: Int = 0,
    @JvmField var targetLevel: Int = 0,
    @JvmField var action: String = "", // BUILD / UPGRADE / REPAIR / REMOVE
    @JvmField var builderId: Int = -1,
    @JvmField var builderName: String = "",
    @JvmField var progress: Double = 0.0 // 0.0 - 1.0
)
