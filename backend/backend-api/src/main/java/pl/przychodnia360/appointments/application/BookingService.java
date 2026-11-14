package pl.przychodnia360.appointments.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.przychodnia360.appointments.api.AppointmentViews.AppointmentView;
import pl.przychodnia360.appointments.api.AppointmentViews.StaffAppointmentView;
import pl.przychodnia360.appointments.domain.Appointment;
import pl.przychodnia360.appointments.domain.AppointmentStatus;
import pl.przychodnia360.appointments.domain.BookingPolicy;
import pl.przychodnia360.catalog.api.CatalogFacade;
import pl.przychodnia360.catalog.api.CatalogFacade.ServiceInfo;
import pl.przychodnia360.common.time.ClinicTime;
import pl.przychodnia360.identity.api.AuthenticatedUser;
import pl.przychodnia360.identity.api.Permission;
import pl.przychodnia360.identity.api.PermissionPolicy;
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.shared.ForbiddenException;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.UnprocessableException;
import pl.przychodnia360.shared.ValidationException;
import pl.przychodnia360.staff.api.StaffFacade;
import pl.przychodnia360.staff.api.StaffFacade.EmployeeInfo;
import pl.przychodnia360.staff.api.TimeSlot;

/**
 * Rezerwacja, przesunięcie i odwołanie wizyt. Gwarancją braku kolizji jest ograniczenie wykluczające w bazie;
 * reguły z {@link BookingPolicy} dają czytelne błędy przed zapisem (plan 1a, 7.1).
 */
@Service
public class BookingService {

    /** Dane rezerwacji; patientId podaje tylko rejestrator, dla pacjenta wynika z tokenu. */
    public record BookCommand(UUID serviceId, UUID employeeId, Instant startAt, UUID patientId) {
    }

    private final AppointmentRepository repository;
    private final CatalogFacade catalog;
    private final StaffFacade staff;
    private final PatientFacade patients;
    private final ViewAssembler views;
    private final Clock clock;
    private final int stepMinutes;

    BookingService(AppointmentRepository repository, CatalogFacade catalog, StaffFacade staff, PatientFacade patients,
            ViewAssembler views, Clock clock, @Value("${appointments.slot-step-minutes:15}") int stepMinutes) {
        this.repository = repository;
        this.catalog = catalog;
        this.staff = staff;
        this.patients = patients;
        this.views = views;
        this.clock = clock;
        this.stepMinutes = stepMinutes;
    }

    @Transactional
    public StaffAppointmentView book(BookCommand command, AuthenticatedUser user) {
        UUID patientId = resolvePatient(command, user);
        patients.find(patientId).orElseThrow(() -> new NotFoundException("Nie znaleziono pacjenta."));
        ServiceInfo service = catalog.service(command.serviceId())
                .orElseThrow(() -> new NotFoundException("Nie znaleziono usługi."));
        EmployeeInfo employee = staff.employee(command.employeeId())
                .orElseThrow(() -> new NotFoundException("Nie znaleziono wykonawcy."));

        BookingPolicy.assertPerformerMatches(employee, service);
        TimeSlot slot = checkedSlot(command.startAt(), service.durationMinutes(), employee.id());

        Appointment appointment = new Appointment(Ids.newId(), patientId, employee.id(), service.id(), slot,
                AppointmentStatus.SCHEDULED, user.userId(), null, null, 0);
        repository.insert(appointment);
        return views.staffViews(java.util.List.of(appointment)).getFirst();
    }

    /** Przesunięcie przez rejestratora; ten sam czas trwania, te same reguły co przy rezerwacji. */
    @Transactional
    public StaffAppointmentView reschedule(UUID id, Instant newStart, AuthenticatedUser user) {
        requirePermission(user, Permission.APPOINTMENT_RESCHEDULE_ANY);
        Appointment current = repository.findById(id).orElseThrow(() -> new NotFoundException("Nie znaleziono wizyty."));
        Appointment moved = current.rescheduleTo(newStart, clock.instant());
        checkedSlot(moved.startAt(), (int) moved.slot().duration().toMinutes(), moved.employeeId());
        repository.update(moved);
        return views.staffViews(java.util.List.of(repository.findById(id).orElseThrow())).getFirst();
    }

    /** Odwołanie przez rejestratora, bez limitu 24 h (FR-51). Ścieżka pacjenta z limitem dochodzi w 1c. */
    @Transactional
    public void cancel(UUID id, AuthenticatedUser user) {
        requirePermission(user, Permission.APPOINTMENT_CANCEL_ANY);
        Appointment current = repository.findById(id).orElseThrow(() -> new NotFoundException("Nie znaleziono wizyty."));
        repository.update(current.cancel(user.userId(), clock.instant()));
    }

    /** Termin w przyszłości, na siatce, w całości w dyżurze wykonawcy. */
    private TimeSlot checkedSlot(Instant start, int durationMinutes, UUID employeeId) {
        BookingPolicy.assertInFuture(start, clock.instant());
        BookingPolicy.assertOnGrid(start, stepMinutes);
        TimeSlot slot = new TimeSlot(start, start.plusSeconds(durationMinutes * 60L));
        var firstDay = ClinicTime.dateOf(slot.start());
        var lastDay = ClinicTime.dateOf(slot.end().minusNanos(1));
        BookingPolicy.assertFitsInShift(slot, staff.workingIntervals(employeeId, firstDay, lastDay));
        return slot;
    }

    private UUID resolvePatient(BookCommand command, AuthenticatedUser user) {
        if (PermissionPolicy.has(user, Permission.APPOINTMENT_BOOK_ANY)) {
            if (command.patientId() == null) {
                throw new ValidationException("Podaj pacjenta, dla którego umawiasz wizytę.");
            }
            return command.patientId();
        }
        if (PermissionPolicy.has(user, Permission.APPOINTMENT_BOOK_OWN)) {
            if (user.patientId() == null) {
                throw new UnprocessableException("no-patient-record", "Konto nie ma powiązanego rekordu pacjenta.");
            }
            if (command.patientId() != null && !command.patientId().equals(user.patientId())) {
                throw new ForbiddenException("Nie możesz umawiać wizyt za innego pacjenta.");
            }
            return user.patientId();
        }
        throw new ForbiddenException("Brak uprawnień do umawiania wizyt.");
    }

    private static void requirePermission(AuthenticatedUser user, Permission permission) {
        if (!PermissionPolicy.has(user, permission)) {
            throw new ForbiddenException("Brak uprawnień do tej operacji.");
        }
    }

    /** Widok pacjenta dla odpowiedzi tworzącej wizytę własną nie jest potrzebny; zostawiony dla spójności API. */
    public AppointmentView patientView(Appointment appointment) {
        return views.patientViews(java.util.List.of(appointment)).getFirst();
    }
}
