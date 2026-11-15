package pl.przychodnia360.staff.api.dto;

import java.time.Instant;

public record IntervalResponse(Instant start, Instant end) {
}
