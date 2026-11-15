package pl.przychodnia360.patients.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Publiczny interfejs modułu pacjentów dla innych modułów (rejestracja konta, rezerwacje). */
public interface PatientFacade {

    /** Dane pacjenta widoczne dla innych modułów. */
    record PatientInfo(UUID id, UUID userId, String firstName, String lastName, LocalDate birthDate,
            String contactEmail) {

        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    /** Zakłada rekord pacjenta w bieżącej placówce (kontekst z TenantContext). */
    UUID create(String firstName, String lastName, LocalDate birthDate, String contactEmail);

    /**
     * Idempotentnie zakłada rekord pacjenta o zadanym identyfikatorze (saga rejestracji, S2): powtórzenie z tym samym
     * identyfikatorem niczego nie duplikuje, a podane powiązanie z kontem jest ustawiane (nadpisuje poprzednie, np. z
     * wycofanej próby rejestracji).
     */
    void ensure(UUID patientId, String firstName, String lastName, LocalDate birthDate, String contactEmail,
            UUID userId);

    /** Wiąże rekord pacjenta z kontem użytkownika. */
    void linkAccount(UUID patientId, UUID userId);

    Optional<PatientInfo> find(UUID patientId);

    /** Pacjenci po identyfikatorach (widoki z imieniem i nazwiskiem); nieznane identyfikatory są pomijane. */
    Map<UUID, PatientInfo> findAll(Collection<UUID> ids);
}
