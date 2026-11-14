package pl.przychodnia360.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AccessRevocationsTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-14T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final AccessRevocations revocations = new AccessRevocations(clock, Duration.ofMinutes(10));
    private final UUID user = UUID.randomUUID();

    @Test
    void tokenIssuedBeforeRevocationIsRejectedAndLaterOneIsNot() {
        Instant issued = now.get().minusSeconds(60);
        assertThat(revocations.isRevoked(user, issued)).isFalse();
        revocations.revoke(user);
        assertThat(revocations.isRevoked(user, issued)).isTrue();
        assertThat(revocations.isRevoked(user, now.get().plusSeconds(1))).isFalse();
        assertThat(revocations.isRevoked(UUID.randomUUID(), issued)).as("inny użytkownik").isFalse();
    }

    @Test
    void oldEntriesAreDroppedAfterRetention() {
        revocations.revoke(user);
        now.set(now.get().plus(Duration.ofMinutes(30)));
        revocations.revoke(UUID.randomUUID());
        assertThat(revocations.isRevoked(user, Instant.parse("2026-11-14T09:00:00Z"))).isFalse();
    }
}
