package pl.przychodnia360.remote;

import java.util.UUID;
import pl.przychodnia360.common.security.Role;

/** Konta pracowników w iam-service (wewnętrzne API, ADR-M07). */
public interface IdentityFacade {

    /** Zakłada konto pracownika (bez hasła; link do ustawienia hasła wysyła iam) i zwraca identyfikator konta. */
    UUID createEmployeeAccount(String email, Role role);

    void linkEmployee(UUID userId, UUID employeeId);

    void deactivate(UUID userId);
}
