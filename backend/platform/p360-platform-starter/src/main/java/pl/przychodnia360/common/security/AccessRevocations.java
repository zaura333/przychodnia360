package pl.przychodnia360.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lista odwołań dostępu (ADR-M04): token dostępu użytkownika wystawiony nie później niż w chwili odwołania jest
 * odrzucany, aż do wygaśnięcia najdłuższego tokenu. Po dezaktywacji konta dostęp ustaje od razu, a nie po
 * wygaśnięciu tokenu (do 10 minut). W fali 1 listę zasila zdarzenie UserDeactivated dostarczone do każdej usługi.
 */
@Component
public class AccessRevocations {

    private final Clock clock;
    private final Duration retention;
    private final ConcurrentMap<UUID, Instant> revokedAt = new ConcurrentHashMap<>();

    public AccessRevocations(Clock clock, @Value("${security.jwt.access-ttl:PT10M}") Duration accessTtl) {
        this.clock = clock;
        // Zapas na rozjazd zegarów: wpis żyje dłużej niż token, który mógłby jeszcze być ważny
        this.retention = accessTtl.plusMinutes(1);
    }

    public void revoke(UUID userId) {
        Instant now = clock.instant();
        revokedAt.put(userId, now);
        revokedAt.values().removeIf(at -> at.plus(retention).isBefore(now));
    }

    /** Czy token wystawiony o {@code issuedAt} dla tego użytkownika został odwołany. */
    public boolean isRevoked(UUID userId, Instant issuedAt) {
        Instant at = revokedAt.get(userId);
        return at != null && !issuedAt.isAfter(at);
    }
}
