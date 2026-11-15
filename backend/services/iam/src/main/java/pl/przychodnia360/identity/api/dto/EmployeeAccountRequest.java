package pl.przychodnia360.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.przychodnia360.common.security.Role;

public record EmployeeAccountRequest(@NotBlank @Email String email, @NotNull Role role) {
}
