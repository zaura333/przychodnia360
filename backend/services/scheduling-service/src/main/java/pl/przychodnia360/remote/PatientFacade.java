package pl.przychodnia360.remote;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dane pacjentów potrzebne rezerwacjom i widokom wizyt; źródłem jest iam-service (wewnętrzne API, ADR-M07). */
public interface PatientFacade {

    record PatientInfo(UUID id, UUID userId, String firstName, String lastName, LocalDate birthDate,
            String contactEmail) {
        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    Optional<PatientInfo> find(UUID patientId);

    Map<UUID, PatientInfo> findAll(Collection<UUID> ids);
}
