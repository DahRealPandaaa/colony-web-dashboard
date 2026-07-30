package DahRealPanda.plugins.colonyweb.model

/**
 * A builder and the work order they are currently assigned to.
 */
data class BuilderInfo(
    @JvmField var id: Int = 0,
    @JvmField var name: String = "",
    @JvmField var hutX: Int = 0,
    @JvmField var hutY: Int = 0,
    @JvmField var hutZ: Int = 0,
    @JvmField var assignedWorkOrderId: Int = -1
)
