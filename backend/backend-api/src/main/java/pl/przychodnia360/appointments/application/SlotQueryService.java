package pl.przychodnia360.appointments.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.przychodnia360.appointments.domain.BookingPolicy;
import pl.przychodnia360.catalog.api.CatalogFacade;
import pl.przychodnia360.catalog.api.CatalogFacade.ServiceInfo;
import pl.przychodnia360.common.time.ClinicTime;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ValidationException;
import pl.przychodnia360.staff.api.StaffFacade;
import pl.przychodnia360.staff.api.StaffFacade.EmployeeInfo;
import pl.przychodnia360.staff.api.TimeSlot;

/**
 * Wolne terminy: przedziały pracy minus zajęte wizyty, pocięte na sloty o stałym kroku (siatka godzinowa UTC,
 * zgodna z {@link BookingPolicy#assertOnGrid}) i długości usługi. Odczyt jest optymistyczny: o rezerwacji
 * rozstrzyga ograniczenie w bazie.
 */
@Service
public class SlotQueryService {

    public static final int MAX_WINDOW_DAYS = 31;

    private final StaffFacade staff;
    private final CatalogFacade catalog;
    private final AppointmentRepository repository;
    private final Clock clock;
    private final int stepMinutes;

    SlotQueryService(StaffFacade staff, CatalogFacade catalog, AppointmentRepository repository, Clock clock,
            @Value("${appointments.slot-step-minutes:15}") int stepMinutes) {
        this.staff = staff;
        this.catalog = catalog;
        this.repository = repository;
        this.clock = clock;
        this.stepMinutes = stepMinutes;
    }

    @Transactional(readOnly = true)
    public List<TimeSlot> freeSlots(UUID employeeId, UUID serviceId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_WINDOW_DAYS).isBefore(to)) {
            throw new ValidationException("Okno zapytania musi mieć od 1 do " + (MAX_WINDOW_DAYS + 1) + " dni.");
        }
        EmployeeInfo employee = staff.employee(employeeId)
                .orElseThrow(() -> new NotFoundException("Nie znaleziono wykonawcy."));
        ServiceInfo service = catalog.service(serviceId)
                .orElseThrow(() -> new NotFoundException("Nie znaleziono usługi."));
        BookingPolicy.assertPerformerMatches(employee, service);

        Instant windowStart = ClinicTime.startOfDay(from);
        Instant windowEnd = ClinicTime.endOfDay(to);
        List<TimeSlot> busy = repository.busyIntervals(employeeId, windowStart, windowEnd);
        List<TimeSlot> free = new ArrayList<>();
        for (TimeSlot work : staff.workingIntervals(employeeId, from, to)) {
            free.addAll(subtract(work, busy));
        }
        Instant now = clock.instant();
        long durationSeconds = service.durationMinutes() * 60L;
        List<TimeSlot> slots = new ArrayList<>();
        for (TimeSlot interval : free) {
            for (Instant start = alignUp(interval.start()); !start.plusSeconds(durationSeconds).isAfter(interval.end());
                    start = start.plusSeconds(stepMinutes * 60L)) {
                if (start.isAfter(now)) {
                    slots.add(new TimeSlot(start, start.plusSeconds(durationSeconds)));
                }
            }
        }
        return slots;
    }

    /** Odejmuje zajęte przedziały od przedziału pracy; wynik to rozłączne przedziały wolne. */
    static List<TimeSlot> subtract(TimeSlot work, List<TimeSlot> busy) {
        List<TimeSlot> result = new ArrayList<>();
        Instant cursor = work.start();
        for (TimeSlot b : busy) {
            if (!b.overlaps(work)) {
                continue;
            }
            if (b.start().isAfter(cursor)) {
                result.add(new TimeSlot(cursor, b.start()));
            }
            if (b.end().isAfter(cursor)) {
                cursor = b.end();
            }
        }
        if (cursor.isBefore(work.end())) {
            result.add(new TimeSlot(cursor, work.end()));
        }
        return result;
    }

    /** Najbliższy punkt siatki (minuty wielokrotne kroku, zero sekund) nie wcześniejszy niż instant. */
    private Instant alignUp(Instant instant) {
        long stepSeconds = stepMinutes * 60L;
        long epoch = instant.atOffset(ZoneOffset.UTC).toEpochSecond();
        long aligned = ((epoch + stepSeconds - 1) / stepSeconds) * stepSeconds;
        return Instant.ofEpochSecond(aligned);
    }
}
