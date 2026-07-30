package DahRealPanda.plugins.colonyweb.model

/**
 * A colony citizen with the stats, skills and happiness modifiers the game tracks for them.
 *
 * Inventories are deliberately not part of this DTO — they are cached separately and
 * served by `/api/colony/{id}/citizen/{citizenId}` so the citizen list stays light.
 */
data class CitizenInfo(
    @JvmField var id: Int = 0,
    @JvmField var name: String = "",
    @JvmField var job: String = "",          // readable job name, "Unemployed" when idle
    @JvmField var jobType: String? = null,   // job registry id, null when unemployed
    @JvmField var jobIcon: String? = null,   // texture key for the job's hut block (icon source)
    @JvmField var child: Boolean = false,
    @JvmField var female: Boolean = false,
    @JvmField var health: Double = 0.0,
    @JvmField var maxHealth: Double = 0.0,
    @JvmField var saturation: Double = 0.0, // 0-20
    @JvmField var happiness: Double = 0.0,  // 0-10
    @JvmField var spawned: Boolean = false,  // the entity is currently loaded in the world
    @JvmField var x: Int = 0,
    @JvmField var y: Int = 0,
    @JvmField var z: Int = 0,
    @JvmField var workBuilding: String = "",
    @JvmField var workBuildingId: Int = -1,
    @JvmField var homeBuilding: String = "",
    @JvmField var homeBuildingId: Int = -1,
    @JvmField var status: String? = null,   // current activity, when MineColonies exposes one
    @JvmField var primarySkill: String = "",
    @JvmField var secondarySkill: String = "",
    @JvmField var skillTotal: Int = 0,
    @JvmField var inventoryUsed: Int = 0,
    @JvmField var inventorySize: Int = 0,
    @JvmField var skills: MutableList<Skill> = ArrayList(),
    @JvmField var modifiers: MutableList<Modifier> = ArrayList()
) {
    /** One of the eleven MineColonies skills. */
    data class Skill(
        @JvmField var name: String = "",
        @JvmField var level: Int = 0,
        @JvmField var xp: Double = 0.0,
        @JvmField var role: String? = null // "primary" / "secondary" / null
    )

    /** A happiness modifier — what the game calls the citizen's perks and grievances. */
    data class Modifier(
        @JvmField var name: String = "",
        @JvmField var factor: Double = 0.0 // >1 positive, <1 negative
    )
}
