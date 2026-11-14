package pl.przychodnia360.identity.domain;

import java.time.Instant;
import java.util.UUID;

/** Jednorazowy token resetu lub ustawienia hasła (w bazie tylko hash). */
public record AccountToken(UUID id, UUID userId, UUID tenantId, Purpose purpose, String tokenHash, Instant expiresAt,
        Instant usedAt) {

    public enum Purpose {
        RESET, SET_PASSWORD
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
