package pl.przychodnia360.staff.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import pl.przychodnia360.staff.domain.ScheduleException;

public record ExceptionResponse(UUID id, LocalDate date, ScheduleException.Type type, LocalTime startTime,
        LocalTime endTime) {

    public static ExceptionResponse of(ScheduleException x) {
        return new ExceptionResponse(x.id(), x.date(), x.type(), x.startTime(), x.endTime());
    }
}
