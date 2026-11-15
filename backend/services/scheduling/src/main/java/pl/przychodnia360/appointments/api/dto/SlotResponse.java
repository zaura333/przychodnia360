package pl.przychodnia360.appointments.api.dto;

import java.time.Instant;

public record SlotResponse(Instant start, Instant end) {
}
