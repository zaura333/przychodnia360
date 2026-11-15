package pl.przychodnia360.staff.api.dto;

import java.util.UUID;
import pl.przychodnia360.staff.api.EmployeeType;
import pl.przychodnia360.staff.domain.Employee;

public record EmployeeResponse(UUID id, UUID userId, EmployeeType type, String firstName, String lastName,
        String specialization, boolean active) {

    public static EmployeeResponse of(Employee e) {
        return new EmployeeResponse(e.id(), e.userId(), e.type(), e.firstName(), e.lastName(), e.specialization(),
                e.active());
    }
}
