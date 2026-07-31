package DahRealPanda.plugins.colonyweb.model

/**
 * Colony defence: raid pressure, the guard roster and the buildings backing them.
 */
data class CombatInfo(
    @JvmField var raidsPossible: Boolean = false,
    @JvmField var underAttack: Boolean = false,
    @JvmField var nightsSinceRaid: Int = 0,
    @JvmField var raidLevel: Int = 0,        // MineColonies' colony raid level (raid difficulty scaling)
    @JvmField var spiesEnabled: Boolean = false,
    @JvmField var guardCount: Int = 0,
    @JvmField var guardCapacity: Int = 0,    // total guard slots across guard buildings
    @JvmField var averageGuardLevel: Double = 0.0,
    @JvmField var averageHealthPct: Double = 0.0,
    @JvmField var graves: Int = 0,           // unclaimed graves — citizens that died and need burying
    @JvmField var guards: MutableList<Guard> = ArrayList(),
    @JvmField var posts: MutableList<Post> = ArrayList(),
    @JvmField var events: MutableList<Event> = ArrayList()
) {
    /** A citizen with a combat job, and the kit they are carrying. */
    data class Guard(
        @JvmField var id: Int = 0,
        @JvmField var name: String = "",
        @JvmField var job: String = "",
        @JvmField var jobType: String = "",
        @JvmField var level: Int = 0,        // job-relevant skill level
        @JvmField var health: Double = 0.0,
        @JvmField var maxHealth: Double = 0.0,
        @JvmField var spawned: Boolean = false,
        @JvmField var building: String = "",      // the guard post they are stationed at
        @JvmField var buildingId: Int = -1,
        @JvmField var buildingLevel: Int = 0,    // that post's level
        @JvmField var equipment: MutableList<EquipmentInfo> = ArrayList(),
        @JvmField var armorPoints: Int = 0,      // total vanilla armour value across the four slots
        @JvmField var weapon: String? = null,     // main-hand item name, null when empty-handed
        @JvmField var x: Int = 0,
        @JvmField var y: Int = 0,
        @JvmField var z: Int = 0
    )

    /** A guard tower / barracks and how well it is staffed. */
    data class Post(
        @JvmField var id: Int = 0,
        @JvmField var name: String = "",
        @JvmField var type: String = "",
        @JvmField var blockId: String = "",
        @JvmField var level: Int = 0,
        @JvmField var assigned: Int = 0,
        @JvmField var capacity: Int = 0,
        @JvmField var x: Int = 0,
        @JvmField var y: Int = 0,
        @JvmField var z: Int = 0
    )

    /** An active colony event — usually an ongoing raid. */
    data class Event(
        @JvmField var id: Int = 0,
        @JvmField var name: String = "",
        @JvmField var status: String = "",
        @JvmField var x: Int = 0,
        @JvmField var y: Int = 0,
        @JvmField var z: Int = 0
    )
}
