package pl.przychodnia360.patients.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port scheduling do patient-service: tylko to, czego scheduling potrzebuje (rezerwacje, widoki wizyt, seed).
 * Interfejs jest własnością konsumenta; adapter HTTP ({@code RemotePatientDirectory}) woła wewnętrzne API
 * patient-service ({@code /api/v1/internal/patients}), którego nie ma w eksportach OpenAPI.
 */
public interface PatientDirectory {

    /** Dane pacjenta widoczne dla scheduling. */
    record PatientInfo(UUID id, UUID userId, String firstName, String lastName, LocalDate birthDate,
            String contactEmail) {

        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    /**
     * Idempotentnie zakłada rekord pacjenta o zadanym identyfikatorze (seed): powtórzenie z tym samym identyfikatorem
     * niczego nie duplikuje.
     */
    void ensure(UUID patientId, String firstName, String lastName, LocalDate birthDate, String contactEmail,
            UUID userId);

    Optional<PatientInfo> find(UUID patientId);

    /** Pacjenci po identyfikatorach (widoki z imieniem i nazwiskiem); nieznane identyfikatory są pomijane. */
    Map<UUID, PatientInfo> findAll(Collection<UUID> ids);
}
