package DahRealPanda.plugins.colonyweb.facade

import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.model.CitizenInfo
import DahRealPanda.plugins.colonyweb.model.ColonySnapshot
import DahRealPanda.plugins.colonyweb.model.ColonySummary
import DahRealPanda.plugins.colonyweb.model.CombatInfo
import DahRealPanda.plugins.colonyweb.model.ItemCount
import DahRealPanda.plugins.colonyweb.model.MapInfo
import DahRealPanda.plugins.colonyweb.model.ResearchInfo
import DahRealPanda.plugins.colonyweb.service.BuildingService
import DahRealPanda.plugins.colonyweb.service.CitizenService
import DahRealPanda.plugins.colonyweb.service.ColonyMapService
import DahRealPanda.plugins.colonyweb.service.CombatService
import DahRealPanda.plugins.colonyweb.service.ResearchService
import DahRealPanda.plugins.colonyweb.support.ConfigReset
import DahRealPanda.plugins.colonyweb.support.withTempDir
import com.google.gson.JsonParser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Path
import java.util.UUID

/**
 * [ColonyFacade] is the authorisation boundary for every colony endpoint. The interesting cases
 * are the refusals: a request for a colony the player does not belong to has to come back looking
 * exactly like a request for a colony that does not exist, or the dashboard becomes a way to
 * enumerate the server's colonies.
 */
class ColonyFacadeTest : DescribeSpec({

    val ann = UUID.fromString("00000000-0000-0000-0000-0000000000a1")

    beforeTest { ConfigReset.applyDefaults() }

    class Fixture(dir: Path) {
        val buildings = BuildingService()
        val citizens = CitizenService()
        val combat = CombatService()
        val research = ResearchService()
        val maps = mockk<ColonyMapService>()
        val auth = AuthService(dir)
        val facade = ColonyFacade(buildings, citizens, combat, research, maps, auth)

        /** A colony Ann belongs to (1) and one she does not (2). */
        fun withTwoColonies() {
            buildings.setSummaries(listOf(summary(1, "Ann's Colony"), summary(2, "Somebody Else's")))
            buildings.storeSnapshot(1, ColonySnapshot(id = 1, name = "Ann's Colony"))
            buildings.storeSnapshot(2, ColonySnapshot(id = 2, name = "Somebody Else's"))
            citizens.storeCitizens(1, listOf(CitizenInfo(id = 5, name = "Ann")))
            citizens.storeCitizens(2, listOf(CitizenInfo(id = 6, name = "Bob")))
            citizens.storeInventories(1, mapOf(5 to listOf(ItemCount().apply { itemKey = "minecraft:stone" })))
            combat.store(1, CombatInfo(guardCount = 3))
            combat.store(2, CombatInfo(guardCount = 9))
            research.store(1, ResearchInfo())
            research.store(2, ResearchInfo())
            every { maps.info(any()) } returns MapInfo(available = true)
        }

        fun tokenForAnn(colonies: List<Int>): String =
            auth.redeemCode(auth.issueCode(ann, "Ann", colonies, false))!!

        private fun summary(id: Int, name: String) =
            ColonySummary(id, name, "minecraft:overworld", "Ann", 0, 0, 0, 1, 1, 0)
    }

    fun fixture(dir: Path) = Fixture(dir).apply { withTwoColonies() }

    describe("listColonies") {
        it("lists only the colonies the player belongs to") {
            withTempDir { dir ->
                val f = fixture(dir)
                val token = f.tokenForAnn(listOf(1))

                val ids = JsonParser.parseString(f.facade.listColonies(token)).asJsonArray
                    .map { it.asJsonObject["id"].asInt }

                ids shouldBe listOf(1)
            }
        }

        it("lists nothing for a caller with no session") {
            withTempDir { dir ->
                val f = fixture(dir)

                f.facade.listColonies(null) shouldBe "[]"
                f.facade.listColonies("not-a-token") shouldBe "[]"
            }
        }

        it("lists every colony for an operator") {
            withTempDir { dir ->
                val f = fixture(dir)
                val token = f.auth.redeemCode(f.auth.issueCode(ann, "Ann", emptyList(), true))!!

                JsonParser.parseString(f.facade.listColonies(token)).asJsonArray.size() shouldBe 2
            }
        }

        it("lists every colony to everyone when auth is switched off") {
            withTempDir { dir ->
                val f = fixture(dir)
                Config.authEnabled = false

                JsonParser.parseString(f.facade.listColonies(null)).asJsonArray.size() shouldBe 2
            }
        }

        it("lists nothing when no colony has been scanned yet") {
            withTempDir { dir ->
                val f = Fixture(dir)
                Config.authEnabled = false

                f.facade.listColonies(null) shouldBe "[]"
            }
        }
    }

    describe("reading one colony") {
        it("returns the snapshot of a colony the player belongs to") {
            withTempDir { dir ->
                val f = fixture(dir)
                val token = f.tokenForAnn(listOf(1))

                JsonParser.parseString(f.facade.snapshot(1, token)).asJsonObject["name"].asString shouldBe
                        "Ann's Colony"
            }
        }

        // Every refusal below returns the same empty document a missing colony would, so a caller
        // cannot tell "not yours" from "does not exist".
        it("refuses a colony the player does not belong to") {
            withTempDir { dir ->
                val f = fixture(dir)
                val token = f.tokenForAnn(listOf(1))

                f.facade.snapshot(2, token) shouldBe "{}"
                f.facade.citizens(2, token) shouldBe "[]"
                f.facade.combat(2, token) shouldBe "{}"
                f.facade.research(2, token) shouldBe "{}"
                f.facade.mapInfo(2, token) shouldBe "{}"
                f.facade.citizenDetail(2, 6, token) shouldBe "{}"
            }
        }

        it("returns the same empty document for a colony that does not exist") {
            withTempDir { dir ->
                val f = fixture(dir)
                val token = f.auth.redeemCode(f.auth.issueCode(ann, "Ann", emptyList(), true))!!

                f.facade.snapshot(99, token) shouldBe "{}"
                f.facade.citizens(99, token) shouldBe "[]"
                f.facade.combat(99, token) shouldBe "{}"
                f.facade.research(99, token) shouldBe "{}"
            }
        }

        it("refuses everything to a caller with no session") {
            withTempDir { dir ->
                val f = fixture(dir)

                f.facade.snapshot(1, null) shouldBe "{}"
                f.facade.citizens(1, null) shouldBe "[]"
                f.facade.combat(1, null) shouldBe "{}"
                f.facade.research(1, null) shouldBe "{}"
                f.facade.mapInfo(1, null) shouldBe "{}"
            }
        }

        it("refuses a token that has been revoked") {
            withTempDir { dir ->
                val f = fixture(dir)
                val token = f.tokenForAnn(listOf(1))
                f.auth.revokeToken(token)

                f.facade.snapshot(1, token) shouldBe "{}"
            }
        }

        it("serves a colony granted by an operator even though the player is not a member") {
            withTempDir { dir ->
                val f = fixture(dir)
                val token = f.tokenForAnn(listOf(1))
                f.auth.grant(ann, "Ann", 2)

                JsonParser.parseString(f.facade.snapshot(2, token)).asJsonObject["id"].asInt shouldBe 2
            }
        }

        it("serves everything to everyone when auth is switched off") {
            withTempDir { dir ->
                val f = fixture(dir)
                Config.authEnabled = false

                JsonParser.parseString(f.facade.snapshot(2, null)).asJsonObject["id"].asInt shouldBe 2
                JsonParser.parseString(f.facade.combat(2, null)).asJsonObject["guardCount"].asInt shouldBe 9
            }
        }
    }

    describe("citizenDetail") {
        it("returns the citizen together with their inventory and equipment") {
            withTempDir { dir ->
                val f = fixture(dir)
                val token = f.tokenForAnn(listOf(1))

                val detail = JsonParser.parseString(f.facade.citizenDetail(1, 5, token)).asJsonObject

                detail["citizen"].asJsonObject["name"].asString shouldBe "Ann"
                detail["inventory"].asJsonArray.size() shouldBe 1
                detail["equipment"].asJsonArray.size() shouldBe 0
            }
        }

        it("returns nothing for a citizen id that is not in this colony") {
            withTempDir { dir ->
                val f = fixture(dir)
                val token = f.tokenForAnn(listOf(1))

                f.facade.citizenDetail(1, 6, token) shouldBe "{}"
                f.facade.citizenDetail(1, -1, token) shouldBe "{}"
            }
        }
    }
})
