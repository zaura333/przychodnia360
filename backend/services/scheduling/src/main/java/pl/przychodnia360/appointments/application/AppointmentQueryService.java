package pl.przychodnia360.appointments.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.przychodnia360.appointments.api.AppointmentViews.AppointmentView;
import pl.przychodnia360.appointments.api.AppointmentViews.StaffAppointmentView;
import pl.przychodnia360.appointments.domain.Appointment;
import pl.przychodnia360.appointments.domain.AppointmentStatus;
import pl.przychodnia360.common.time.ClinicTime;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ValidationException;

@Service
public class AppointmentQueryService {

    private static final int MAX_WINDOW_DAYS = 31;

    private final AppointmentRepository repository;
    private final ViewAssembler views;

    AppointmentQueryService(AppointmentRepository repository, ViewAssembler views) {
        this.repository = repository;
        this.views = views;
    }

    /** Własne wizyty pacjenta od podanego momentu (FR-12), opcjonalnie tylko wybrane statusy. */
    @Transactional(readOnly = true)
    public List<AppointmentView> forPatient(UUID patientId, List<AppointmentStatus> statuses, Instant from) {
        if (patientId == null) {
            throw new NotFoundException("Nie znaleziono danych pacjenta.");
        }
        return views.patientViews(repository.search(null, patientId, statuses, from, null));
    }

    /** Terminarz placówki w oknie dni lokalnych [from, to] (FR-52). */
    @Transactional(readOnly = true)
    public List<StaffAppointmentView> schedule(UUID employeeId, LocalDate from, LocalDate to,
            List<AppointmentStatus> statuses) {
        validateWindow(from, to);
        List<Appointment> found = repository.search(employeeId, null, statuses, ClinicTime.startOfDay(from),
                ClinicTime.endOfDay(to));
        return views.staffViews(found);
    }

    /** Wizyty wykonawcy (SCHEDULED i COMPLETED) w oknie dni lokalnych: dla lekarza i personelu (FR-14, FR-53). */
    @Transactional(readOnly = true)
    public List<StaffAppointmentView> forEmployee(UUID employeeId, LocalDate from, LocalDate to) {
        if (employeeId == null) {
            throw new NotFoundException("Konto nie jest powiązane z pracownikiem.");
        }
        return schedule(employeeId, from, to, List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED));
    }

    private static void validateWindow(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_WINDOW_DAYS).isBefore(to)) {
            throw new ValidationException("Okno zapytania musi mieć od 1 do " + (MAX_WINDOW_DAYS + 1) + " dni.");
        }
    }
}
