package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.BuildingInfo
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.ColonySummary
import DahRealPanda.plugins.colonyweb.support.ExplodingBuilding
import DahRealPanda.plugins.colonyweb.support.FakeBuilding
import DahRealPanda.plugins.colonyweb.support.PositionOnlyBuilding
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import net.minecraft.core.BlockPos

/**
 * [BuildingService] turns MineColonies' building objects into the list the dashboard draws, and
 * builds the position index everything else — work orders, citizens, guards — is resolved
 * through. A building it drops disappears from all of those at once.
 */
class BuildingServiceTest : DescribeSpec({

    val townHall = BlockPos(10, 64, 20)
    val builderHut = BlockPos(30, 64, 40)

    fun summary(id: Int) = ColonySummary(id, "Colony $id", "minecraft:overworld", "Ann", 0, 0, 0, 0, 0, 0)

    describe("scanning buildings") {
        it("reads a building's position, level, type and readable name") {
            val result = BuildingService().scan(
                listOf(FakeBuilding(builderHut, type = "minecolonies:builder", level = 3)),
                null
            )

            val building = result.buildings.single()
            building.type shouldBe "minecolonies:builder"
            building.name shouldBe "Builder"
            building.level shouldBe 3
            building.x shouldBe 30
            building.y shouldBe 64
            building.z shouldBe 40
            building.kind shouldBe "building"
        }

        it("indexes each building by its position, so work orders can be linked to it") {
            val result = BuildingService().scan(
                listOf(
                    FakeBuilding(townHall, type = "minecolonies:townhall"),
                    FakeBuilding(builderHut, type = "minecolonies:builder")
                ),
                null
            )

            result.buildingByPos.keys shouldContainExactlyInAnyOrder listOf(townHall, builderHut)
            result.buildingByPos[builderHut]!!.name shouldBe "Builder"
            result.rawBuildingByPos.keys shouldContainExactlyInAnyOrder listOf(townHall, builderHut)
        }

        it("falls back to getPosition for buildings that do not expose getID") {
            val result = BuildingService().scan(listOf(PositionOnlyBuilding(townHall, level = 2)), null)

            result.buildings.single().level shouldBe 2
            result.buildingByPos.keys shouldContainExactly listOf(townHall)
        }

        it("returns an empty result for a colony with no buildings") {
            val result = BuildingService().scan(emptyList(), null)

            result.buildings.shouldBeEmpty()
            result.buildingByPos.keys.shouldBeEmpty()
        }

        // One building failing to read must not cost the player the rest of their colony.
        it("skips a building it cannot read and keeps the others") {
            val result = BuildingService().scan(
                listOf(
                    FakeBuilding(townHall, type = "minecolonies:townhall"),
                    ExplodingBuilding(),
                    FakeBuilding(builderHut, type = "minecolonies:builder")
                ),
                null
            )

            result.buildings.map { it.name } shouldContainExactlyInAnyOrder listOf("Townhall", "Builder")
        }

        it("skips a building that has no position at all") {
            val nowhere = object {
                fun getBuildingLevel(): Int = 1
            }

            BuildingService().scan(listOf(nowhere), null).buildings.shouldBeEmpty()
        }

        it("gives a building with no readable type a fallback name rather than a blank one") {
            val result = BuildingService().scan(listOf(FakeBuilding(townHall)), null)

            result.buildings.single().type shouldBe "unknown"
            result.buildings.single().name shouldBe "Unknown"
        }

        it("defaults an unreadable level to zero") {
            val levelless = object {
                fun getID(): BlockPos = townHall
            }

            BuildingService().scan(listOf(levelless), null).buildings.single().level shouldBe 0
        }
    }

    describe("working out what a building is") {
        it("prefers the building's own registered type") {
            BuildingService().typeOf(
                FakeBuilding(builderHut, type = "minecolonies:builder"),
                "minecolonies:blockhutwarehouse"
            ) shouldBe "minecolonies:builder"
        }

        // Some buildings report a type that is only a class name. The hut block standing at the
        // position names the building just as well, so it is used instead.
        it("infers the type from the hut block when the building does not name one") {
            BuildingService().typeOf(
                FakeBuilding(builderHut),
                "minecolonies:blockhutbuilder"
            ) shouldBe "minecolonies:builder"
        }

        it("gives up rather than inventing a type when neither source says anything") {
            BuildingService().typeOf(FakeBuilding(builderHut), "minecraft:stone") shouldBe "unknown"
            BuildingService().typeOf(FakeBuilding(builderHut), "") shouldBe "unknown"
        }

        it("does not treat a bare hut prefix as a building type") {
            BuildingService().typeOf(FakeBuilding(builderHut), "minecolonies:blockhut") shouldBe "unknown"
        }
    }

    describe("recognising particular buildings") {
        it("counts the colony's builders") {
            val service = BuildingService()
            val buildings = service.scan(
                listOf(
                    FakeBuilding(builderHut, type = "minecolonies:builder"),
                    FakeBuilding(BlockPos(31, 64, 41), type = "minecolonies:builder"),
                    FakeBuilding(townHall, type = "minecolonies:townhall")
                ),
                null
            )

            service.countBuilders(buildings.buildings) shouldBe 2
        }

        it("counts no builders in a colony that has none") {
            val service = BuildingService()

            service.countBuilders(emptyList()) shouldBe 0
            service.countBuilders(listOf(BuildingInfo(type = "minecolonies:townhall"))) shouldBe 0
        }

        it("counts builders straight from the raw buildings too") {
            BuildingService().countRawBuilders(
                listOf(
                    FakeBuilding(builderHut, type = "minecolonies:builder"),
                    FakeBuilding(townHall, type = "minecolonies:townhall")
                )
            ) shouldBe 1
        }

        it("recognises the warehouse by its type") {
            BuildingService().isWarehouse(BuildingInfo(type = "minecolonies:warehouse")) shouldBe true
        }

        it("recognises the warehouse by its hut block when the type is unhelpful") {
            BuildingService().isWarehouse(
                BuildingInfo(type = "unknown", blockId = "minecolonies:blockhutwarehouse")
            ) shouldBe true
        }

        it("does not mistake another building for the warehouse") {
            BuildingService().isWarehouse(BuildingInfo(type = "minecolonies:builder")) shouldBe false
            BuildingService().isWarehouse(BuildingInfo()) shouldBe false
        }
    }

    describe("naming") {
        it("turns a registry name into something readable") {
            val service = BuildingService()

            service.prettyName("minecolonies:guardtower") shouldBe "Guardtower"
            service.prettyName("minecolonies:combat_academy") shouldBe "Combat Academy"
        }

        it("names a building whose registry name is empty") {
            BuildingService().prettyName("") shouldBe "Building"
            BuildingService().prettyName("minecolonies:") shouldBe "Building"
        }
    }

    describe("the cache the API reads from") {
        it("serves back the snapshot that was stored") {
            val service = BuildingService()
            service.storeSnapshot(1, ColonySnapshot(id = 1, name = "Ann's Colony"))

            service.snapshot(1).shouldNotBeNull().name shouldBe "Ann's Colony"
        }

        it("has nothing for a colony that has never been scanned") {
            BuildingService().snapshot(99).shouldBeNull()
        }

        it("starts with no summaries and serves back the ones it is given") {
            val service = BuildingService()
            service.summaries().shouldBeEmpty()

            service.setSummaries(listOf(summary(1)))

            service.summaries().map { it.id } shouldContainExactly listOf(1)
        }

        // A colony that was deleted in game must not linger in the cache: its data would keep
        // being served to anyone who still has access to that id.
        it("forgets colonies that no longer exist") {
            val service = BuildingService()
            service.storeSnapshot(1, ColonySnapshot(id = 1))
            service.storeSnapshot(2, ColonySnapshot(id = 2))

            service.retainOnly(listOf(summary(1)))

            service.snapshot(1).shouldNotBeNull()
            service.snapshot(2).shouldBeNull()
        }

        it("forgets everything when the last colony goes") {
            val service = BuildingService()
            service.storeSnapshot(1, ColonySnapshot(id = 1))

            service.retainOnly(emptyList())

            service.snapshot(1).shouldBeNull()
        }
    }
})
