package pl.przychodnia360.staff.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Publiczny interfejs modułu staff dla innych modułów (rezerwacje). Staff nie zna wizyt. */
public interface StaffFacade {

    /** Dane pracownika potrzebne innym modułom. */
    record EmployeeInfo(UUID id, UUID userId, EmployeeType type, String firstName, String lastName,
            String specialization, boolean active) {

        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    Optional<EmployeeInfo> employee(UUID employeeId);

    /** Pracownicy po identyfikatorach (do widoków z imieniem i nazwiskiem); nieznane identyfikatory są pomijane. */
    Map<UUID, EmployeeInfo> employees(Collection<UUID> ids);

    /**
     * Przedziały pracy w dniach lokalnych [from, to] (włącznie): grafik tygodniowy bez nieobecności plus dodatkowe
     * zmiany, przeliczone na czas bezwzględny, posortowane i scalone.
     */
    List<TimeSlot> workingIntervals(UUID employeeId, LocalDate from, LocalDate to);
}
