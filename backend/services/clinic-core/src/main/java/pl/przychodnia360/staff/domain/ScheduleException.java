package pl.przychodnia360.staff.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Wyjątek grafiku: nieobecność całego dnia albo dodatkowa zmiana w podanych godzinach. */
public record ScheduleException(UUID id, UUID employeeId, LocalDate date, Type type, LocalTime startTime,
        LocalTime endTime) {

    public enum Type {
        ABSENCE, EXTRA_SHIFT
    }
}
