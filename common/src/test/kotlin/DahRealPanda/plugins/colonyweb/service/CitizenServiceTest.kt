package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.BuildingInfo
import DahRealPanda.plugins.colonyweb.model.CitizenInfo
import DahRealPanda.plugins.colonyweb.model.EquipmentInfo
import DahRealPanda.plugins.colonyweb.model.ItemCount
import DahRealPanda.plugins.colonyweb.support.ExplodingCitizen
import DahRealPanda.plugins.colonyweb.support.FakeCitizen
import DahRealPanda.plugins.colonyweb.support.FakeCitizenManager
import DahRealPanda.plugins.colonyweb.support.FakeColony
import DahRealPanda.plugins.colonyweb.support.FakeHappinessHandler
import DahRealPanda.plugins.colonyweb.support.FakeJob
import DahRealPanda.plugins.colonyweb.support.FakePlatform
import DahRealPanda.plugins.colonyweb.support.FakeSkillHandler
import DahRealPanda.plugins.colonyweb.support.MalformedCitizenManager
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.minecraft.core.BlockPos

/**
 * The citizen list is the busiest screen in the dashboard, and every value on it comes out of
 * reflection one citizen at a time. This covers what is read, the order the roster comes back in,
 * and what happens when a single citizen cannot be read at all.
 */
class CitizenServiceTest : DescribeSpec({

    beforeSpec { FakePlatform.withMineColonies() }

    val builderHut = BlockPos(30, 64, 40)
    val house = BlockPos(50, 64, 60)

    fun colonyOf(vararg citizens: Any) = FakeColony(citizenManager = FakeCitizenManager(citizens.toList()))

    fun builderJob() = FakeJob(registryKey = "minecolonies:builder", primarySkill = "Strength", secondarySkill = "Focus")

    describe("reading one citizen") {
        it("reads the basics") {
            val citizen = FakeCitizen(id = 7, name = "Ann", female = true, saturation = 12.5)

            val info = CitizenService().scan(colonyOf(citizen), emptyMap()).citizens.single()

            info.id shouldBe 7
            info.name shouldBe "Ann"
            info.female shouldBe true
            info.child shouldBe false
            info.saturation shouldBe 12.5
        }

        it("names a citizen after their id when the game gives no name") {
            val nameless = object {
                fun getId(): Int = 7
            }

            CitizenService().scan(colonyOf(nameless), emptyMap()).citizens.single().name shouldBe "Citizen 7"
        }

        it("reads a citizen's job and keeps the registry id for the icon") {
            val citizen = FakeCitizen(id = 1, name = "Ann", job = builderJob())

            val info = CitizenService().scan(colonyOf(citizen), emptyMap()).citizens.single()

            info.jobType shouldBe "minecolonies:builder"
            info.job shouldBe "Builder"
            info.primarySkill shouldBe "Strength"
            info.secondarySkill shouldBe "Focus"
        }

        it("labels an adult with no job as unemployed, and leaves the job type unset") {
            val info = CitizenService().scan(colonyOf(FakeCitizen(name = "Ann")), emptyMap()).citizens.single()

            info.job shouldBe "Unemployed"
            info.jobType.shouldBeNull()
        }

        it("labels a child as a child rather than as unemployed") {
            val info = CitizenService()
                .scan(colonyOf(FakeCitizen(name = "Ann", child = true)), emptyMap()).citizens.single()

            info.child shouldBe true
            info.job shouldBe "Child"
        }

        it("links the buildings a citizen works and lives in") {
            val buildingByPos = mapOf(
                builderHut to BuildingInfo(id = 100, name = "Builder", blockId = "minecolonies:blockhutbuilder"),
                house to BuildingInfo(id = 200, name = "Home")
            )
            val citizen = FakeCitizen(
                name = "Ann",
                workBuilding = object { fun getID(): BlockPos = builderHut },
                homeBuilding = object { fun getID(): BlockPos = house }
            )

            val info = CitizenService().scan(colonyOf(citizen), buildingByPos).citizens.single()

            info.workBuilding shouldBe "Builder"
            info.workBuildingId shouldBe 100
            info.jobIcon shouldBe "minecolonies:blockhutbuilder"
            info.homeBuilding shouldBe "Home"
            info.homeBuildingId shouldBe 200
        }

        it("leaves the buildings unset when the citizen's hut is not in the index") {
            val citizen = FakeCitizen(
                name = "Ann",
                workBuilding = object { fun getID(): BlockPos = BlockPos(999, 64, 999) }
            )

            val info = CitizenService().scan(colonyOf(citizen), emptyMap()).citizens.single()

            info.workBuilding shouldBe ""
            info.workBuildingId shouldBe -1
        }

        // A citizen whose chunk is not loaded has no entity, so their last known position is all
        // there is, and their health is unknown rather than zero.
        it("falls back to the last known position for a citizen who is not loaded") {
            val citizen = FakeCitizen(name = "Ann", lastPosition = BlockPos(1, 2, 3))

            val info = CitizenService().scan(colonyOf(citizen), emptyMap()).citizens.single()

            info.spawned shouldBe false
            info.x shouldBe 1
            info.y shouldBe 2
            info.z shouldBe 3
            info.maxHealth shouldBe 20.0
        }

        it("reads happiness through the handler") {
            val citizen = FakeCitizen(name = "Ann", happinessHandler = FakeHappinessHandler(8.5))

            CitizenService().scan(colonyOf(citizen), emptyMap()).citizens.single().happiness shouldBe 8.5
        }

        it("leaves happiness at zero when the game exposes no handler") {
            CitizenService().scan(colonyOf(FakeCitizen(name = "Ann")), emptyMap())
                .citizens.single().happiness shouldBe 0.0
        }

        it("reads the skill levels and totals them") {
            val citizen = FakeCitizen(
                name = "Ann",
                job = builderJob(),
                skillHandler = FakeSkillHandler(
                    levels = mapOf("Strength" to 5, "Focus" to 3, "Stamina" to 2),
                    experience = mapOf("Strength" to 120.0)
                )
            )

            val info = CitizenService().scan(colonyOf(citizen), emptyMap()).citizens.single()

            info.skillTotal shouldBe 10
            info.skills.first { it.name == "Strength" }.level shouldBe 5
            info.skills.first { it.name == "Strength" }.xp shouldBe 120.0
        }

        it("marks the skills the citizen's job actually uses") {
            val citizen = FakeCitizen(
                name = "Ann",
                job = builderJob(),
                skillHandler = FakeSkillHandler(mapOf("Strength" to 5, "Focus" to 3, "Stamina" to 2))
            )

            val skills = CitizenService().scan(colonyOf(citizen), emptyMap()).citizens.single().skills

            skills.first { it.name == "Strength" }.role shouldBe "primary"
            skills.first { it.name == "Focus" }.role shouldBe "secondary"
            skills.first { it.name == "Stamina" }.role.shouldBeNull()
        }

        it("reads no skills at all when the game exposes no handler") {
            val info = CitizenService().scan(colonyOf(FakeCitizen(name = "Ann")), emptyMap()).citizens.single()

            info.skills.shouldBeEmpty()
            info.skillTotal shouldBe 0
        }
    }

    describe("the roster order") {
        // Workers first, then children, then the unemployed — so the people a player is likely to
        // be looking for are at the top and the ones needing a job are grouped together.
        it("puts workers before children and children before the unemployed") {
            val colony = colonyOf(
                FakeCitizen(id = 1, name = "Unemployed"),
                FakeCitizen(id = 2, name = "Child", child = true),
                FakeCitizen(id = 3, name = "Worker", job = builderJob())
            )

            CitizenService().scan(colony, emptyMap()).citizens.map { it.name } shouldContainExactly
                    listOf("Worker", "Child", "Unemployed")
        }

        it("sorts by name within a group, ignoring case") {
            val colony = colonyOf(
                FakeCitizen(id = 1, name = "charlie", job = builderJob()),
                FakeCitizen(id = 2, name = "Alice", job = builderJob()),
                FakeCitizen(id = 3, name = "bob", job = builderJob())
            )

            CitizenService().scan(colony, emptyMap()).citizens.map { it.name } shouldContainExactly
                    listOf("Alice", "bob", "charlie")
        }
    }

    describe("colonies that cannot be read") {
        it("returns nobody when the colony exposes no citizen manager") {
            val result = CitizenService().scan(FakeColony(), emptyMap())

            result.citizens.shouldBeEmpty()
            result.rawCitizens.shouldBeEmpty()
        }

        it("returns nobody when the citizen manager returns something that is not a list") {
            CitizenService().scan(FakeColony(citizenManager = MalformedCitizenManager()), emptyMap())
                .citizens.shouldBeEmpty()
        }

        it("returns nobody for an empty colony") {
            CitizenService().scan(colonyOf(), emptyMap()).citizens.shouldBeEmpty()
        }

        it("skips a null entry in the citizen list") {
            val colony = FakeColony(citizenManager = FakeCitizenManager(listOf(null, FakeCitizen(name = "Ann"))))

            CitizenService().scan(colony, emptyMap()).citizens.map { it.name } shouldContainExactly listOf("Ann")
        }

        // One unreadable citizen is a bad row, not a broken colony.
        it("skips a citizen it cannot read and keeps the rest of the roster") {
            val colony = colonyOf(
                FakeCitizen(id = 1, name = "Ann", job = builderJob()),
                ExplodingCitizen(),
                FakeCitizen(id = 2, name = "Bob", job = builderJob())
            )

            CitizenService().scan(colony, emptyMap()).citizens.map { it.name } shouldContainExactly
                    listOf("Ann", "Bob")
        }
    }

    describe("the cache the API reads from") {
        it("serves back a stored roster and finds one citizen in it") {
            val service = CitizenService()
            service.storeCitizens(1, listOf(CitizenInfo(id = 5, name = "Ann")))

            service.citizens(1)!!.single().name shouldBe "Ann"
            service.citizen(1, 5)!!.name shouldBe "Ann"
        }

        it("has nothing for a colony or a citizen it has never seen") {
            val service = CitizenService()
            service.storeCitizens(1, listOf(CitizenInfo(id = 5)))

            service.citizens(99).shouldBeNull()
            service.citizen(1, 99).shouldBeNull()
            service.citizen(99, 5).shouldBeNull()
        }

        it("serves an empty inventory and kit rather than null for an unknown citizen") {
            val service = CitizenService()

            service.inventory(1, 5).shouldBeEmpty()
            service.equipment(1, 5).shouldBeEmpty()
        }

        it("serves back stored inventories and kit") {
            val service = CitizenService()
            service.storeInventories(1, mapOf(5 to listOf(ItemCount().apply { itemKey = "minecraft:stone" })))
            service.storeEquipment(1, mapOf(5 to listOf(EquipmentInfo().apply { itemKey = "minecraft:iron_helmet" })))

            service.inventory(1, 5).single().itemKey shouldBe "minecraft:stone"
            service.equipment(1, 5).single().itemKey shouldBe "minecraft:iron_helmet"
        }

        it("forgets a colony that no longer exists, including its inventories and kit") {
            val service = CitizenService()
            service.storeCitizens(1, listOf(CitizenInfo(id = 5)))
            service.storeInventories(1, mapOf(5 to emptyList()))
            service.storeEquipment(1, mapOf(5 to emptyList()))
            service.storeCitizens(2, listOf(CitizenInfo(id = 6)))

            service.retainOnly(listOf(2))

            service.citizens(1).shouldBeNull()
            service.inventory(1, 5).shouldBeEmpty()
            service.equipment(1, 5).shouldBeEmpty()
            service.citizens(2)!!.single().id shouldBe 6
        }
    }
})
