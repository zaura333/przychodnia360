package pl.przychodnia360.patients.api;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port iam do patient-service: tylko to, czego iam potrzebuje (rejestracja konta pacjenta). Kontrakt sieciowy to
 * {@code api/exports/patient.json}, a ten interfejs jest własnością konsumenta.
 */
public interface PatientFacade {

    /** Dane pacjenta widoczne dla iam. */
    record PatientInfo(UUID id, UUID userId, String firstName, String lastName, LocalDate birthDate,
            String contactEmail) {

        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    /**
     * Idempotentnie zakłada rekord pacjenta o zadanym identyfikatorze (saga rejestracji, S2): powtórzenie z tym samym
     * identyfikatorem niczego nie duplikuje, a podane powiązanie z kontem jest ustawiane (nadpisuje poprzednie, np. z
     * wycofanej próby rejestracji).
     */
    void ensure(UUID patientId, String firstName, String lastName, LocalDate birthDate, String contactEmail,
            UUID userId);

    Optional<PatientInfo> find(UUID patientId);
}
