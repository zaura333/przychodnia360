package pl.przychodnia360.staff.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Dyżur tygodniowy w czasie lokalnym placówki, obowiązujący od validFrom do validTo włącznie (null = bezterminowo). */
public record WorkSchedule(UUID id, UUID employeeId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
        LocalDate validFrom, LocalDate validTo) {

    public boolean appliesOn(LocalDate date) {
        return date.getDayOfWeek() == dayOfWeek && !date.isBefore(validFrom)
                && (validTo == null || !date.isAfter(validTo));
    }
}
