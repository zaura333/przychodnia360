package pl.przychodnia360.appointments.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record BookRequest(@NotNull UUID serviceId, @NotNull UUID employeeId, @NotNull Instant startAt,
        UUID patientId) {
}
