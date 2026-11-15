package pl.przychodnia360.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantAdminRequest(@NotBlank @Email @Size(max = 254) String email) {
}
