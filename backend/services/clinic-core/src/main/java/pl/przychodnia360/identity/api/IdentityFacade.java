package pl.przychodnia360.identity.api;

import java.util.UUID;
import pl.przychodnia360.common.security.Role;

/** Publiczny interfejs modułu identity dla innych modułów i usług (konta pracowników). */
public interface IdentityFacade {

    /**
     * Idempotentnie zakłada konto pracownika w bieżącej placówce, od razu powiązane z {@code employeeId}, bez hasła
     * (pracownik ustawia je z linku wysłanego e-mailem, token jednorazowy, TTL 72 h). Powtórzenie z tym samym
     * pracownikiem i e-mailem zwraca istniejące konto bez ponownej wysyłki (saga zakładania pracownika). E-mail zajęty
     * przez inne konto to ConflictException (email-taken).
     */
    UUID ensureEmployeeAccount(UUID employeeId, String email, Role role);

    /** Dezaktywuje konto i unieważnia wszystkie tokeny odświeżania użytkownika. Idempotentne. */
    void deactivate(UUID userId);
}
