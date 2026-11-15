package pl.przychodnia360.identity.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.przychodnia360.shared.TooManyRequestsException;

/**
 * Limiter żądań na /auth/* w pamięci procesu (okno stałe). Jedno zadanie w demo, więc limit jest spójny, ale
 * zeruje się po restarcie; główną ochroną kont jest blokada w bazie (plan 1a, sekcja 8).
 */
@Component
public class AuthRateLimiter {

    private record Window(Instant start, int count) {
    }

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_KEYS = 10_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int limitPerMinute;

    AuthRateLimiter(Clock clock, @Value("${security.rate-limit.per-minute:20}") int limitPerMinute) {
        this.clock = clock;
        this.limitPerMinute = limitPerMinute;
    }

    /** Zlicza próbę; po przekroczeniu limitu w oknie rzuca 429. */
    public void check(String key) {
        Instant now = clock.instant();
        if (windows.size() > MAX_KEYS) {
            windows.entrySet().removeIf(e -> e.getValue().start().plus(WINDOW).isBefore(now));
        }
        Window w = windows.merge(key, new Window(now, 1), (old, fresh) ->
                old.start().plus(WINDOW).isAfter(now) ? new Window(old.start(), old.count() + 1) : fresh);
        if (w.count() > limitPerMinute) {
            throw new TooManyRequestsException("Zbyt wiele prób. Spróbuj ponownie za chwilę.");
        }
    }
}
