package pl.przychodnia360.patients.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import java.util.UUID;

public record EnsureRequest(@NotBlank String firstName, @NotBlank String lastName, @NotNull @Past LocalDate birthDate,
        String contactEmail, UUID userId) {
}
