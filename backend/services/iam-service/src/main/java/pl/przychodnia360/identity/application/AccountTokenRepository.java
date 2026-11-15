package pl.przychodnia360.identity.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pl.przychodnia360.identity.domain.AccountToken;

public interface AccountTokenRepository {

    void insert(AccountToken token);

    /** Odczyt z blokadą wiersza: dwa równoległe użycia tego samego tokenu nie przejdą oba. */
    Optional<AccountToken> findByHashForUpdate(String tokenHash);

    void markUsed(UUID id, Instant now);

    /** Unieważnia (oznacza jako użyte) wszystkie niewykorzystane tokeny użytkownika. */
    void invalidateAllForUser(UUID userId, Instant now);
}
