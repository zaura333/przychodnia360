package pl.przychodnia360.patients.api.dto;

import java.time.LocalDate;
import java.util.UUID;
import pl.przychodnia360.patients.domain.Patient;

public record PatientResponse(UUID id, String firstName, String lastName, LocalDate birthDate,
        String contactEmail, boolean hasAccount) {

    public static PatientResponse of(Patient p) {
        return new PatientResponse(p.id(), p.firstName(), p.lastName(), p.birthDate(), p.contactEmail(),
                p.userId() != null);
    }
}
