package pl.przychodnia360.appointments.api;

import java.time.Instant;
import java.util.UUID;
import pl.przychodnia360.appointments.domain.AppointmentStatus;

/** Widoki wizyt zwracane przez API. Widok pacjenta nie zawiera danych innych pacjentów. */
public final class AppointmentViews {

    private AppointmentViews() {
    }

    /** Widok pacjenta: własne wizyty. */
    public record AppointmentView(UUID id, Instant startAt, Instant endAt, UUID serviceId, String serviceName,
            UUID employeeId, String employeeName, AppointmentStatus status) {
    }

    /** Widok pracowniczy (rejestrator, lekarz, personel): dodatkowo pacjent. */
    public record StaffAppointmentView(UUID id, Instant startAt, Instant endAt, UUID serviceId, String serviceName,
            UUID employeeId, String employeeName, UUID patientId, String patientName, AppointmentStatus status) {
    }
}
