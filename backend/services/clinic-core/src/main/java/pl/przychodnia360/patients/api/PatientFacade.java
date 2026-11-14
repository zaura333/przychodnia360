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

    /** Wiąże rekord pacjenta z kontem użytkownika. */
    void linkAccount(UUID patientId, UUID userId);

    Optional<PatientInfo> find(UUID patientId);

    /** Pacjenci po identyfikatorach (widoki z imieniem i nazwiskiem); nieznane identyfikatory są pomijane. */
    Map<UUID, PatientInfo> findAll(Collection<UUID> ids);
}
