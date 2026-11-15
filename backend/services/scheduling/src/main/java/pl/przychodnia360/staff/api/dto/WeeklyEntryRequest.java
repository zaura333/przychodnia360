package pl.przychodnia360.staff.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record WeeklyEntryRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime,
        @NotNull LocalTime endTime, @NotNull LocalDate validFrom, LocalDate validTo) {
}
