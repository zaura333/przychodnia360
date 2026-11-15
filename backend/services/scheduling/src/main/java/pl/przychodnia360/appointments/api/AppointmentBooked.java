package pl.przychodnia360.appointments.api;

import java.time.Instant;
import java.util.UUID;
import pl.przychodnia360.shared.DomainEvent;

/** Wizyta zarezerwowana. Tylko identyfikatory (bez danych osobowych). */
public record AppointmentBooked(UUID tenantId, UUID aggregateId, UUID patientId, UUID employeeId, UUID serviceId, Instant startAt, Instant occurredAt) implements DomainEvent {
}
