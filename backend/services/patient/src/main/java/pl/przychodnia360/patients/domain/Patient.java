package pl.przychodnia360.patients.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Pacjent placówki; w 1a tylko pola jawne (PESEL i dane kontaktowe szyfrowane dochodzą w 1b). */
public record Patient(UUID id, UUID userId, String firstName, String lastName, LocalDate birthDate,
        String contactEmail, long version) {

    public String fullName() {
        return firstName + " " + lastName;
    }

    public Patient withContactEmail(String email) {
        return new Patient(id, userId, firstName, lastName, birthDate, email, version);
    }

    public Patient withUserId(UUID newUserId) {
        return new Patient(id, newUserId, firstName, lastName, birthDate, contactEmail, version);
    }
}
