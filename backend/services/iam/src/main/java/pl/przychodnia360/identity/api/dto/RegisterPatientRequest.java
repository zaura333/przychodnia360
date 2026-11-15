package pl.przychodnia360.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RegisterPatientRequest(
        @NotBlank @Size(max = 63) String tenantSlug,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @PastOrPresent LocalDate birthDate) {
}
