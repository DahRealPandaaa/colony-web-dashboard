package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.BuildingInfo
import DahRealPanda.plugins.colonyweb.model.CitizenInfo
import DahRealPanda.plugins.colonyweb.model.ColonyScan
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.CombatInfo
import DahRealPanda.plugins.colonyweb.model.ResourceEntry
import DahRealPanda.plugins.colonyweb.model.WorkOrderInfo
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * [ScanHasher] decides whether a scan is worth pushing to every connected browser. A hash that
 * misses a change leaves the dashboard stale; one that changes when nothing did turns a colony
 * with wandering citizens into a permanent broadcast storm. Both directions are tested here.
 */
class ScanHasherTest : DescribeSpec({

    fun scanOf(build: ColonyScan.() -> Unit = {}): ColonyScan {
        val scan = ColonyScan()
        scan.snapshot = ColonySnapshot(id = 1, name = "Test Colony")
        scan.build()
        return scan
    }

    fun buildingScan(build: BuildingInfo.() -> Unit = {}): ColonyScan = scanOf {
        snapshot.buildings.add(BuildingInfo(id = 100, name = "Builder", level = 3).apply(build))
    }

    fun citizenScan(build: CitizenInfo.() -> Unit = {}): ColonyScan = scanOf {
        citizens = listOf(CitizenInfo(id = 7, name = "Ann", jobType = "minecolonies:builder").apply(build))
    }

    describe("stability") {
        it("gives two identical empty scans the same hash") {
            ScanHasher.hash(scanOf()) shouldBe ScanHasher.hash(scanOf())
        }

        it("gives two identically populated scans the same hash") {
            ScanHasher.hash(buildingScan()) shouldBe ScanHasher.hash(buildingScan())
        }

        it("is stable across repeated calls on the same scan") {
            val scan = buildingScan()
            ScanHasher.hash(scan) shouldBe ScanHasher.hash(scan)
        }
    }

    describe("changes it must notice") {
        it("notices a building that levelled up") {
            ScanHasher.hash(buildingScan()) shouldNotBe ScanHasher.hash(buildingScan { level = 4 })
        }

        it("notices a building that started being built") {
            ScanHasher.hash(buildingScan()) shouldNotBe ScanHasher.hash(buildingScan { beingBuilt = true })
        }

        it("notices a change in what a building still needs") {
            val before = buildingScan {
                required.add(ResourceEntry().apply { itemKey = "minecraft:oak_planks"; needed = 64 })
            }
            val after = buildingScan {
                required.add(ResourceEntry().apply { itemKey = "minecraft:oak_planks"; needed = 32 })
            }
            ScanHasher.hash(before) shouldNotBe ScanHasher.hash(after)
        }

        it("notices work order progress") {
            val before = scanOf { snapshot.workOrders.add(WorkOrderInfo(id = 1, progress = 0.25)) }
            val after = scanOf { snapshot.workOrders.add(WorkOrderInfo(id = 1, progress = 0.75)) }
            ScanHasher.hash(before) shouldNotBe ScanHasher.hash(after)
        }

        it("notices a warehouse stock change") {
            val before = scanOf {
                snapshot.warehouse.stacks.add(ColonySnapshot.Stack(count = 10).apply { itemKey = "minecraft:stone" })
            }
            val after = scanOf {
                snapshot.warehouse.stacks.add(ColonySnapshot.Stack(count = 11).apply { itemKey = "minecraft:stone" })
            }
            ScanHasher.hash(before) shouldNotBe ScanHasher.hash(after)
        }

        it("notices a citizen changing job") {
            ScanHasher.hash(citizenScan()) shouldNotBe
                    ScanHasher.hash(citizenScan { jobType = "minecolonies:farmer" })
        }

        it("notices a citizen picking something up") {
            ScanHasher.hash(citizenScan()) shouldNotBe ScanHasher.hash(citizenScan { inventoryUsed = 1 })
        }

        it("notices a raid starting") {
            val before = scanOf { combat = CombatInfo(underAttack = false) }
            val after = scanOf { combat = CombatInfo(underAttack = true) }
            ScanHasher.hash(before) shouldNotBe ScanHasher.hash(after)
        }

        it("notices research finishing") {
            val before = scanOf()
            val after = scanOf { snapshot.stats.researchCompleted = 1 }
            ScanHasher.hash(before) shouldNotBe ScanHasher.hash(after)
        }
    }

    describe("noise it must ignore") {
        // Citizen coordinates are bucketed into 8-block blocks. Without that, a colony of wandering
        // citizens would produce a new hash on essentially every scan and broadcast continuously.
        it("ignores a citizen shuffling around inside one position bucket") {
            val before = citizenScan { x = 80; z = 80 }
            val after = citizenScan { x = 87; z = 87 }
            ScanHasher.hash(before) shouldBe ScanHasher.hash(after)
        }

        it("notices a citizen crossing into the next bucket") {
            val before = citizenScan { x = 80; z = 80 }
            val after = citizenScan { x = 88; z = 80 }
            ScanHasher.hash(before) shouldNotBe ScanHasher.hash(after)
        }

        it("buckets negative coordinates too, rather than flipping on the sign") {
            val before = citizenScan { x = -8; z = -8 }
            val after = citizenScan { x = -3; z = -3 }
            ScanHasher.hash(before) shouldBe ScanHasher.hash(after)
        }

        // Health is compared as a whole number and happiness to one decimal, so regeneration
        // ticking a fraction at a time does not count as a change.
        it("ignores a fractional health tick") {
            ScanHasher.hash(citizenScan { health = 19.2 }) shouldBe
                    ScanHasher.hash(citizenScan { health = 19.8 })
        }

        it("ignores a happiness change too small to render") {
            ScanHasher.hash(citizenScan { happiness = 7.51 }) shouldBe
                    ScanHasher.hash(citizenScan { happiness = 7.55 })
        }

        it("ignores the citizen's display name, which no scan can change on its own") {
            ScanHasher.hash(citizenScan()) shouldBe ScanHasher.hash(citizenScan { name = "Bob" })
        }
    }

    describe("empty and partial scans") {
        it("hashes a scan with no buildings, citizens or warehouse without throwing") {
            ScanHasher.hash(scanOf()) shouldNotBe 0
        }

        it("hashes a scan whose research was not refreshed this pass") {
            val scan = scanOf { research = null }
            ScanHasher.hash(scan) shouldBe ScanHasher.hash(scanOf())
        }

        it("tolerates a resource with no item key") {
            val scan = buildingScan { required.add(ResourceEntry().apply { needed = 5 }) }
            ScanHasher.hash(scan) shouldBe ScanHasher.hash(scan)
        }
    }
})
