package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.BuildingInfo
import DahRealPanda.plugins.colonyweb.model.CitizenInfo
import DahRealPanda.plugins.colonyweb.model.ColonyScan
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.CombatInfo
import DahRealPanda.plugins.colonyweb.model.ItemCount
import DahRealPanda.plugins.colonyweb.model.ResearchInfo
import DahRealPanda.plugins.colonyweb.model.ResourceEntry
import DahRealPanda.plugins.colonyweb.model.WorkOrderInfo
import DahRealPanda.plugins.colonyweb.support.FakeCitizenManager
import DahRealPanda.plugins.colonyweb.support.FakeColony
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * The overview tab is nothing but these numbers, and they are computed from lists that are
 * routinely empty — a brand new colony has no citizens, no warehouse and no buildings — so the
 * empty cases matter as much as the arithmetic.
 */
class StatsServiceTest : DescribeSpec({

    fun colony(maxCitizens: Int = 0, overallHappiness: Double? = null) = FakeColony(
        citizenManager = FakeCitizenManager(maxCitizens = maxCitizens),
        overallHappiness = overallHappiness
    )

    fun citizen(
        id: Int, happiness: Double = 0.0, saturation: Double = 0.0,
        child: Boolean = false, jobType: String? = "minecolonies:builder"
    ) = CitizenInfo(id = id, happiness = happiness, saturation = saturation, child = child, jobType = jobType)

    describe("citizen numbers") {
        it("counts the citizens and reports the colony's cap") {
            val snapshot = ColonySnapshot()

            StatsService().fill(colony(maxCitizens = 25), snapshot, listOf(citizen(1), citizen(2)), CombatInfo(), 0)

            snapshot.stats.citizens shouldBe 2
            snapshot.stats.maxCitizens shouldBe 25
        }

        it("averages happiness and saturation across the colony") {
            val snapshot = ColonySnapshot()
            val citizens = listOf(
                citizen(1, happiness = 6.0, saturation = 10.0),
                citizen(2, happiness = 8.0, saturation = 20.0)
            )

            StatsService().fill(colony(), snapshot, citizens, CombatInfo(), 0)

            snapshot.stats.happiness shouldBe 7.0
            snapshot.stats.saturation shouldBe 15.0
        }

        // A brand new colony has nobody in it, and an average over nobody is not a number.
        it("leaves the averages at zero for an empty colony rather than dividing by zero") {
            val snapshot = ColonySnapshot()

            StatsService().fill(colony(), snapshot, emptyList(), CombatInfo(), 0)

            snapshot.stats.citizens shouldBe 0
            snapshot.stats.happiness shouldBe 0.0
            snapshot.stats.saturation shouldBe 0.0
        }

        it("prefers the colony's own happiness figure over the average of its citizens") {
            val snapshot = ColonySnapshot()

            StatsService().fill(
                colony(overallHappiness = 9.5), snapshot,
                listOf(citizen(1, happiness = 1.0)), CombatInfo(), 0
            )

            snapshot.stats.happiness shouldBe 9.5
        }

        it("keeps the citizen average when the colony reports no happiness of its own") {
            val snapshot = ColonySnapshot()

            StatsService().fill(colony(), snapshot, listOf(citizen(1, happiness = 6.0)), CombatInfo(), 0)

            snapshot.stats.happiness shouldBe 6.0
        }

        it("counts children and the unemployed separately, and counts neither twice") {
            val snapshot = ColonySnapshot()
            val citizens = listOf(
                citizen(1),
                citizen(2, child = true, jobType = null),
                citizen(3, jobType = null),
                citizen(4, jobType = null)
            )

            StatsService().fill(colony(), snapshot, citizens, CombatInfo(), 0)

            snapshot.stats.children shouldBe 1
            snapshot.stats.unemployed shouldBe 2
        }

        it("does not count a child who somehow has a job as unemployed") {
            val snapshot = ColonySnapshot()

            StatsService().fill(colony(), snapshot, listOf(citizen(1, child = true)), CombatInfo(), 0)

            snapshot.stats.children shouldBe 1
            snapshot.stats.unemployed shouldBe 0
        }

        it("reports a cap of zero when the colony exposes no citizen manager") {
            val snapshot = ColonySnapshot()

            StatsService().fill(FakeColony(), snapshot, listOf(citizen(1)), CombatInfo(), 0)

            snapshot.stats.maxCitizens shouldBe 0
        }
    }

    describe("building numbers") {
        it("counts buildings and decorations apart") {
            val snapshot = ColonySnapshot()
            snapshot.buildings.add(BuildingInfo(id = 1, kind = "building"))
            snapshot.buildings.add(BuildingInfo(id = 2, kind = "building"))
            snapshot.buildings.add(BuildingInfo(id = 3, kind = "decoration"))

            StatsService().fill(colony(), snapshot, emptyList(), CombatInfo(), builderCount = 2)

            snapshot.stats.buildings shouldBe 2
            snapshot.stats.decorations shouldBe 1
            snapshot.stats.builders shouldBe 2
        }

        it("counts the open work orders") {
            val snapshot = ColonySnapshot()
            snapshot.workOrders.add(WorkOrderInfo(id = 1))
            snapshot.workOrders.add(WorkOrderInfo(id = 2))

            StatsService().fill(colony(), snapshot, emptyList(), CombatInfo(), 0)

            snapshot.stats.workOrders shouldBe 2
        }
    }

    describe("warehouse numbers") {
        it("counts distinct items and the total stock separately") {
            val snapshot = ColonySnapshot()
            snapshot.warehouse.stacks.add(ColonySnapshot.Stack(count = 64).apply { itemKey = "minecraft:stone" })
            snapshot.warehouse.stacks.add(ColonySnapshot.Stack(count = 12).apply { itemKey = "minecraft:oak_log" })

            StatsService().fill(colony(), snapshot, emptyList(), CombatInfo(), 0)

            snapshot.stats.warehouseTypes shouldBe 2
            snapshot.stats.warehouseItems shouldBe 76
        }

        it("reports an empty warehouse as empty") {
            val snapshot = ColonySnapshot()

            StatsService().fill(colony(), snapshot, emptyList(), CombatInfo(), 0)

            snapshot.stats.warehouseTypes shouldBe 0
            snapshot.stats.warehouseItems shouldBe 0
        }
    }

    describe("defence numbers") {
        it("carries the combat figures onto the overview") {
            val snapshot = ColonySnapshot()
            val combat = CombatInfo(underAttack = true, nightsSinceRaid = 4, guardCount = 6)

            StatsService().fill(colony(), snapshot, emptyList(), combat, 0)

            snapshot.stats.guards shouldBe 6
            snapshot.stats.raided shouldBe true
            snapshot.stats.nightsSinceRaid shouldBe 4
        }
    }
})
