package pl.przychodnia360.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}$",
                message = "Identyfikator: małe litery, cyfry i myślniki (2-63 znaki)") String slug,
        @NotBlank @Size(max = 200) String name) {
}
