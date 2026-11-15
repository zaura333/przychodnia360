package pl.przychodnia360.staff.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.przychodnia360.staff.api.EmployeeType;

public record CreateEmployeeRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull EmployeeType type,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 200) String specialization) {
}
