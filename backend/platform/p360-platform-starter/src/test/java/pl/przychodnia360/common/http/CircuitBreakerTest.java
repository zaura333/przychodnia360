package pl.przychodnia360.common.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    /** Zegar do przesuwania czasu w teście. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-15T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

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
            return now;
        }
    }

    @Test
    void opensAfterConsecutiveFailuresAndRejectsCallsUntilTheWindowPasses() {
        MutableClock clock = new MutableClock();
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(10), clock);

        for (int i = 0; i < 3; i++) {
            assertThat(breaker.tryAcquire()).isTrue();
            breaker.onFailure();
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).as("obwód otwarty").isFalse();

        clock.advance(Duration.ofSeconds(11));
        assertThat(breaker.tryAcquire()).as("jedna próba po czasie otwarcia").isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.tryAcquire()).as("druga jednoczesna próba odrzucona").isFalse();
    }

    @Test
    void successClosesTheCircuitAndAFailedTrialReopensIt() {
        MutableClock clock = new MutableClock();
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofSeconds(5), clock);
        breaker.tryAcquire();
        breaker.onFailure();
        clock.advance(Duration.ofSeconds(6));

        breaker.tryAcquire();
        breaker.onFailure();
        assertThat(breaker.state()).as("porażka próby otwiera ponownie").isEqualTo(CircuitBreaker.State.OPEN);

        clock.advance(Duration.ofSeconds(6));
        breaker.tryAcquire();
        breaker.onSuccess();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    void successInTheMiddleResetsTheFailureCount() {
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(5), new MutableClock());
        breaker.onFailure();
        breaker.onFailure();
        breaker.onSuccess();
        breaker.onFailure();
        breaker.onFailure();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
