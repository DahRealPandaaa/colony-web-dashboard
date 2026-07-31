package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.model.CitizenInfo
import DahRealPanda.plugins.colonyweb.model.CombatInfo
import DahRealPanda.plugins.colonyweb.model.ResearchInfo
import DahRealPanda.plugins.colonyweb.support.FakeColony
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Defence and research are the two scans that lean hardest on MineColonies internals — raid
 * managers, grave handlers, the global research tree — and the two most likely to move between
 * MineColonies versions. Neither is allowed to take the rest of the scan down when it does, so
 * what is pinned here is that a colony they cannot read comes back empty rather than throwing.
 */
class CombatAndResearchScanTest : DescribeSpec({

    describe("CombatService") {
        it("returns an empty defence report for a colony it cannot read") {
            val info = CombatService().scan(FakeColony(), emptyList(), emptyMap(), emptyMap(), emptyMap())

            info.guardCount shouldBe 0
            info.underAttack shouldBe false
            info.guards.shouldBeEmpty()
            info.posts.shouldBeEmpty()
            info.events.shouldBeEmpty()
        }

        it("returns an empty defence report rather than throwing when the colony itself explodes") {
            val hostile = object {
                fun getRaiderManager(): Any = throw IllegalStateException("not this version")
                fun getEventManager(): Any = throw IllegalStateException("not this version")
                fun getGraveManager(): Any = throw IllegalStateException("not this version")
            }

            CombatService().scan(hostile, emptyList(), emptyMap(), emptyMap(), emptyMap()).guardCount shouldBe 0
        }

        it("reports no guards for a colony whose citizens all have civilian jobs") {
            val citizens = listOf(
                CitizenInfo(id = 1, name = "Ann", jobType = "minecolonies:builder"),
                CitizenInfo(id = 2, name = "Bob", jobType = "minecolonies:farmer")
            )

            CombatService().scan(FakeColony(), citizens, emptyMap(), emptyMap(), emptyMap())
                .guards.shouldBeEmpty()
        }

        it("serves back a stored report and forgets colonies that are gone") {
            val service = CombatService()
            service.store(1, CombatInfo(guardCount = 4))
            service.store(2, CombatInfo(guardCount = 9))

            service.combat(1).shouldNotBeNull().guardCount shouldBe 4
            service.combat(99).shouldBeNull()

            service.retainOnly(listOf(1))

            service.combat(1).shouldNotBeNull()
            service.combat(2).shouldBeNull()
        }
    }

    describe("ResearchService") {
        it("reports research as unavailable when there is no research tree to read") {
            val info = ResearchService().scan(FakeColony())

            info.available shouldBe false
            info.branches.shouldBeEmpty()
            info.completed shouldBe 0
            info.inProgress shouldBe 0
        }

        it("serves back a stored tree and forgets colonies that are gone") {
            val service = ResearchService()
            service.store(1, ResearchInfo(completed = 3, available = true))
            service.store(2, ResearchInfo(completed = 1, available = true))

            service.research(1).shouldNotBeNull().completed shouldBe 3
            service.research(99).shouldBeNull()

            service.retainOnly(listOf(1))

            service.research(1).shouldNotBeNull()
            service.research(2).shouldBeNull()
        }
    }
})
