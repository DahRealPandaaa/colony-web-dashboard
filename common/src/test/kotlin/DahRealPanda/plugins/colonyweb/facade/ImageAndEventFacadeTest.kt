package DahRealPanda.plugins.colonyweb.facade

import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.service.ColonyMapService
import DahRealPanda.plugins.colonyweb.service.SseEvent
import DahRealPanda.plugins.colonyweb.service.SseService
import DahRealPanda.plugins.colonyweb.service.TextureService
import DahRealPanda.plugins.colonyweb.support.ConfigReset
import DahRealPanda.plugins.colonyweb.support.withTempDir
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.UUID

/**
 * The image and event endpoints are guarded the same way the JSON ones are, and they are the easy
 * ones to leave open by accident: a map PNG shows a colony's whole layout, and an unguarded SSE
 * stream leaks every colony's updates to anyone holding the connection.
 */
class ImageAndEventFacadeTest : DescribeSpec({

    val ann = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    beforeTest { ConfigReset.applyDefaults() }

    describe("MapFacade") {
        it("serves the map of a colony the player belongs to") {
            withTempDir { dir ->
                val maps = mockk<ColonyMapService> { every { png(1) } returns png }
                val auth = AuthService(dir)
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                MapFacade(maps, auth).getMapImage(1, token) shouldBe png
            }
        }

        it("refuses the map of a colony the player does not belong to") {
            withTempDir { dir ->
                val maps = mockk<ColonyMapService> { every { png(any()) } returns png }
                val auth = AuthService(dir)
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                MapFacade(maps, auth).getMapImage(2, token).shouldBeNull()
            }
        }

        it("refuses the map to a caller with no session") {
            withTempDir { dir ->
                val maps = mockk<ColonyMapService> { every { png(any()) } returns png }
                val auth = AuthService(dir)

                MapFacade(maps, auth).getMapImage(1, null).shouldBeNull()
                MapFacade(maps, auth).getMapImage(1, "not-a-token").shouldBeNull()
            }
        }

        it("serves the map to anyone when auth is switched off") {
            withTempDir { dir ->
                val maps = mockk<ColonyMapService> { every { png(1) } returns png }
                val auth = AuthService(dir)
                Config.authEnabled = false

                MapFacade(maps, auth).getMapImage(1, null) shouldBe png
            }
        }

        it("returns nothing when the map has not been drawn yet") {
            withTempDir { dir ->
                val maps = mockk<ColonyMapService> { every { png(any()) } returns null }
                val auth = AuthService(dir)
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                MapFacade(maps, auth).getMapImage(1, token).shouldBeNull()
            }
        }
    }

    // Item icons carry no colony data at all — they are the same for every server running the
    // same mods — so this endpoint is deliberately unauthenticated, and the cache lookup is all
    // there is to it.
    describe("TextureFacade") {
        it("serves the icon for a known item") {
            val textures = mockk<TextureService> { every { getPng("minecraft:stone") } returns png }

            TextureFacade(textures).getTexture("minecraft:stone") shouldBe png
        }

        it("passes an unknown key straight through to the renderer's own fallback") {
            val textures = mockk<TextureService> { every { getPng(any()) } returns ByteArray(0) }

            TextureFacade(textures).getTexture("nonsense:not-an-item")!!.size shouldBe 0
        }
    }

    describe("EventsFacade") {
        it("delivers broadcasts to a subscriber") {
            withTempDir { dir ->
                val sse = SseService()
                val facade = EventsFacade(sse, AuthService(dir))
                val received = mutableListOf<SseEvent>()

                facade.subscribe { received.add(it) }
                sse.broadcast("{\"type\":\"colony\"}")

                received.map { it.data } shouldBe listOf("{\"type\":\"colony\"}")
            }
        }

        it("stops delivering once the browser has gone") {
            withTempDir { dir ->
                val sse = SseService()
                val facade = EventsFacade(sse, AuthService(dir))
                val received = mutableListOf<SseEvent>()

                val unsubscribe = facade.subscribe { received.add(it) }
                unsubscribe()
                sse.broadcast("{}")

                received.size shouldBe 0
            }
        }

        it("identifies the player behind a live token") {
            withTempDir { dir ->
                val auth = AuthService(dir)
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                EventsFacade(SseService(), auth).authenticate(token)!!.name shouldBe "Ann"
            }
        }

        it("identifies nobody behind a missing or unknown token") {
            withTempDir { dir ->
                val facade = EventsFacade(SseService(), AuthService(dir))

                facade.authenticate(null).shouldBeNull()
                facade.authenticate("not-a-token").shouldBeNull()
            }
        }

        // The router uses isAuthEnabled to decide whether the null from authenticate means
        // "rejected" or "there is nothing to reject", so the two have to agree.
        it("reports auth being off, and identifies nobody while it is") {
            withTempDir { dir ->
                val auth = AuthService(dir)
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!
                val facade = EventsFacade(SseService(), auth)
                Config.authEnabled = false

                facade.isAuthEnabled() shouldBe false
                facade.authenticate(token).shouldBeNull()
            }
        }
    }
})
