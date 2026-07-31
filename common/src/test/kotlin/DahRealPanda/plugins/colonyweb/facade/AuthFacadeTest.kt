package DahRealPanda.plugins.colonyweb.facade

import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.support.ConfigReset
import DahRealPanda.plugins.colonyweb.support.withTempDir
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import java.util.UUID

/**
 * [AuthFacade] is what the auth endpoints actually call. Its job is to turn the auth
 * service's yes/no into the response shape the dashboard expects — including deciding when a
 * token is handed back to be written into a cookie, which is the one thing that must never
 * happen on a failed login.
 */
class AuthFacadeTest : DescribeSpec({

    val ann = UUID.fromString("00000000-0000-0000-0000-0000000000a1")

    beforeTest { ConfigReset.applyDefaults() }

    fun facadeIn(dir: Path): Pair<AuthFacade, AuthService> {
        val auth = AuthService(dir)
        return AuthFacade(auth) to auth
    }

    describe("checkSession") {
        it("reports an authenticated user and the colonies they may see") {
            withTempDir { dir ->
                val (facade, auth) = facadeIn(dir)
                auth.issueCode(ann, "Ann", listOf(1, 2), false)
                auth.grant(ann, "Ann", 7)
                val token = auth.redeemCode(auth.issueCode(ann, "Ann", listOf(1, 2), false))!!

                val session = facade.checkSession(token)

                session.authenticated shouldBe true
                session.authEnabled shouldBe true
                session.user.shouldNotBeNull().name shouldBe "Ann"
                session.user!!.colonies shouldContainExactlyInAnyOrder listOf(1, 2, 7)
                session.user!!.granted shouldContainExactlyInAnyOrder listOf(7)
            }
        }

        it("reports an unknown token as not signed in, and sends no user with it") {
            withTempDir { dir ->
                val (facade, _) = facadeIn(dir)

                val session = facade.checkSession("not-a-token")

                session.authenticated shouldBe false
                session.authEnabled shouldBe true
                session.user.shouldBeNull()
            }
        }

        it("reports a missing token as not signed in") {
            withTempDir { dir ->
                facadeIn(dir).first.checkSession(null).authenticated shouldBe false
            }
        }

        // With auth off there is nothing to sign in to, so the browser is told the login screen
        // does not apply rather than being told it failed to sign in.
        it("tells the browser auth is off entirely, rather than that it is signed out") {
            withTempDir { dir ->
                val (facade, _) = facadeIn(dir)
                Config.authEnabled = false

                val session = facade.checkSession(null)

                session.authEnabled shouldBe false
                session.authenticated shouldBe false
                session.user.shouldBeNull()
            }
        }
    }

    describe("login") {
        it("returns a token and the signed-in user for a valid code") {
            withTempDir { dir ->
                val (facade, auth) = facadeIn(dir)
                val code = auth.issueCode(ann, "Ann", listOf(1), false)

                val result = facade.login(code)

                result.token.shouldNotBeNull()
                result.response.authenticated shouldBe true
                result.response.user.shouldNotBeNull().name shouldBe "Ann"
                result.response.error.shouldBeNull()
            }
        }

        it("hands back a token that immediately works as a session") {
            withTempDir { dir ->
                val (facade, auth) = facadeIn(dir)
                val token = facade.login(auth.issueCode(ann, "Ann", listOf(1), false)).token

                facade.isEnabled() shouldBe true
                facade.checkSession(token).authenticated shouldBe true
            }
        }

        // No token means no `Set-Cookie`. A failed login that still returned one would leave the
        // browser holding a credential the server does not recognise.
        it("returns no token for a code that was never issued") {
            withTempDir { dir ->
                val result = facadeIn(dir).first.login("ZZZZ-ZZZZ")

                result.token.shouldBeNull()
                result.response.authenticated shouldBe false
                result.response.error.shouldNotBeNull()
                result.response.user.shouldBeNull()
            }
        }

        it("returns no token for an empty code") {
            withTempDir { dir ->
                facadeIn(dir).first.login("").token.shouldBeNull()
            }
        }

        it("returns no token for a code that has already been used") {
            withTempDir { dir ->
                val (facade, auth) = facadeIn(dir)
                val code = auth.issueCode(ann, "Ann", listOf(1), false)
                facade.login(code)

                facade.login(code).token.shouldBeNull()
            }
        }

        it("refuses to issue a session while auth is disabled") {
            withTempDir { dir ->
                val (facade, auth) = facadeIn(dir)
                val code = auth.issueCode(ann, "Ann", listOf(1), false)
                Config.authEnabled = false

                val result = facade.login(code)

                result.token.shouldBeNull()
                result.response.authenticated shouldBe false
                result.response.error shouldBe "Auth disabled"
            }
        }
    }

    describe("logout") {
        it("ends the session the token belongs to") {
            withTempDir { dir ->
                val (facade, auth) = facadeIn(dir)
                val token = facade.login(auth.issueCode(ann, "Ann", listOf(1), false)).token!!

                facade.logout(token)

                facade.checkSession(token).authenticated shouldBe false
            }
        }

        it("does nothing at all for a missing or unknown token") {
            withTempDir { dir ->
                val (facade, auth) = facadeIn(dir)
                val token = facade.login(auth.issueCode(ann, "Ann", listOf(1), false)).token!!

                facade.logout(null)
                facade.logout("not-a-token")

                facade.checkSession(token).authenticated shouldBe true
            }
        }
    }

    describe("userForToken") {
        it("resolves a live token to its account") {
            withTempDir { dir ->
                val (facade, auth) = facadeIn(dir)
                val token = facade.login(auth.issueCode(ann, "Ann", listOf(1), false)).token!!

                facade.userForToken(token).shouldNotBeNull().name shouldBe "Ann"
            }
        }

        it("resolves nothing for an unknown token") {
            withTempDir { dir ->
                facadeIn(dir).first.userForToken("not-a-token").shouldBeNull()
            }
        }

        // With auth off there are no accounts to resolve to, and callers must not treat the null
        // as "denied" — the access checks stay open on their own.
        it("resolves nothing while auth is disabled, even for a token that was valid") {
            withTempDir { dir ->
                val (facade, auth) = facadeIn(dir)
                val token = facade.login(auth.issueCode(ann, "Ann", listOf(1), false)).token!!
                Config.authEnabled = false

                facade.userForToken(token).shouldBeNull()
                facade.isEnabled() shouldBe false
            }
        }
    }
})
