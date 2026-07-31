package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.BuildingInfo
import DahRealPanda.plugins.colonyweb.model.ColonyScan
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.ItemCount
import DahRealPanda.plugins.colonyweb.model.ResearchInfo
import DahRealPanda.plugins.colonyweb.model.ResourceEntry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [RecipeService.markCraftable] is what puts the "a worker here can make this" mark on a missing
 * resource. It reaches into every item list in the scan at once — warehouse, build requirements,
 * citizen inventories and research costs — which is exactly why it is worth checking that it
 * reaches all of them and marks nothing else.
 */
class RecipeServiceTest : DescribeSpec({

    val craftable = "minecolonies:blockhutbuilder"

    fun scanWithItemEverywhere(): ColonyScan {
        val scan = ColonyScan()
        scan.snapshot = ColonySnapshot(id = 1)
        scan.snapshot.warehouse.stacks.add(ColonySnapshot.Stack(count = 1).apply { itemKey = craftable })
        scan.snapshot.buildings.add(BuildingInfo(id = 1).apply {
            required.add(ResourceEntry(needed = 5).apply { itemKey = craftable })
            required.add(ResourceEntry(needed = 5).apply { itemKey = "minecraft:diamond" })
        })
        scan.inventories = mapOf(1 to listOf(ItemCount(count = 3).apply { itemKey = craftable }))
        scan.research = ResearchInfo().apply {
            branches.add(ResearchInfo.Branch(id = "civil", name = "Civilisation").apply {
                researches.add(ResearchInfo.Entry(id = "r1", name = "Research").apply {
                    cost.add(ItemCount(count = 1).apply { itemKey = craftable })
                })
            })
        }
        return scan
    }

    describe("markCraftable") {
        it("marks the item in every list it appears in") {
            val scan = scanWithItemEverywhere()

            RecipeService.markCraftable(scan, setOf(craftable))

            scan.snapshot.warehouse.stacks.single().craftable shouldBe true
            scan.snapshot.buildings.single().required.first().craftable shouldBe true
            scan.inventories.values.single().single().craftable shouldBe true
            scan.research!!.branches.single().researches.single().cost.single().craftable shouldBe true
        }

        it("leaves an item nobody can craft alone") {
            val scan = scanWithItemEverywhere()

            RecipeService.markCraftable(scan, setOf(craftable))

            scan.snapshot.buildings.single().required.last().craftable shouldBe false
        }

        it("marks nothing when no worker knows a recipe") {
            val scan = scanWithItemEverywhere()

            RecipeService.markCraftable(scan, emptySet())

            scan.snapshot.warehouse.stacks.single().craftable shouldBe false
            scan.snapshot.buildings.single().required.first().craftable shouldBe false
        }

        // Research is only rescanned every few passes, so most scans reach this with nothing there.
        it("copes with a scan whose research was not refreshed this pass") {
            val scan = scanWithItemEverywhere()
            scan.research = null

            RecipeService.markCraftable(scan, setOf(craftable))

            scan.snapshot.warehouse.stacks.single().craftable shouldBe true
        }

        it("copes with a completely empty scan") {
            val scan = ColonyScan()
            scan.snapshot = ColonySnapshot(id = 1)

            RecipeService.markCraftable(scan, setOf(craftable))

            scan.snapshot.buildings.size shouldBe 0
        }
    }

    // Without MineColonies there is no colony manager to ask, and the scan has to come back empty
    // rather than throwing — which is also what happens on a version whose API has moved.
    describe("scanning for recipes without MineColonies present") {
        it("finds nothing and does not throw") {
            RecipeService().scan(listOf(Any())).size shouldBe 0
        }

        it("finds nothing for a colony with no buildings") {
            RecipeService().scan(emptyList()).size shouldBe 0
        }
    }
})
