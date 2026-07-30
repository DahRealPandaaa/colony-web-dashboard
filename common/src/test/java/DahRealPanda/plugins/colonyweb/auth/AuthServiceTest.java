package DahRealPanda.plugins.colonyweb.auth;

import DahRealPanda.plugins.colonyweb.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sign-in flow: pairing codes issued in game, exchanged for a session, and the access
 * control that decides which colonies a session may read.
 *
 * <p>Because a code is the only credential in the system, the unhappy paths matter more than
 * the happy one — a code that could be redeemed twice, survive expiry, or be guessed
 * case-by-case would each be a way into somebody else's colony.</p>
 */
class AuthServiceTest {

    @TempDir
    Path dataDir;

    private static final UUID ALICE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BOB = UUID.fromString("66666666-7777-8888-9999-000000000000");

    @BeforeEach
    void configureDefaults() {
        // Config is plain static state filled in by the loader at runtime; tests set it directly.
        Config.authEnabled = true;
        Config.sessionDays = 30;
        Config.loginCodeMinutes = 10;
    }

    private AuthService service() {
        return new AuthService(dataDir);
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("signing in")
    class SigningIn {

        @Test
        @DisplayName("a freshly issued code exchanges for a session that resolves to the player")
        void codeRedeemsToSession() {
            AuthService auth = service();
            String code = auth.issueCode(ALICE, "Alice", List.of(1, 2), false);

            Optional<String> token = auth.redeemCode(code);

            assertTrue(token.isPresent(), "a fresh code should redeem");
            WebUser user = auth.userForToken(token.get()).orElseThrow();
            assertEquals("Alice", user.name);
            assertEquals(Set.of(1, 2), user.accessibleColonies());
        }

        @Test
        @DisplayName("codes are formatted XXXX-XXXX and avoid look-alike characters")
        void codeShape() {
            String code = service().issueCode(ALICE, "Alice", List.of(), false);

            assertTrue(code.matches("[23456789BCDFGHJKLMNPQRSTVWXZ]{4}-[23456789BCDFGHJKLMNPQRSTVWXZ]{4}"),
                    "unexpected code shape: " + code);
        }

        @Test
        @DisplayName("two players get separate sessions")
        void sessionsAreNotShared() {
            AuthService auth = service();
            String aliceToken = auth.redeemCode(auth.issueCode(ALICE, "Alice", List.of(1), false)).orElseThrow();
            String bobToken = auth.redeemCode(auth.issueCode(BOB, "Bob", List.of(2), false)).orElseThrow();

            assertNotEquals(aliceToken, bobToken);
            assertEquals("Alice", auth.userForToken(aliceToken).orElseThrow().name);
            assertEquals("Bob", auth.userForToken(bobToken).orElseThrow().name);
        }

        @Test
        @DisplayName("accounts and sessions survive a restart")
        void persistsAcrossRestart() {
            AuthService before = service();
            String token = before.redeemCode(before.issueCode(ALICE, "Alice", List.of(7), false))
                    .orElseThrow();

            // A second instance over the same data directory is what a server restart looks like.
            AuthService after = service();

            assertEquals("Alice", after.userForToken(token).orElseThrow().name);
            assertEquals(Set.of(7), after.user(ALICE).orElseThrow().accessibleColonies());
        }

        @Test
        @DisplayName("a pending code does not survive a restart")
        void pendingCodesAreNotPersisted() {
            String code = service().issueCode(ALICE, "Alice", List.of(1), false);

            // Deliberate: codes live in memory only, so a restart cancels half-finished logins
            // rather than leaving a credential valid across it.
            assertTrue(service().redeemCode(code).isEmpty());
        }

        @Test
        @DisplayName("the account file is written to the data directory")
        void writesAuthJson() {
            service().issueCode(ALICE, "Alice", List.of(1), false);

            assertTrue(Files.isRegularFile(dataDir.resolve("auth.json")));
        }
    }

    // ------------------------------------------------------------------
    // Codes that must not work
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("rejecting bad codes")
    class RejectingBadCodes {

        @Test
        @DisplayName("a code that was never issued is refused")
        void unknownCode() {
            assertTrue(service().redeemCode("ZZZZ-ZZZZ").isEmpty());
        }

        @Test
        @DisplayName("a code cannot be redeemed twice")
        void singleUse() {
            AuthService auth = service();
            String code = auth.issueCode(ALICE, "Alice", List.of(1), false);

            assertTrue(auth.redeemCode(code).isPresent());
            assertTrue(auth.redeemCode(code).isEmpty(), "the second redemption should fail");
        }

        @Test
        @DisplayName("an expired code is refused")
        void expiredCode() {
            AuthService auth = service();
            // Zero minutes means the code expires the instant it is issued.
            Config.loginCodeMinutes = 0;
            String code = auth.issueCode(ALICE, "Alice", List.of(1), false);

            assertTrue(auth.redeemCode(code).isEmpty());
        }

        @Test
        @DisplayName("re-syncing invalidates the previously issued code")
        void reissueInvalidatesOldCode() {
            AuthService auth = service();
            String first = auth.issueCode(ALICE, "Alice", List.of(1), false);
            String second = auth.issueCode(ALICE, "Alice", List.of(1), false);

            assertTrue(auth.redeemCode(first).isEmpty(), "the superseded code should be dead");
            assertTrue(auth.redeemCode(second).isPresent());
        }

        @ParameterizedTest
        @DisplayName("null, blank and malformed codes are refused rather than throwing")
        @ValueSource(strings = {"", "   ", "----", "not a code", "AAAA-AAAA-AAAA"})
        void malformedCode(String code) {
            assertTrue(service().redeemCode(code).isEmpty());
        }

        @Test
        @DisplayName("a null code is refused")
        void nullCode() {
            assertTrue(service().redeemCode(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("code normalisation")
    class CodeNormalisation {

        @Test
        @DisplayName("case does not matter")
        void caseInsensitive() {
            AuthService auth = service();
            String code = auth.issueCode(ALICE, "Alice", List.of(1), false);

            assertTrue(auth.redeemCode(code.toLowerCase(java.util.Locale.ROOT)).isPresent());
        }

        @Test
        @DisplayName("the dash is optional")
        void dashOptional() {
            AuthService auth = service();
            String code = auth.issueCode(ALICE, "Alice", List.of(1), false);

            assertTrue(auth.redeemCode(code.replace("-", "")).isPresent());
        }

        @Test
        @DisplayName("stray spaces are ignored, so a pasted code still works")
        void spacesIgnored() {
            AuthService auth = service();
            String code = auth.issueCode(ALICE, "Alice", List.of(1), false);

            assertTrue(auth.redeemCode(" " + code + " ").isPresent());
        }
    }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("sessions")
    class Sessions {

        @Test
        @DisplayName("an unknown token resolves to nobody")
        void unknownToken() {
            assertTrue(service().userForToken("deadbeef").isEmpty());
        }

        @ParameterizedTest
        @DisplayName("null and blank tokens resolve to nobody")
        @ValueSource(strings = {"", " ", "\t"})
        void blankToken(String token) {
            assertTrue(service().userForToken(token).isEmpty());
        }

        @Test
        @DisplayName("a null token resolves to nobody")
        void nullToken() {
            assertTrue(service().userForToken(null).isEmpty());
        }

        @Test
        @DisplayName("an expired session no longer resolves")
        void expiredSession() {
            AuthService auth = service();
            Config.sessionDays = 0;
            String token = auth.redeemCode(auth.issueCode(ALICE, "Alice", List.of(1), false)).orElseThrow();

            assertTrue(auth.userForToken(token).isEmpty());
        }

        @Test
        @DisplayName("signing out drops only that browser's session")
        void revokeOneToken() {
            AuthService auth = service();
            String first = auth.redeemCode(auth.issueCode(ALICE, "Alice", List.of(1), false)).orElseThrow();
            String second = auth.redeemCode(auth.issueCode(ALICE, "Alice", List.of(1), false)).orElseThrow();

            auth.revokeToken(first);

            assertTrue(auth.userForToken(first).isEmpty(), "the signed-out browser should be gone");
            assertTrue(auth.userForToken(second).isPresent(), "the other browser should still work");
        }

        @Test
        @DisplayName("revoking an unknown token changes nothing")
        void revokeUnknownToken() {
            AuthService auth = service();
            String token = auth.redeemCode(auth.issueCode(ALICE, "Alice", List.of(1), false)).orElseThrow();

            auth.revokeToken("not-a-real-token");
            auth.revokeToken(null);

            assertTrue(auth.userForToken(token).isPresent());
        }

        @Test
        @DisplayName("an operator can sign a player out everywhere")
        void revokeAll() {
            AuthService auth = service();
            String first = auth.redeemCode(auth.issueCode(ALICE, "Alice", List.of(1), false)).orElseThrow();
            String second = auth.redeemCode(auth.issueCode(ALICE, "Alice", List.of(1), false)).orElseThrow();

            assertEquals(2, auth.revokeAllSessions(ALICE));

            assertTrue(auth.userForToken(first).isEmpty());
            assertTrue(auth.userForToken(second).isEmpty());
        }

        @Test
        @DisplayName("signing out a player with no account reports nothing revoked")
        void revokeAllForUnknownPlayer() {
            assertEquals(0, service().revokeAllSessions(BOB));
        }

        @Test
        @DisplayName("purging drops expired sessions and leaves live ones alone")
        void purgeExpired() {
            AuthService auth = service();
            Config.sessionDays = 0;
            auth.redeemCode(auth.issueCode(ALICE, "Alice", List.of(1), false)).orElseThrow();
            Config.sessionDays = 30;
            String live = auth.redeemCode(auth.issueCode(BOB, "Bob", List.of(2), false)).orElseThrow();

            assertEquals(2, auth.sessionCount());
            auth.purgeExpiredSessions();

            assertEquals(1, auth.sessionCount());
            assertTrue(auth.userForToken(live).isPresent());
        }
    }

    // ------------------------------------------------------------------
    // Pending code bookkeeping
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("pending code count")
    class PendingCodes {

        @Test
        @DisplayName("counts codes waiting to be typed in")
        void countsOutstanding() {
            AuthService auth = service();
            auth.issueCode(ALICE, "Alice", List.of(1), false);
            auth.issueCode(BOB, "Bob", List.of(2), false);

            assertEquals(2, auth.pendingCodeCount());
        }

        @Test
        @DisplayName("a redeemed code stops counting")
        void redeemedCodeIsGone() {
            AuthService auth = service();
            String code = auth.issueCode(ALICE, "Alice", List.of(1), false);
            auth.redeemCode(code);

            assertEquals(0, auth.pendingCodeCount());
        }

        @Test
        @DisplayName("expired codes are not counted")
        void expiredNotCounted() {
            AuthService auth = service();
            Config.loginCodeMinutes = 0;
            auth.issueCode(ALICE, "Alice", List.of(1), false);

            assertEquals(0, auth.pendingCodeCount());
        }

        @Test
        @DisplayName("signing a player out everywhere also cancels their pending code")
        void revokeAllCancelsCode() {
            AuthService auth = service();
            String code = auth.issueCode(ALICE, "Alice", List.of(1), false);

            auth.revokeAllSessions(ALICE);

            assertEquals(0, auth.pendingCodeCount());
            assertTrue(auth.redeemCode(code).isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // Access control
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("colony access")
    class ColonyAccess {

        @Test
        @DisplayName("a member sees their own colony and no others")
        void memberColoniesOnly() {
            AuthService auth = service();
            auth.issueCode(ALICE, "Alice", List.of(1), false);
            WebUser alice = auth.user(ALICE).orElseThrow();

            assertTrue(auth.canAccess(alice, 1));
            assertFalse(auth.canAccess(alice, 2));
        }

        @Test
        @DisplayName("an operator sees every colony")
        void adminSeesEverything() {
            AuthService auth = service();
            auth.issueCode(ALICE, "Alice", List.of(), true);
            WebUser alice = auth.user(ALICE).orElseThrow();

            assertTrue(auth.canAccess(alice, 999));
        }

        @Test
        @DisplayName("nobody is refused while authentication is switched off")
        void authDisabledOpensEverything() {
            Config.authEnabled = false;

            assertTrue(service().canAccess(null, 1), "a disabled dashboard is deliberately open");
        }

        @Test
        @DisplayName("an anonymous caller is refused while authentication is on")
        void nullUserRefused() {
            assertFalse(service().canAccess(null, 1));
        }

        @Test
        @DisplayName("an explicit grant survives a re-sync that drops the membership")
        void grantSurvivesResync() {
            AuthService auth = service();
            auth.issueCode(ALICE, "Alice", List.of(1), false);
            assertTrue(auth.grant(ALICE, "Alice", 5));

            // A later sync replaces the mirrored colonies wholesale.
            auth.issueCode(ALICE, "Alice", List.of(1), false);
            WebUser alice = auth.user(ALICE).orElseThrow();

            assertTrue(auth.canAccess(alice, 5), "the operator's grant must not be lost");
            assertEquals(Set.of(1, 5), alice.accessibleColonies());
        }

        @Test
        @DisplayName("granting the same colony twice reports no change the second time")
        void grantIsIdempotent() {
            AuthService auth = service();

            assertTrue(auth.grant(ALICE, "Alice", 5));
            assertFalse(auth.grant(ALICE, "Alice", 5));
        }

        @Test
        @DisplayName("granting creates an account for a player who has never synced")
        void grantCreatesAccount() {
            AuthService auth = service();

            assertTrue(auth.grant(BOB, "Bob", 3));

            assertEquals(Set.of(3), auth.user(BOB).orElseThrow().accessibleColonies());
        }

        @Test
        @DisplayName("revoking a grant leaves in-game membership intact")
        void revokeGrantKeepsMembership() {
            AuthService auth = service();
            auth.issueCode(ALICE, "Alice", List.of(1), false);
            auth.grant(ALICE, "Alice", 5);

            assertTrue(auth.revokeGrant(ALICE, 5));

            WebUser alice = auth.user(ALICE).orElseThrow();
            assertFalse(auth.canAccess(alice, 5));
            assertTrue(auth.canAccess(alice, 1), "membership is not a grant and must survive");
        }

        @Test
        @DisplayName("revoking a grant that was never made reports no change")
        void revokeMissingGrant() {
            AuthService auth = service();
            auth.issueCode(ALICE, "Alice", List.of(1), false);

            assertFalse(auth.revokeGrant(ALICE, 5));
            assertFalse(auth.revokeGrant(BOB, 5), "an unknown player has nothing to revoke");
        }
    }

    @Nested
    @DisplayName("account lookup")
    class AccountLookup {

        @Test
        @DisplayName("a player who never synced has no account")
        void unknownPlayer() {
            assertTrue(service().user(BOB).isEmpty());
        }

        @Test
        @DisplayName("lookup ignores UUID case")
        void uuidCaseInsensitive() {
            AuthService auth = service();
            auth.issueCode(ALICE, "Alice", List.of(1), false);

            assertTrue(auth.user(UUID.fromString(ALICE.toString().toUpperCase(java.util.Locale.ROOT))).isPresent());
        }

        @Test
        @DisplayName("every known account is listed")
        void listsAllUsers() {
            AuthService auth = service();
            auth.issueCode(ALICE, "Alice", List.of(1), false);
            auth.issueCode(BOB, "Bob", List.of(2), false);

            assertEquals(2, auth.allUsers().size());
        }

        @Test
        @DisplayName("a rename is picked up on the next sync")
        void nameIsRefreshed() {
            AuthService auth = service();
            auth.issueCode(ALICE, "OldName", List.of(1), false);
            auth.issueCode(ALICE, "NewName", List.of(1), false);

            assertEquals("NewName", auth.user(ALICE).orElseThrow().name);
        }
    }
}
