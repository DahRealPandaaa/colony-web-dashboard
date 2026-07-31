package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.support.FakeBuilding
import DahRealPanda.plugins.colonyweb.support.FakePlatform
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import net.minecraft.core.BlockPos
import java.lang.reflect.Modifier

/**
 * Regression cover for issue #38: the warehouse tab reported zero items on every colony.
 *
 * [WarehouseService] is created once for the whole server and reused for every colony on every
 * scan, but it kept its "racks already counted" set and its per-item aggregates in fields. From
 * the second scan onwards every rack was skipped as a duplicate, so nothing was counted again —
 * and because the warehouse then never changed, the scan hash stopped moving and the dashboard's
 * live-update stream fell silent with it.
 *
 * Counting the items themselves means reading block entities out of a loaded world and building
 * real `ItemStack`s, which needs `Bootstrap.bootStrap()` and a server these tests deliberately do
 * not start. So the world-facing half is covered by the shape of the service rather than by
 * driving it: what broke was state outliving a scan, and that is what is asserted here.
 */
class WarehouseServiceTest : DescribeSpec({

    beforeSpec { FakePlatform.withMineColonies() }

    val warehouseHut = BlockPos(10, 64, 20)

    /** A warehouse building, as MineColonies reports one. */
    class FakeWarehouseBuilding(private val pos: BlockPos = BlockPos(10, 64, 20)) {
        fun getID(): BlockPos = pos
        fun getBuildingType(): Any = object { fun getRegistryName(): String = "minecolonies:warehouse" }
        fun getBuildingLevel(): Int = 3
        fun getContainers(): Collection<BlockPos> = listOf(pos.east(), pos.west())
    }

    fun buildingsOf(vararg raw: Any) = BuildingService().scan(raw.toList(), null)

    describe("carrying nothing between scans") {
        // This is the bug. The service is a singleton, so anything it keeps in a field it keeps
        // for the lifetime of the server — across every later scan and every other colony. Both
        // of the fields it used to hold were per-scan bookkeeping, and keeping them made the
        // first scan the only one that ever counted anything.
        it("holds no state of its own, so one scan cannot blind the next") {
            val ownState = WarehouseService::class.java.declaredFields
                .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
                .map { it.name }

            ownState.shouldBeEmpty()
        }
    }

    describe("finding the warehouse") {
        it("reports the warehouse as present") {
            val warehouse = ColonySnapshot.Warehouse()

            WarehouseService().scan(emptyList(), buildingsOf(FakeWarehouseBuilding()), null, warehouse)

            warehouse.present shouldBe true
        }

        it("reports the warehouse as present on every scan, not just the first") {
            val service = WarehouseService()
            val buildings = buildingsOf(FakeWarehouseBuilding())

            repeat(3) {
                val warehouse = ColonySnapshot.Warehouse()
                service.scan(emptyList(), buildings, null, warehouse)
                warehouse.present shouldBe true
            }
        }

        it("fills in the warehouse it was handed, not one from an earlier scan") {
            val service = WarehouseService()
            val buildings = buildingsOf(FakeWarehouseBuilding())
            val first = ColonySnapshot.Warehouse()
            val second = ColonySnapshot.Warehouse()

            service.scan(emptyList(), buildings, null, first)
            service.scan(emptyList(), buildings, null, second)

            first.present shouldBe true
            second.present shouldBe true
        }

        it("reports no warehouse for a colony that has not built one") {
            val warehouse = ColonySnapshot.Warehouse()

            WarehouseService().scan(
                emptyList(),
                buildingsOf(FakeBuilding(warehouseHut, type = "minecolonies:builder")),
                null,
                warehouse
            )

            warehouse.present shouldBe false
            warehouse.stacks.shouldBeEmpty()
        }

        it("reports no warehouse for a colony with no buildings at all") {
            val warehouse = ColonySnapshot.Warehouse()

            WarehouseService().scan(emptyList(), buildingsOf(), null, warehouse)

            warehouse.present shouldBe false
        }

        // The dimension a colony sits in is not always loaded when the scan runs.
        it("does not throw when the world is unavailable, and reports no stock") {
            val warehouse = ColonySnapshot.Warehouse()

            WarehouseService().scan(emptyList(), buildingsOf(FakeWarehouseBuilding()), null, warehouse)

            warehouse.stacks.shouldBeEmpty()
        }
    }

    describe("countIn") {
        it("totals every stack of one item across the warehouse") {
            val snapshot = ColonySnapshot()
            snapshot.warehouse.stacks.add(ColonySnapshot.Stack(count = 30).apply { itemKey = "minecraft:stone" })
            snapshot.warehouse.stacks.add(ColonySnapshot.Stack(count = 12).apply { itemKey = "minecraft:stone" })
            snapshot.warehouse.stacks.add(ColonySnapshot.Stack(count = 5).apply { itemKey = "minecraft:oak_log" })

            WarehouseService.countIn(snapshot, "minecraft:stone") shouldBe 42
        }

        it("totals zero for an item the warehouse does not hold") {
            WarehouseService.countIn(ColonySnapshot(), "minecraft:stone") shouldBe 0
        }
    }
})
