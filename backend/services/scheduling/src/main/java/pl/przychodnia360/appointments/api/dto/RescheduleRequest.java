package pl.przychodnia360.appointments.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RescheduleRequest(@NotNull Instant startAt) {
}
