package pl.przychodnia360.staff.api;

import java.time.Duration;
import java.time.Instant;

/** Przedział czasu [start, end): koniec wyłączny, więc przedziały stykające się nie nakładają się. */
public record TimeSlot(Instant start, Instant end) {

    public TimeSlot {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end musi być po start");
        }
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public boolean contains(TimeSlot other) {
        return !other.start.isBefore(start) && !other.end.isAfter(end);
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
