package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.support.FakeBuilding
import DahRealPanda.plugins.colonyweb.support.FakeRequiredResource
import DahRealPanda.plugins.colonyweb.support.FakeWorkOrder
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import net.minecraft.core.BlockPos

/**
 * Work orders are what the build-queue screen is made of. Reading one means joining three things
 * up — the order, the building it targets, and the builder that claimed it — any of which can be
 * missing, so most of this is about what the screen shows when they are.
 */
class WorkOrderServiceTest : DescribeSpec({

    val target = BlockPos(30, 64, 40)
    val builderHut = BlockPos(10, 64, 20)

    class Fixture(rawBuildings: List<Any> = emptyList()) {
        val snapshot = ColonySnapshot(id = 1, name = "Ann's Colony")
        val buildings = BuildingService().scan(rawBuildings, null)
        val service = WorkOrderService()

        init {
            snapshot.buildings.addAll(buildings.buildings)
        }

        fun scan(vararg workOrders: Any) {
            service.scan(workOrders.toList(), buildings, snapshot, snapshot.warehouse)
        }
    }

    describe("reading a work order") {
        it("reads the id, position and levels") {
            val fixture = Fixture(listOf(FakeBuilding(target, type = "minecolonies:builder", level = 2)))

            fixture.scan(FakeWorkOrder(id = 7, location = target, currentLevel = 2, targetLevel = 3))

            val order = fixture.snapshot.workOrders.single()
            order.id shouldBe 7
            order.x shouldBe 30
            order.y shouldBe 64
            order.z shouldBe 40
            order.currentLevel shouldBe 2
            order.targetLevel shouldBe 3
        }

        it("links the order to the building it targets and flags that building as under way") {
            val fixture = Fixture(listOf(FakeBuilding(target, type = "minecolonies:builder", level = 2)))

            fixture.scan(FakeWorkOrder(id = 7, location = target, currentLevel = 2, targetLevel = 3))

            fixture.snapshot.workOrders.single().buildingType shouldBe "minecolonies:builder"
            val building = fixture.buildings.buildingByPos[target]!!
            building.beingBuilt shouldBe true
            building.workOrderId shouldBe 7
        }

        it("reads a work order that has no id as -1 rather than as zero") {
            val fixture = Fixture()
            val idless = object {
                fun getLocation(): BlockPos = target
            }

            fixture.scan(idless)

            fixture.snapshot.workOrders.single().id shouldBe -1
        }

        // Reflection never throws, so an unreadable order still produces a row — with the blank
        // values the coercion defaults to. What matters is that it does not cost the queue the
        // orders after it.
        it("records an unreadable work order as a blank row and carries on with the rest") {
            val fixture = Fixture()
            val exploding = object {
                fun getID(): Int = throw IllegalStateException("work order is gone")
                fun getLocation(): BlockPos = throw IllegalStateException("work order is gone")
            }

            fixture.scan(exploding, FakeWorkOrder(id = 7, location = target))

            fixture.snapshot.workOrders.map { it.id } shouldBe listOf(-1, 7)
        }
    }

    describe("working out what is being done") {
        it("calls a first build a BUILD") {
            val fixture = Fixture()

            fixture.scan(FakeWorkOrder(location = target, currentLevel = 0, targetLevel = 1))

            fixture.snapshot.workOrders.single().action shouldBe "BUILD"
        }

        it("calls raising an existing building an UPGRADE") {
            val fixture = Fixture()

            fixture.scan(FakeWorkOrder(location = target, currentLevel = 2, targetLevel = 3))

            fixture.snapshot.workOrders.single().action shouldBe "UPGRADE"
        }

        it("uses the order's own type when the game states one") {
            val fixture = Fixture()

            fixture.scan(FakeWorkOrder(location = target, workOrderType = "REMOVE"))
            fixture.scan(FakeWorkOrder(location = target, workOrderType = "REPAIR"))

            fixture.snapshot.workOrders.map { it.action } shouldBe listOf("REMOVE", "REPAIR")
        }

        it("reads a stated BUILD of an existing building as an upgrade anyway") {
            val fixture = Fixture()

            fixture.scan(FakeWorkOrder(location = target, workOrderType = "BUILD", currentLevel = 2, targetLevel = 3))

            fixture.snapshot.workOrders.single().action shouldBe "UPGRADE"
        }

        it("falls back to BUILD for a type it does not recognise") {
            val fixture = Fixture()

            fixture.scan(FakeWorkOrder(location = target, workOrderType = "SOMETHING_NEW"))

            fixture.snapshot.workOrders.single().action shouldBe "BUILD"
        }
    }

    // Decorations are not buildings, so nothing indexed them during the building scan. They still
    // have to appear on the map and in the queue, so the work order creates the entry itself.
    describe("work orders that target something that is not a building") {
        it("adds a decoration entry for the target") {
            val fixture = Fixture()

            fixture.scan(FakeWorkOrder(id = 7, location = target, structureName = "decorations/wooden_bridge"))

            val decoration = fixture.snapshot.buildings.single()
            decoration.kind shouldBe "decoration"
            decoration.name shouldBe "Wooden Bridge"
            decoration.x shouldBe 30
            decoration.workOrderId shouldBe 7
            decoration.beingBuilt shouldBe true
        }

        it("names an unnamed decoration rather than leaving it blank") {
            val fixture = Fixture()

            fixture.scan(FakeWorkOrder(location = target))

            fixture.snapshot.buildings.single().name shouldBe "Decoration"
        }

        it("adds nothing at all when the order does not say where it is") {
            val fixture = Fixture()

            fixture.scan(FakeWorkOrder(id = 7))

            fixture.snapshot.buildings.shouldBeEmpty()
            fixture.snapshot.workOrders.single().id shouldBe 7
        }
    }

    describe("linking the builder that claimed the order") {
        it("records the builder and names them after the citizen working there") {
            val builderCitizen = object {
                fun getName(): String = "Bob the Builder"
            }
            val fixture = Fixture(
                listOf(
                    FakeBuilding(builderHut, type = "minecolonies:builder", assignedCitizens = listOf(builderCitizen)),
                    FakeBuilding(target, type = "minecolonies:farmer")
                )
            )

            fixture.scan(FakeWorkOrder(id = 7, location = target, claimedBy = builderHut))

            val order = fixture.snapshot.workOrders.single()
            order.builderName shouldBe "Bob the Builder"
            order.builderId shouldBe builderHut.hashCode()
            fixture.snapshot.builders.single().assignedWorkOrderId shouldBe 7
        }

        it("falls back to a generic name when the builder's hut is unstaffed") {
            val fixture = Fixture(listOf(FakeBuilding(builderHut, type = "minecolonies:builder")))

            fixture.scan(FakeWorkOrder(id = 7, location = target, claimedBy = builderHut))

            fixture.snapshot.builders.single().name shouldBe "Builder"
        }

        it("leaves the builder unset for an unclaimed order") {
            val fixture = Fixture()

            fixture.scan(FakeWorkOrder(id = 7, location = target))

            fixture.snapshot.workOrders.single().builderId shouldBe -1
            fixture.snapshot.builders.shouldBeEmpty()
        }

        // MineColonies reports an unclaimed order as claimed by the origin rather than by nothing.
        it("treats a claim on the world origin as no claim") {
            val fixture = Fixture()

            fixture.scan(FakeWorkOrder(id = 7, location = target, claimedBy = BlockPos.ZERO))

            fixture.snapshot.builders.shouldBeEmpty()
        }

        it("records one builder once even when they are working through several orders") {
            val fixture = Fixture(listOf(FakeBuilding(builderHut, type = "minecolonies:builder")))

            fixture.scan(
                FakeWorkOrder(id = 7, location = target, claimedBy = builderHut),
                FakeWorkOrder(id = 8, location = BlockPos(60, 64, 70), claimedBy = builderHut)
            )

            fixture.snapshot.builders.size shouldBe 1
        }
    }

    describe("build progress") {
        it("works out how far along the build is from what is still missing") {
            val hut = FakeBuilding(
                builderHut,
                type = "minecolonies:builder",
                neededResources = mapOf("planks" to FakeRequiredResource(amount = 25))
            )
            val fixture = Fixture(listOf(hut, FakeBuilding(target, type = "minecolonies:farmer")))

            fixture.scan(FakeWorkOrder(id = 7, location = target, claimedBy = builderHut, amountOfResources = 100))

            fixture.snapshot.workOrders.single().progress shouldBe 0.75
        }

        it("reports no progress when nothing has been delivered yet") {
            val hut = FakeBuilding(
                builderHut,
                type = "minecolonies:builder",
                neededResources = mapOf("planks" to FakeRequiredResource(amount = 100))
            )
            val fixture = Fixture(listOf(hut, FakeBuilding(target, type = "minecolonies:farmer")))

            fixture.scan(FakeWorkOrder(id = 7, location = target, claimedBy = builderHut, amountOfResources = 100))

            fixture.snapshot.workOrders.single().progress shouldBe 0.0
        }

        it("never reports more than finished, however the numbers come out") {
            val hut = FakeBuilding(builderHut, type = "minecolonies:builder", neededResources = emptyMap())
            val fixture = Fixture(listOf(hut, FakeBuilding(target, type = "minecolonies:farmer")))

            fixture.scan(FakeWorkOrder(id = 7, location = target, claimedBy = builderHut, amountOfResources = 100))

            fixture.snapshot.workOrders.single().progress shouldBe 1.0
        }

        it("reports no progress when the total is unknown, rather than dividing by zero") {
            val hut = FakeBuilding(
                builderHut,
                type = "minecolonies:builder",
                neededResources = mapOf("planks" to FakeRequiredResource(amount = 25))
            )
            val fixture = Fixture(listOf(hut, FakeBuilding(target, type = "minecolonies:farmer")))

            fixture.scan(FakeWorkOrder(id = 7, location = target, claimedBy = builderHut, amountOfResources = 0))

            fixture.snapshot.workOrders.single().progress shouldBe 0.0
        }

        it("reports progress as a fraction between nothing and finished") {
            val hut = FakeBuilding(
                builderHut,
                type = "minecolonies:builder",
                neededResources = mapOf("planks" to FakeRequiredResource(amount = 1))
            )
            val fixture = Fixture(listOf(hut, FakeBuilding(target, type = "minecolonies:farmer")))

            fixture.scan(FakeWorkOrder(id = 7, location = target, claimedBy = builderHut, amountOfResources = 3))

            val progress = fixture.snapshot.workOrders.single().progress
            progress shouldBeGreaterThan 0.0
            (progress <= 1.0) shouldBe true
        }
    }
})
