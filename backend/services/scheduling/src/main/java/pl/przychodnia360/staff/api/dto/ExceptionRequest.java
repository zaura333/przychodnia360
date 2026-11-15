package pl.przychodnia360.staff.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import pl.przychodnia360.staff.domain.ScheduleException;

public record ExceptionRequest(@NotNull LocalDate date, @NotNull ScheduleException.Type type, LocalTime startTime,
        LocalTime endTime) {
}
