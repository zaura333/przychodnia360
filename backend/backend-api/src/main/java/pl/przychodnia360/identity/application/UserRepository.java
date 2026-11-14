package pl.przychodnia360.identity.application;

import java.util.Optional;
import java.util.UUID;
import pl.przychodnia360.identity.domain.User;

public interface UserRepository {

    /** Wstawia nowe konto; zajęty e-mail w placówce to ConflictException (email-taken). */
    void insert(User user);

    /** Zapis zmian (licznik prób, blokada, hasło, status) z blokadą optymistyczną. */
    void update(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(UUID tenantId, String email);

    Optional<User> findPlatformAdminByEmail(String email);
}
