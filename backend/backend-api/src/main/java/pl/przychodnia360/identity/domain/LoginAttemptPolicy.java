package pl.przychodnia360.identity.domain;

import java.time.Duration;

/** Blokada konta po serii nieudanych logowań. */
public record LoginAttemptPolicy(int maxAttempts, Duration lockDuration) {

    public LoginAttemptPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts");
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lockDuration");
        }
    }
}
