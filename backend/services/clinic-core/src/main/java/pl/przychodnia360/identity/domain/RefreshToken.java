package pl.przychodnia360.identity.domain;

import java.time.Instant;
import java.util.UUID;

/** Zapis tokenu odświeżania (w bazie tylko hash). tenantId jest null dla administratora platformy. */
public record RefreshToken(UUID id, UUID familyId, UUID userId, UUID tenantId, String tokenHash, Instant expiresAt,
        Instant usedAt, Instant revokedAt) {

    public boolean isUsableAt(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    /** Zużyty lub unieważniony token użyty ponownie to sygnał kradzieży: unieważniamy całą rodzinę. */
    public boolean isReuse() {
        return usedAt != null || revokedAt != null;
    }
}
