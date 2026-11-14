package pl.przychodnia360.appointments.domain;

import java.time.Instant;
import java.util.UUID;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.UnprocessableException;
import pl.przychodnia360.staff.api.TimeSlot;

/** Wizyta: pacjent, wykonawca, usługa i przedział czasu [start, end). */
public record Appointment(UUID id, UUID patientId, UUID employeeId, UUID serviceId, TimeSlot slot,
        AppointmentStatus status, UUID bookedByUserId, UUID cancelledByUserId, Instant cancelledAt, long version) {

    public Instant startAt() {
        return slot.start();
    }

    public Instant endAt() {
        return slot.end();
    }

    /** Odwołanie: tylko zaplanowana wizyta, która jeszcze się nie rozpoczęła. */
    public Appointment cancel(UUID byUserId, Instant now) {
        if (status == AppointmentStatus.CANCELLED) {
            throw new ConflictException("already-cancelled", "Wizyta została już odwołana.");
        }
        if (status != AppointmentStatus.SCHEDULED) {
            throw new UnprocessableException("appointment-not-scheduled", "Tej wizyty nie można odwołać.");
        }
        if (!slot.start().isAfter(now)) {
            throw new UnprocessableException("appointment-started", "Wizyta już się rozpoczęła lub minęła.");
        }
        return new Appointment(id, patientId, employeeId, serviceId, slot, AppointmentStatus.CANCELLED,
                bookedByUserId, byUserId, now, version);
    }

    /** Przesunięcie na nowy początek, z zachowaniem czasu trwania. */
    public Appointment rescheduleTo(Instant newStart, Instant now) {
        if (status != AppointmentStatus.SCHEDULED) {
            throw new UnprocessableException("appointment-not-scheduled", "Tej wizyty nie można przesunąć.");
        }
        if (!slot.start().isAfter(now)) {
            throw new UnprocessableException("appointment-started", "Wizyta już się rozpoczęła lub minęła.");
        }
        return new Appointment(id, patientId, employeeId, serviceId,
                new TimeSlot(newStart, newStart.plus(slot.duration())), status, bookedByUserId, cancelledByUserId,
                cancelledAt, version);
    }
}
