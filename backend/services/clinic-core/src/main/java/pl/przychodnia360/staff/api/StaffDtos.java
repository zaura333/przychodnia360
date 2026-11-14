package pl.przychodnia360.staff.api;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import pl.przychodnia360.staff.application.ScheduleService.ScheduleView;
import pl.przychodnia360.staff.domain.Employee;
import pl.przychodnia360.staff.domain.ScheduleException;
import pl.przychodnia360.staff.domain.WorkSchedule;

/** Wspólne kształty odpowiedzi API modułu staff. */
final class StaffDtos {

    private StaffDtos() {
    }

    record EmployeeResponse(UUID id, UUID userId, EmployeeType type, String firstName, String lastName,
            String specialization, boolean active) {

        static EmployeeResponse of(Employee e) {
            return new EmployeeResponse(e.id(), e.userId(), e.type(), e.firstName(), e.lastName(), e.specialization(),
                    e.active());
        }
    }

    record WeeklyEntryResponse(UUID id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
            LocalDate validFrom, LocalDate validTo) {

        static WeeklyEntryResponse of(WorkSchedule w) {
            return new WeeklyEntryResponse(w.id(), w.dayOfWeek(), w.startTime(), w.endTime(), w.validFrom(),
                    w.validTo());
        }
    }

    record ExceptionResponse(UUID id, LocalDate date, ScheduleException.Type type, LocalTime startTime,
            LocalTime endTime) {

        static ExceptionResponse of(ScheduleException x) {
            return new ExceptionResponse(x.id(), x.date(), x.type(), x.startTime(), x.endTime());
        }
    }

    record IntervalResponse(Instant start, Instant end) {
    }

    record ScheduleResponse(List<WeeklyEntryResponse> weekly, List<ExceptionResponse> exceptions,
            List<IntervalResponse> intervals) {

        static ScheduleResponse of(ScheduleView view) {
            return new ScheduleResponse(view.weekly().stream().map(WeeklyEntryResponse::of).toList(),
                    view.exceptions().stream().map(ExceptionResponse::of).toList(),
                    view.intervals().stream().map(i -> new IntervalResponse(i.start(), i.end())).toList());
        }
    }
}
