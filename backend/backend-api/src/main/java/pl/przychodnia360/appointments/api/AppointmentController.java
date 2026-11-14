package pl.przychodnia360.appointments.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.appointments.api.AppointmentViews.AppointmentView;
import pl.przychodnia360.appointments.api.AppointmentViews.StaffAppointmentView;
import pl.przychodnia360.appointments.application.AppointmentQueryService;
import pl.przychodnia360.appointments.application.BookingService;
import pl.przychodnia360.appointments.application.BookingService.BookCommand;
import pl.przychodnia360.appointments.application.SlotQueryService;
import pl.przychodnia360.appointments.domain.AppointmentStatus;
import pl.przychodnia360.identity.api.AuthenticatedUser;
import pl.przychodnia360.staff.api.TimeSlot;

/** Rezerwacje: umawianie (pacjent, rejestrator), listy wizyt, przesunięcie i odwołanie przez rejestratora, sloty. */
@RestController
public class AppointmentController {

    public record BookRequest(@NotNull UUID serviceId, @NotNull UUID employeeId, @NotNull Instant startAt,
            UUID patientId) {
    }

    public record RescheduleRequest(@NotNull Instant startAt) {
    }

    public record SlotResponse(Instant start, Instant end) {
    }

    private final BookingService booking;
    private final AppointmentQueryService queries;
    private final SlotQueryService slots;

    AppointmentController(BookingService booking, AppointmentQueryService queries, SlotQueryService slots) {
        this.booking = booking;
        this.queries = queries;
        this.slots = slots;
    }

    @PostMapping(value = "/api/v1/appointments", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has(authentication, 'APPOINTMENT_BOOK_OWN') or @permissions.has(authentication, 'APPOINTMENT_BOOK_ANY')")
    @Operation(operationId = "bookAppointment", summary = "Umówienie wizyty",
            description = "Dostęp: APPOINTMENT_BOOK_OWN (PATIENT, pacjent z tokenu) lub APPOINTMENT_BOOK_ANY (RECEPTIONIST, patientId w ciele). "
                    + "409 kolizja terminu, 422 termin w przeszłości, poza grafikiem lub wykonawca niezgodny z usługą.")
    public StaffAppointmentView book(@Valid @RequestBody BookRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return booking.book(new BookCommand(request.serviceId(), request.employeeId(), request.startAt(),
                request.patientId()), user);
    }

    @GetMapping(value = "/api/v1/me/appointments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'APPOINTMENT_READ_OWN')")
    @Operation(operationId = "listMyAppointments", summary = "Własne wizyty pacjenta",
            description = "Dostęp: APPOINTMENT_READ_OWN (PATIENT). Domyślnie wizyty od teraz.")
    public List<AppointmentView> mine(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) List<AppointmentStatus> status,
            @RequestParam(required = false) Instant from) {
        return queries.forPatient(user.patientId(), status, from == null ? Instant.now() : from);
    }

    @PostMapping(value = "/api/v1/appointments/{id}/reschedule", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'APPOINTMENT_RESCHEDULE_ANY')")
    @Operation(operationId = "rescheduleAppointment", summary = "Przesunięcie wizyty",
            description = "Dostęp: APPOINTMENT_RESCHEDULE_ANY (RECEPTIONIST).")
    public StaffAppointmentView reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return booking.reschedule(id, request.startAt(), user);
    }

    @PostMapping("/api/v1/appointments/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissions.has(authentication, 'APPOINTMENT_CANCEL_ANY')")
    @Operation(operationId = "cancelAppointment", summary = "Odwołanie wizyty przez rejestratora",
            description = "Dostęp: APPOINTMENT_CANCEL_ANY (RECEPTIONIST), bez limitu 24 h. Ścieżka pacjenta dochodzi w 1c.")
    public void cancel(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        booking.cancel(id, user);
    }

    @GetMapping(value = "/api/v1/reception/appointments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'APPOINTMENT_READ_ALL')")
    @Operation(operationId = "listReceptionAppointments", summary = "Terminarz placówki",
            description = "Dostęp: APPOINTMENT_READ_ALL (RECEPTIONIST, TENANT_ADMIN). Okno do 32 dni, daty lokalne placówki.")
    public List<StaffAppointmentView> reception(@RequestParam(required = false) UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<AppointmentStatus> status) {
        return queries.schedule(employeeId, from, to, status);
    }

    @GetMapping(value = "/api/v1/employees/{id}/slots", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SLOT_READ')")
    @Operation(operationId = "listFreeSlots", summary = "Wolne terminy wykonawcy dla usługi",
            description = "Dostęp: SLOT_READ (PATIENT, RECEPTIONIST). Okno do 32 dni, daty lokalne placówki.")
    public List<SlotResponse> freeSlots(@PathVariable UUID id, @RequestParam UUID serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return slots.freeSlots(id, serviceId, from, to).stream().map(AppointmentController::slot).toList();
    }

    private static SlotResponse slot(TimeSlot s) {
        return new SlotResponse(s.start(), s.end());
    }
}
