package pl.przychodnia360.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ServiceTokenRequest(@NotBlank String clientId, @NotBlank String clientSecret, @NotNull UUID tenantId,
        @NotBlank String audience) {
}
