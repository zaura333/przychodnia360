package pl.przychodnia360.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Size(max = 63) String tenantSlug,
        @NotBlank @Email @Size(max = 254) String email) {
}
