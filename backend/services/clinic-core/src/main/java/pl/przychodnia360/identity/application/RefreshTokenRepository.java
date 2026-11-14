package pl.przychodnia360.identity.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pl.przychodnia360.identity.domain.RefreshToken;

public interface RefreshTokenRepository {

    void insert(RefreshToken token);

    /** Odczyt z blokadą wiersza, żeby dwa równoległe odświeżenia tym samym tokenem nie przeszły oba. */
    Optional<RefreshToken> findByHashForUpdate(String tokenHash);

    void markUsed(UUID id, Instant now);

    void revokeFamily(UUID familyId, Instant now);

    void revokeAllForUser(UUID userId, Instant now);
}
