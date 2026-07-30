package DahRealPanda.plugins.colonyweb.model


data class ColonySummary(
    val id: Int,
    val name: String,
    val dimension: String,
    val owner: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val buildingCount: Int,
    val builderCount: Int,
    val activeWorkOrders: Int
)
