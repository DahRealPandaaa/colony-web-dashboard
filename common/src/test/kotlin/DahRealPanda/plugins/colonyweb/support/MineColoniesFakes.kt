package DahRealPanda.plugins.colonyweb.support

import net.minecraft.core.BlockPos

/**
 * Stand-ins for the MineColonies objects the scan services read.
 *
 * ColonyWeb never links against MineColonies: every value it shows is pulled out through
 * [DahRealPanda.plugins.colonyweb.util.MineColoniesReflect], which looks methods up by name at
 * runtime. That is what makes these tests possible — reflection cannot tell a real
 * `IBuilding` from any other object that happens to answer `getID()`, so a handful of plain
 * Kotlin classes with the right method names exercise the real scanning code end to end.
 *
 * It also means these classes are a specification: the method names below are exactly the
 * MineColonies API ColonyWeb depends on, and a rename upstream shows up here as a test failure
 * rather than as a silently empty dashboard.
 *
 * Everything is nullable and defaulted, because the situation worth testing is usually the one
 * where MineColonies hands back nothing.
 */

// ---- Colony ----

class FakeColony(
    private val id: Int = 1,
    private val name: String = "Test Colony",
    private val citizenManager: Any? = null,
    private val overallHappiness: Double? = null,
) {
    fun getID(): Int = id
    fun getName(): String = name
    fun getCitizenManager(): Any? = citizenManager
    fun getOverallHappiness(): Double? = overallHappiness
}

class FakeCitizenManager(
    private val citizens: Collection<Any?> = emptyList(),
    private val maxCitizens: Int = 0,
) {
    fun getCitizens(): Collection<Any?> = citizens
    fun getMaxCitizens(): Int = maxCitizens
}

/** A citizen manager that answers `getCitizens` with something that is not a collection. */
class MalformedCitizenManager {
    fun getCitizens(): Any = "not a collection"
}

// ---- Buildings ----

class FakeBuildingType(private val registryName: String) {
    fun getRegistryName(): String = registryName
}

class FakeBuilding(
    private val id: BlockPos,
    private val type: String? = null,
    private val level: Int = 0,
    private val assignedCitizens: Collection<Any> = emptyList(),
    private val neededResources: Map<Any, Any> = emptyMap(),
) {
    fun getID(): BlockPos = id
    fun getBuildingType(): Any? = type?.let { FakeBuildingType(it) }
    fun getBuildingLevel(): Int = level
    fun getAllAssignedCitizen(): Collection<Any> = assignedCitizens
    fun getNeededResources(): Map<Any, Any> = neededResources
}

/**
 * Older building types expose their position as `getPosition` rather than `getID`; the scanner
 * tries both, and this covers the second branch.
 */
class PositionOnlyBuilding(private val position: BlockPos, private val level: Int = 0) {
    fun getPosition(): BlockPos = position
    fun getBuildingLevel(): Int = level
}

/** A building whose accessors blow up, standing in for a version mismatch at runtime. */
class ExplodingBuilding {
    fun getID(): BlockPos = throw IllegalStateException("building is not loaded")
    fun getBuildingLevel(): Int = throw IllegalStateException("building is not loaded")
}

// ---- Citizens ----

class FakeCitizen(
    private val id: Int = 1,
    private val name: String = "Citizen",
    private val child: Boolean = false,
    private val female: Boolean = false,
    private val saturation: Double = 0.0,
    private val job: Any? = null,
    private val workBuilding: Any? = null,
    private val homeBuilding: Any? = null,
    private val lastPosition: BlockPos? = null,
    private val happinessHandler: Any? = null,
    private val skillHandler: Any? = null,
    private val status: Any? = null,
) {
    fun getId(): Int = id
    fun getName(): String = name
    fun isChild(): Boolean = child
    fun isFemale(): Boolean = female
    fun getSaturation(): Double = saturation
    fun getJob(): Any? = job
    fun getWorkBuilding(): Any? = workBuilding
    fun getHomeBuilding(): Any? = homeBuilding
    fun getLastPosition(): BlockPos? = lastPosition
    fun getCitizenHappinessHandler(): Any? = happinessHandler
    fun getCitizenSkillHandler(): Any? = skillHandler
    fun getStatus(): Any? = status
}

/**
 * A citizen the scanner cannot finish reading.
 *
 * Reflection swallows its own failures, so a citizen only breaks a scan where the scanner touches
 * a returned value directly — as it does when it indexes the skill map. That is what this
 * reproduces, and the scanner is expected to drop this one citizen and carry on with the roster.
 */
class ExplodingCitizen {
    fun getId(): Int = -99
    fun getName(): String = "Broken"
    fun getCitizenSkillHandler(): Any = ThrowingSkillHandler()
}

class ThrowingSkillHandler {
    fun getSkills(): Map<Any, Any> = ThrowingMap
}

/** Lists one skill and then throws when the scanner asks for it. */
object ThrowingMap : Map<Any, Any> {
    override val entries: Set<Map.Entry<Any, Any>> get() = emptySet()
    override val keys: Set<Any> = setOf("Strength")
    override val size: Int get() = 1
    override val values: Collection<Any> get() = emptyList()
    override fun isEmpty(): Boolean = false
    override fun containsKey(key: Any): Boolean = key in keys
    override fun containsValue(value: Any): Boolean = false
    override fun get(key: Any): Any = throw IllegalStateException("citizen data is gone")
}

class FakeJob(
    private val registryKey: String? = null,
    private val primarySkill: Any? = null,
    private val secondarySkill: Any? = null,
    private val displayName: String? = null,
) {
    fun getJobRegistryEntry(): Any? = registryKey?.let { FakeJobRegistryEntry(it) }
    fun getPrimarySkill(): Any? = primarySkill
    fun getSecondarySkill(): Any? = secondarySkill
    fun getName(): String? = displayName
}

class FakeJobRegistryEntry(private val key: String) {
    fun getKey(): String = key
}

class FakeSkillHandler(private val levels: Map<String, Int>, private val experience: Map<String, Double> = emptyMap()) {
    fun getSkills(): Map<Any, Any> = levels.mapValues { (skill, level) ->
        FakeSkillData(level, experience[skill] ?: 0.0)
    }

    fun getLevel(skill: Any): Int = levels[skill.toString()] ?: 0
}

class FakeSkillData(private val level: Int, private val experience: Double) {
    fun getLevel(): Int = level
    fun getExperience(): Double = experience
}

class FakeHappinessHandler(
    private val happiness: Double,
    private val modifiers: Collection<Any> = emptyList(),
    private val factors: Map<String, Double> = emptyMap(),
) {
    fun getHappiness(colony: Any?, citizen: Any?): Double = happiness
    fun getModifiers(): Collection<Any> = modifiers
    fun getModifierFactor(id: String, citizen: Any?): Double = factors[id] ?: 1.0
}

// ---- Work orders ----

class FakeWorkOrder(
    private val id: Int = 1,
    private val location: BlockPos? = null,
    private val currentLevel: Int = 0,
    private val targetLevel: Int = 1,
    private val workOrderType: String? = null,
    private val claimedBy: BlockPos? = null,
    private val structureName: String? = null,
    private val amountOfResources: Int = 0,
) {
    fun getID(): Int = id
    fun getLocation(): BlockPos? = location
    fun getCurrentLevel(): Int = currentLevel
    fun getTargetLevel(): Int = targetLevel
    fun getWorkOrderType(): String? = workOrderType
    fun getClaimedBy(): BlockPos? = claimedBy
    fun getStructureName(): String? = structureName
    fun getAmountOfResources(): Int = amountOfResources
}

/** A required-resources entry as the builder's hut reports it. */
class FakeRequiredResource(private val amount: Int, private val available: Int = 0) {
    fun getAmount(): Int = amount
    fun getAvailable(): Int = available
}
