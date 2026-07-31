package DahRealPanda.plugins.colonyweb.auth

import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.support.ConfigReset
import DahRealPanda.plugins.colonyweb.support.withTempDir
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * [AuthService] is the only thing standing between a colony's data and anyone who can reach the
 * port, so both halves matter: the codes and sessions it accepts, and — more importantly — the
 * ones it refuses.
 */
class AuthServiceTest : DescribeSpec({

    val ann = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    val bob = UUID.fromString("00000000-0000-0000-0000-0000000000b2")

    beforeTest { ConfigReset.applyDefaults() }

    fun authIn(dir: Path) = AuthService(dir)

    describe("issuing a pairing code") {
        it("produces a code in the two-groups-of-four shape the dashboard asks for") {
            withTempDir { dir ->
                authIn(dir).issueCode(ann, "Ann", listOf(1), false) shouldMatch Regex("[A-Z0-9]{4}-[A-Z0-9]{4}")
            }
        }

        // The player reads this code off chat and types it into a browser, so anything that can be
        // confused for something else — vowels that spell words, 0/O, 1/I — is left out.
        it("uses no vowels and no look-alike characters") {
            withTempDir { dir ->
                val auth = authIn(dir)
                repeat(200) {
                    val code = auth.issueCode(ann, "Ann", listOf(1), false).replace("-", "")
                    code.forEach { character ->
                        ("23456789BCDFGHJKLMNPQRSTVWXZ".contains(character)) shouldBe true
                    }
                }
            }
        }

        it("issues a different code each time") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val codes = (1..50).map { auth.issueCode(ann, "Ann", listOf(1), false) }
                codes.toSet() shouldHaveSize codes.size
            }
        }

        it("invalidates the player's previous code, so only the newest one works") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val first = auth.issueCode(ann, "Ann", listOf(1), false)
                val second = auth.issueCode(ann, "Ann", listOf(1), false)

                auth.redeemCode(first) shouldBe null
                auth.redeemCode(second).shouldNotBeNull()
            }
        }

        it("does not invalidate another player's pending code") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val annsCode = auth.issueCode(ann, "Ann", listOf(1), false)
                auth.issueCode(bob, "Bob", listOf(2), false)

                auth.redeemCode(annsCode).shouldNotBeNull()
            }
        }

        it("records the colonies, admin flag and current name on the account") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.issueCode(ann, "Ann", listOf(1, 2), true)

                val user = auth.user(ann).shouldNotBeNull()
                user.name shouldBe "Ann"
                user.admin shouldBe true
                user.colonies shouldContainExactlyInAnyOrder listOf(1, 2)
                (user.syncedAt > 0) shouldBe true
            }
        }

        it("replaces the colony list on re-sync, so leaving a colony revokes access to it") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.issueCode(ann, "Ann", listOf(1, 2), false)
                auth.issueCode(ann, "Ann", listOf(2), false)

                auth.user(ann)!!.colonies shouldContainExactlyInAnyOrder listOf(2)
            }
        }

        it("picks up a rename on the next sync") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.issueCode(ann, "Ann", listOf(1), false)
                auth.issueCode(ann, "Annabel", listOf(1), false)

                auth.user(ann)!!.name shouldBe "Annabel"
            }
        }

        it("treats the same player id in a different case as the same account") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.issueCode(ann, "Ann", listOf(1), false)

                auth.user(UUID.fromString(ann.toString().uppercase())).shouldNotBeNull()
                auth.allUsers() shouldHaveSize 1
            }
        }
    }

    describe("redeeming a pairing code") {
        it("returns a session token for a valid code") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val code = auth.issueCode(ann, "Ann", listOf(1), false)

                val token = auth.redeemCode(code).shouldNotBeNull()
                auth.userForToken(token)!!.uuid shouldBe ann.toString()
            }
        }

        it("accepts the code however the player typed it") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val code = auth.issueCode(ann, "Ann", listOf(1), false)

                auth.redeemCode(code.replace("-", "").lowercase()).shouldNotBeNull()
            }
        }

        it("ignores stray spaces around and inside the code") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val code = auth.issueCode(ann, "Ann", listOf(1), false)

                auth.redeemCode("  ${code.replace("-", " - ")}  ").shouldNotBeNull()
            }
        }

        it("burns the code, so a shoulder-surfer cannot reuse it") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val code = auth.issueCode(ann, "Ann", listOf(1), false)

                auth.redeemCode(code).shouldNotBeNull()
                auth.redeemCode(code) shouldBe null
            }
        }

        it("rejects a code that was never issued") {
            withTempDir { dir ->
                authIn(dir).redeemCode("ZZZZ-ZZZZ") shouldBe null
            }
        }

        it("rejects an empty and a blank code") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.issueCode(ann, "Ann", listOf(1), false)

                auth.redeemCode("") shouldBe null
                auth.redeemCode("   ") shouldBe null
            }
        }

        it("rejects a code that has expired") {
            withTempDir { dir ->
                val auth = authIn(dir)
                Config.loginCodeMinutes = 0
                val code = auth.issueCode(ann, "Ann", listOf(1), false)

                auth.redeemCode(code) shouldBe null
            }
        }

        it("stops counting a code as pending once it has expired") {
            withTempDir { dir ->
                val auth = authIn(dir)
                Config.loginCodeMinutes = 0
                auth.issueCode(ann, "Ann", listOf(1), false)

                auth.pendingCodeCount() shouldBe 0
            }
        }
    }

    describe("sessions") {
        it("resolves a live token to its owner") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                auth.userForToken(token)!!.name shouldBe "Ann"
                auth.sessionCount() shouldBe 1
            }
        }

        it("rejects a token nobody was ever given") {
            withTempDir { dir ->
                authIn(dir).userForToken("deadbeef") shouldBe null
            }
        }

        it("rejects a null and a blank token") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.userForToken(null) shouldBe null
                auth.userForToken("") shouldBe null
                auth.userForToken("   ") shouldBe null
            }
        }

        it("rejects a token whose session has run out") {
            withTempDir { dir ->
                val auth = authIn(dir)
                Config.sessionDays = 0
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                auth.userForToken(token) shouldBe null
            }
        }

        it("does not hand one player's token to another") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val annsToken = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!
                auth.redeemCode(auth.issueCode(bob, "Bob", listOf(2), false))!!

                auth.userForToken(annsToken)!!.name shouldBe "Ann"
            }
        }

        it("logging in twice leaves both browsers signed in") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val first = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!
                val second = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                first shouldNotBe second
                auth.userForToken(first).shouldNotBeNull()
                auth.userForToken(second).shouldNotBeNull()
                auth.sessionCount() shouldBe 2
            }
        }

        it("revoking a token signs out only that browser") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val first = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!
                val second = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                auth.revokeToken(first)

                auth.userForToken(first) shouldBe null
                auth.userForToken(second).shouldNotBeNull()
            }
        }

        it("revoking an unknown, null or blank token is a no-op") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                auth.revokeToken("not-a-token")
                auth.revokeToken(null)
                auth.revokeToken("")

                auth.userForToken(token).shouldNotBeNull()
            }
        }

        it("revoking everything reports how many sessions were dropped") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                auth.revokeAllSessions(ann) shouldBe 2
                auth.userForToken(token) shouldBe null
            }
        }

        it("revoking everything for a player with no account reports nothing") {
            withTempDir { dir ->
                authIn(dir).revokeAllSessions(ann) shouldBe 0
            }
        }

        it("revoking everything also cancels a code that was never redeemed") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val code = auth.issueCode(ann, "Ann", listOf(1), false)

                auth.revokeAllSessions(ann)

                auth.redeemCode(code) shouldBe null
            }
        }

        it("purging drops expired sessions and leaves live ones alone") {
            withTempDir { dir ->
                val auth = authIn(dir)
                Config.sessionDays = 0
                auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))
                Config.sessionDays = 30
                val live = auth.redeemCode(auth.issueCode(bob, "Bob", listOf(2), false))!!

                auth.purgeExpiredSessions()

                auth.sessionCount() shouldBe 1
                auth.userForToken(live).shouldNotBeNull()
            }
        }
    }

    describe("what gets written to disk") {
        it("never stores the session token itself") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                val stored = Files.readString(dir.resolve("auth.json"))
                stored shouldNotContain token
                stored.contains("tokenHash") shouldBe true
            }
        }

        it("keeps accounts and sessions across a server restart") {
            withTempDir { dir ->
                val auth = authIn(dir)
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1), false))!!

                val reopened = authIn(dir)
                reopened.user(ann).shouldNotBeNull()
                reopened.userForToken(token).shouldNotBeNull()
                reopened.canAccess(reopened.user(ann), 1) shouldBe true
            }
        }

        // Pending codes are deliberately in-memory only: a code is valid for minutes, and a code
        // that outlived the restart it was issued before would be a credential nobody remembers.
        it("forgets pending codes across a restart") {
            withTempDir { dir ->
                val code = authIn(dir).issueCode(ann, "Ann", listOf(1), false)

                authIn(dir).redeemCode(code) shouldBe null
            }
        }
    }

    describe("access control") {
        it("lets a member see their own colony") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.issueCode(ann, "Ann", listOf(1), false)

                auth.canAccess(auth.user(ann), 1) shouldBe true
            }
        }

        it("refuses a colony the player does not belong to") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.issueCode(ann, "Ann", listOf(1), false)

                auth.canAccess(auth.user(ann), 2) shouldBe false
            }
        }

        it("refuses an anonymous visitor while auth is on") {
            withTempDir { dir ->
                authIn(dir).canAccess(null, 1) shouldBe false
            }
        }

        it("lets an operator see every colony") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.issueCode(ann, "Ann", emptyList(), true)

                auth.canAccess(auth.user(ann), 99) shouldBe true
            }
        }

        // With auth off the dashboard is deliberately wide open, and the guard has to say so even
        // for a caller that never signed in.
        it("lets anyone in when auth is switched off entirely") {
            withTempDir { dir ->
                val auth = authIn(dir)
                Config.authEnabled = false

                auth.enabled() shouldBe false
                auth.canAccess(null, 1) shouldBe true
            }
        }

        it("honours a colony an operator granted by hand") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.issueCode(ann, "Ann", listOf(1), false)

                auth.grant(ann, "Ann", 7) shouldBe true
                auth.canAccess(auth.user(ann), 7) shouldBe true
            }
        }

        it("reports a repeated grant as a no-op") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.grant(ann, "Ann", 7) shouldBe true
                auth.grant(ann, "Ann", 7) shouldBe false
            }
        }

        it("can grant to a player who has never signed in") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.grant(ann, "Ann", 7) shouldBe true

                auth.user(ann)!!.name shouldBe "Ann"
            }
        }

        it("takes a granted colony away again") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.grant(ann, "Ann", 7)

                auth.revokeGrant(ann, 7) shouldBe true
                auth.canAccess(auth.user(ann), 7) shouldBe false
            }
        }

        it("reports revoking a grant that was never made") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.grant(ann, "Ann", 7)

                auth.revokeGrant(ann, 8) shouldBe false
                auth.revokeGrant(bob, 7) shouldBe false
            }
        }

        // A grant is the operator saying "yes, this one too". A sync mirrors MineColonies and
        // replaces the mirrored list wholesale, so it must not take the grant with it.
        it("keeps a grant across a re-sync that drops the player's colonies") {
            withTempDir { dir ->
                val auth = authIn(dir)
                auth.issueCode(ann, "Ann", listOf(1), false)
                auth.grant(ann, "Ann", 7)

                auth.issueCode(ann, "Ann", emptyList(), false)

                auth.canAccess(auth.user(ann), 7) shouldBe true
                auth.canAccess(auth.user(ann), 1) shouldBe false
            }
        }
    }
})
