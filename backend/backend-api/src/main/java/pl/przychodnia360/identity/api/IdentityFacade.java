package pl.przychodnia360.identity.api;

import java.util.UUID;
import pl.przychodnia360.common.security.Role;

/** Publiczny interfejs modułu identity dla innych modułów (konta pracowników i pacjentów). */
public interface IdentityFacade {

    /**
     * Zakłada konto pracownika w bieżącej placówce bez hasła. Pracownik ustawia hasło z linku wysłanego e-mailem
     * (token jednorazowy, TTL 72 h). Zajęty e-mail to ConflictException.
     */
    UUID createEmployeeAccount(String email, Role role);

    void linkEmployee(UUID userId, UUID employeeId);

    /** Dezaktywuje konto i unieważnia wszystkie tokeny odświeżania użytkownika. */
    void deactivate(UUID userId);
}
