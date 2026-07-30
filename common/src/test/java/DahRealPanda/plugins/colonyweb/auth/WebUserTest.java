package DahRealPanda.plugins.colonyweb.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account record itself: how the two sources of colony access combine, and when a stored
 * session stops counting.
 */
class WebUserTest {

    private static StoredSession session(long expiresAt) {
        return new StoredSession("hash-" + expiresAt, 0, expiresAt);
    }

    @Nested
    @DisplayName("accessible colonies")
    class AccessibleColonies {

        @Test
        @DisplayName("membership and grants are combined")
        void unionOfBoth() {
            WebUser user = new WebUser("uuid", "Alice");
            user.colonies = new java.util.LinkedHashSet<>(List.of(1, 2));
            user.granted = new java.util.LinkedHashSet<>(List.of(3));

            assertEquals(Set.of(1, 2, 3), user.accessibleColonies());
        }

        @Test
        @DisplayName("a colony that is both a membership and a grant appears once")
        void overlapIsDeduplicated() {
            WebUser user = new WebUser("uuid", "Alice");
            user.colonies = new java.util.LinkedHashSet<>(List.of(1));
            user.granted = new java.util.LinkedHashSet<>(List.of(1));

            assertEquals(1, user.accessibleColonies().size());
        }

        @Test
        @DisplayName("a brand new account can see nothing")
        void emptyByDefault() {
            assertTrue(new WebUser("uuid", "Alice").accessibleColonies().isEmpty());
        }

        @Test
        @DisplayName("the returned set is a copy, so callers cannot grant themselves access")
        void returnsCopy() {
            WebUser user = new WebUser("uuid", "Alice");
            user.colonies = new java.util.LinkedHashSet<>(List.of(1));

            user.accessibleColonies().add(99);

            assertEquals(Set.of(1), user.accessibleColonies());
        }
    }

    @Nested
    @DisplayName("purging sessions")
    class Purging {

        @Test
        @DisplayName("an expired session is dropped and the change is reported")
        void dropsExpired() {
            WebUser user = new WebUser("uuid", "Alice");
            user.sessions.add(session(50));

            assertTrue(user.purgeExpiredSessions(100));
            assertTrue(user.sessions.isEmpty());
        }

        @Test
        @DisplayName("a live session is kept and no change is reported")
        void keepsLive() {
            WebUser user = new WebUser("uuid", "Alice");
            user.sessions.add(session(500));

            assertFalse(user.purgeExpiredSessions(100));
            assertEquals(1, user.sessions.size());
        }

        @Test
        @DisplayName("a session expiring exactly now is dropped")
        void boundaryIsExpired() {
            WebUser user = new WebUser("uuid", "Alice");
            user.sessions.add(session(100));

            assertTrue(user.purgeExpiredSessions(100));
        }

        @Test
        @DisplayName("only the expired sessions go")
        void mixedSessions() {
            WebUser user = new WebUser("uuid", "Alice");
            user.sessions.add(session(50));
            user.sessions.add(session(500));

            assertTrue(user.purgeExpiredSessions(100));
            assertEquals(1, user.sessions.size());
            assertEquals(500, user.sessions.get(0).expiresAt);
        }

        @Test
        @DisplayName("purging an account with no sessions reports no change")
        void noSessions() {
            assertFalse(new WebUser("uuid", "Alice").purgeExpiredSessions(100));
        }
    }
}
