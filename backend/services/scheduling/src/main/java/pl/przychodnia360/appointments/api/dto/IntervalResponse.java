package pl.przychodnia360.appointments.api.dto;

import java.time.Instant;

public record IntervalResponse(Instant start, Instant end) {
}
