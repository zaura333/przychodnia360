package pl.przychodnia360.patients.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkAccountRequest(@NotNull UUID userId) {
}
