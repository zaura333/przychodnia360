package pl.przychodnia360.common.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Prosty wyłącznik obwodu dla wywołań innych usług: po {@code failureThreshold} kolejnych porażkach obwód otwiera się
 * na {@code openFor} (wywołania są odrzucane od razu), potem przepuszcza jedną próbę. Sukces zamyka obwód, porażka
 * próby otwiera go ponownie. Bez zewnętrznych zależności; stan jest lokalny dla procesu.
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration openFor;
    private final Clock clock;

    private State state = State.CLOSED;
    private int failures;
    private Instant openedAt;
    private boolean trialInFlight;

    public CircuitBreaker(int failureThreshold, Duration openFor, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openFor = openFor;
        this.clock = clock;
    }

    /** Czy wywołanie wolno wykonać; w stanie półotwartym tylko jedno jednoczesne. */
    public synchronized boolean tryAcquire() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (clock.instant().isBefore(openedAt.plus(openFor))) {
                    return false;
                }
                state = State.HALF_OPEN;
                trialInFlight = true;
                return true;
            default:
                if (trialInFlight) {
                    return false;
                }
                trialInFlight = true;
                return true;
        }
    }

    public synchronized void onSuccess() {
        state = State.CLOSED;
        failures = 0;
        trialInFlight = false;
    }

    public synchronized void onFailure() {
        trialInFlight = false;
        failures++;
        if (state == State.HALF_OPEN || failures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.instant();
        }
    }

    public synchronized State state() {
        return state;
    }
}
