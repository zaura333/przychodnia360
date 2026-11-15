package pl.przychodnia360.staff.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ValidationException;
import pl.przychodnia360.staff.api.StaffFacade;
import pl.przychodnia360.staff.api.TimeSlot;
import pl.przychodnia360.staff.domain.Employee;
import pl.przychodnia360.staff.domain.ScheduleException;
import pl.przychodnia360.staff.domain.WorkSchedule;
import pl.przychodnia360.staff.domain.WorkingTimeCalculator;

/** Grafiki pracowników oraz implementacja {@link StaffFacade}. */
@Service
public class ScheduleService implements StaffFacade {

    /** Maksymalne okno zapytań o grafik, chroni wydajność (plan 1a, 7.3). */
    public static final int MAX_WINDOW_DAYS = 31;

    /** Pozycja grafiku tygodniowego do zapisania. */
    public record WeeklyEntry(java.time.DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
            LocalDate validFrom, LocalDate validTo) {
    }

    /** Grafik pracownika w oknie: wpisy tygodniowe, wyjątki i wyliczone przedziały pracy. */
    public record ScheduleView(List<WorkSchedule> weekly, List<ScheduleException> exceptions, List<TimeSlot> intervals) {
    }

    private final StaffRepository repository;

    ScheduleService(StaffRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeInfo> employee(UUID employeeId) {
        return repository.findEmployee(employeeId).map(ScheduleService::info);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, EmployeeInfo> employees(Collection<UUID> ids) {
        return repository.findEmployees(ids).stream()
                .collect(Collectors.toMap(Employee::id, ScheduleService::info, (a, b) -> a, java.util.LinkedHashMap::new));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeSlot> workingIntervals(UUID employeeId, LocalDate from, LocalDate to) {
        return view(employeeId, from, to).intervals();
    }

    @Transactional(readOnly = true)
    public ScheduleView view(UUID employeeId, LocalDate from, LocalDate to) {
        validateWindow(from, to);
        repository.findEmployee(employeeId).orElseThrow(() -> new NotFoundException("Nie znaleziono pracownika."));
        List<WorkSchedule> weekly = repository.findWeekly(employeeId);
        List<ScheduleException> exceptions = repository.findExceptions(employeeId, from, to);
        return new ScheduleView(weekly, exceptions, WorkingTimeCalculator.compute(weekly, exceptions, from, to));
    }

    @Transactional(readOnly = true)
    public List<WorkSchedule> weekly(UUID employeeId) {
        repository.findEmployee(employeeId).orElseThrow(() -> new NotFoundException("Nie znaleziono pracownika."));
        return repository.findWeekly(employeeId);
    }

    /** Zastępuje grafik tygodniowy pracownika (FR-34). Nakładające się dyżury odrzuca baza (409). */
    @Transactional
    public List<WorkSchedule> defineWeekly(UUID employeeId, List<WeeklyEntry> entries) {
        repository.findEmployee(employeeId).orElseThrow(() -> new NotFoundException("Nie znaleziono pracownika."));
        for (WeeklyEntry e : entries) {
            if (!e.endTime().isAfter(e.startTime())) {
                throw new ValidationException("Koniec dyżuru musi być po jego początku.");
            }
            if (e.validTo() != null && e.validTo().isBefore(e.validFrom())) {
                throw new ValidationException("Data końca obowiązywania nie może być przed datą początku.");
            }
        }
        List<WorkSchedule> schedule = entries.stream().map(e -> new WorkSchedule(Ids.newId(), employeeId,
                e.dayOfWeek(), e.startTime(), e.endTime(), e.validFrom(), e.validTo())).toList();
        repository.replaceWeekly(employeeId, schedule);
        return repository.findWeekly(employeeId);
    }

    @Transactional
    public ScheduleException addException(UUID employeeId, LocalDate date, ScheduleException.Type type,
            LocalTime start, LocalTime end) {
        repository.findEmployee(employeeId).orElseThrow(() -> new NotFoundException("Nie znaleziono pracownika."));
        if (type == ScheduleException.Type.EXTRA_SHIFT
                && (start == null || end == null || !end.isAfter(start))) {
            throw new ValidationException("Dodatkowa zmiana wymaga godzin, a koniec musi być po początku.");
        }
        ScheduleException exception = new ScheduleException(Ids.newId(), employeeId, date, type,
                type == ScheduleException.Type.ABSENCE ? null : start,
                type == ScheduleException.Type.ABSENCE ? null : end);
        repository.insertException(exception);
        return exception;
    }

    @Transactional
    public void deleteException(UUID employeeId, UUID exceptionId) {
        if (!repository.deleteException(employeeId, exceptionId)) {
            throw new NotFoundException("Nie znaleziono wyjątku grafiku.");
        }
    }

    private static void validateWindow(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("Data końca nie może być przed datą początku.");
        }
        if (from.plusDays(MAX_WINDOW_DAYS).isBefore(to)) {
            throw new ValidationException("Okno zapytania może mieć najwyżej " + MAX_WINDOW_DAYS + " dni.");
        }
    }

    private static EmployeeInfo info(Employee e) {
        return new EmployeeInfo(e.id(), e.userId(), e.type(), e.firstName(), e.lastName(), e.specialization(),
                e.active());
    }
}
