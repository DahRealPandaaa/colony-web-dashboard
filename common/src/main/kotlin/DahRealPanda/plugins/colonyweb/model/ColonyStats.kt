package DahRealPanda.plugins.colonyweb.model

/**
 * Headline numbers for a colony, shown on the overview tab.
 */
data class ColonyStats(
    @JvmField var citizens: Int = 0,
    @JvmField var maxCitizens: Int = 0,
    @JvmField var children: Int = 0,
    @JvmField var unemployed: Int = 0,
    @JvmField var happiness: Double = 0.0,      // colony-wide average, 0-10
    @JvmField var saturation: Double = 0.0,     // average citizen saturation, 0-20
    @JvmField var buildings: Int = 0,
    @JvmField var decorations: Int = 0,
    @JvmField var workOrders: Int = 0,
    @JvmField var builders: Int = 0,
    @JvmField var guards: Int = 0,
    @JvmField var warehouseTypes: Int = 0,      // distinct stacks in the warehouse
    @JvmField var warehouseItems: Int = 0,      // total item count in the warehouse
    @JvmField var researchCompleted: Int = 0,
    @JvmField var researchInProgress: Int = 0,
    @JvmField var raided: Boolean = false,
    @JvmField var nightsSinceRaid: Int = 0
)
