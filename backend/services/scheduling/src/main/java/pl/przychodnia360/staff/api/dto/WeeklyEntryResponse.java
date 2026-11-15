package pl.przychodnia360.staff.api.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import pl.przychodnia360.staff.domain.WorkSchedule;

public record WeeklyEntryResponse(UUID id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
        LocalDate validFrom, LocalDate validTo) {

    public static WeeklyEntryResponse of(WorkSchedule w) {
        return new WeeklyEntryResponse(w.id(), w.dayOfWeek(), w.startTime(), w.endTime(), w.validFrom(),
                w.validTo());
    }
}
