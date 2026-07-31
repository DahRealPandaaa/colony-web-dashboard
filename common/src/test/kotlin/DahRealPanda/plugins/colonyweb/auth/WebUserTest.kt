package DahRealPanda.plugins.colonyweb.auth

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * [WebUser] keeps the two sources of access apart on purpose: the colonies mirrored from
 * MineColonies, and the ones an operator granted by hand. The union of the two is what the auth
 * guard actually checks, so it is worth testing on its own.
 */
class WebUserTest : DescribeSpec({

    fun sessionExpiringAt(expiresAt: Long) = StoredSession("hash-$expiresAt", 0L, expiresAt)

    describe("accessibleColonies") {
        it("combines mirrored and granted colonies") {
            val user = WebUser(colonies = mutableSetOf(1, 2), granted = mutableSetOf(7))

            user.accessibleColonies() shouldContainExactlyInAnyOrder listOf(1, 2, 7)
        }

        it("counts a colony that appears in both lists once") {
            val user = WebUser(colonies = mutableSetOf(1, 2), granted = mutableSetOf(2))

            user.accessibleColonies() shouldContainExactlyInAnyOrder listOf(1, 2)
        }

        it("is empty for a player with no access at all") {
            WebUser().accessibleColonies() shouldHaveSize 0
        }

        it("works when only one of the two sources has anything in it") {
            WebUser(granted = mutableSetOf(7)).accessibleColonies() shouldContainExactlyInAnyOrder listOf(7)
            WebUser(colonies = mutableSetOf(1)).accessibleColonies() shouldContainExactlyInAnyOrder listOf(1)
        }

        // The guard reads this on every request, so it must not be a live view onto the sets it
        // was built from — a caller mutating the result must not silently widen the player's access.
        it("returns a snapshot rather than a live view of the underlying sets") {
            val user = WebUser(colonies = mutableSetOf(1))
            val accessible = user.accessibleColonies()

            user.colonies.add(2)

            accessible shouldContainExactlyInAnyOrder listOf(1)
        }
    }

    describe("purgeExpiredSessions") {
        it("drops a session that has expired and says so") {
            val user = WebUser(sessions = mutableListOf(sessionExpiringAt(50L)))

            user.purgeExpiredSessions(100L) shouldBe true
            user.sessions shouldHaveSize 0
        }

        it("keeps a session that is still live and reports no change") {
            val user = WebUser(sessions = mutableListOf(sessionExpiringAt(500L)))

            user.purgeExpiredSessions(100L) shouldBe false
            user.sessions shouldHaveSize 1
        }

        it("drops only the expired sessions of a player with several browsers") {
            val user = WebUser(sessions = mutableListOf(sessionExpiringAt(50L), sessionExpiringAt(500L)))

            user.purgeExpiredSessions(100L) shouldBe true
            user.sessions.map { it.expiresAt } shouldContainExactlyInAnyOrder listOf(500L)
        }

        it("treats a session expiring exactly now as expired") {
            val user = WebUser(sessions = mutableListOf(sessionExpiringAt(100L)))

            user.purgeExpiredSessions(100L) shouldBe true
        }

        it("reports no change for a player who has never signed in") {
            WebUser().purgeExpiredSessions(100L) shouldBe false
        }
    }
})
