package pl.przychodnia360.appointments.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pl.przychodnia360.appointments.domain.Appointment;
import pl.przychodnia360.appointments.domain.AppointmentStatus;
import pl.przychodnia360.staff.api.TimeSlot;

public interface AppointmentRepository {

    /** Wstawia wizytę; kolizje zgłasza jako ConflictException (slot-taken lub patient-overlap). */
    void insert(Appointment appointment);

    /** Zapis zmiany terminu lub statusu z blokadą optymistyczną; kolizje jak przy insert. */
    void update(Appointment appointment);

    Optional<Appointment> findById(UUID id);

    /** Wizyty (każdy status z listy) zaczynające się w [from, to); filtry null są pomijane. */
    List<Appointment> search(UUID employeeId, UUID patientId, List<AppointmentStatus> statuses, Instant from,
            Instant to);

    /** Zajęte przedziały wykonawcy (wizyty SCHEDULED i COMPLETED) nakładające się na [from, to). */
    List<TimeSlot> busyIntervals(UUID employeeId, Instant from, Instant to);

    long countScheduledAfter(UUID employeeId, Instant now);
}
