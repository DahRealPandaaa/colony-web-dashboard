package DahRealPanda.plugins.colonyweb.model

/**
 * The colony's university research: every branch, what is finished, what is running.
 */
data class ResearchInfo(
    @JvmField var branches: MutableList<Branch> = ArrayList(),
    @JvmField var completed: Int = 0,
    @JvmField var inProgress: Int = 0,
    @JvmField var total: Int = 0,
    @JvmField var available: Boolean = false // false when MineColonies exposes no research tree
) {
    /** One research branch (e.g. Technology, Civilian, Combat). */
    data class Branch(
        @JvmField var id: String = "",
        @JvmField var name: String = "",
        @JvmField var completed: Int = 0,
        @JvmField var inProgress: Int = 0,
        @JvmField var total: Int = 0,
        @JvmField var researches: MutableList<Entry> = ArrayList()
    )

    /** A single research node and its state in this colony. */
    data class Entry(
        @JvmField var id: String = "",
        @JvmField var name: String = "",
        @JvmField var branch: String = "",
        @JvmField var depth: Int = 0,
        @JvmField var state: String = "",       // COMPLETED / IN_PROGRESS / NOT_STARTED
        @JvmField var progress: Int = 0,
        @JvmField var maxProgress: Int = 0,
        @JvmField var effects: MutableList<String> = ArrayList(),
        @JvmField var requirements: MutableList<String> = ArrayList(),
        @JvmField var cost: MutableList<ItemCount> = ArrayList()
    )
}
