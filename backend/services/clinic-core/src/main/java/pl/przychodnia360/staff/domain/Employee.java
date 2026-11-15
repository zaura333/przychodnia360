package pl.przychodnia360.staff.domain;

import java.util.UUID;
import pl.przychodnia360.staff.api.EmployeeType;

public record Employee(UUID id, UUID userId, EmployeeType type, String firstName, String lastName,
        String specialization, boolean active, long version) {

    public boolean canPerform(String performerType) {
        return type.canPerform(performerType);
    }

    public Employee withDetails(String newFirstName, String newLastName, String newSpecialization) {
        return new Employee(id, userId, type, newFirstName, newLastName, newSpecialization, active, version);
    }

    public Employee deactivated() {
        return new Employee(id, userId, type, firstName, lastName, specialization, false, version);
    }
}
