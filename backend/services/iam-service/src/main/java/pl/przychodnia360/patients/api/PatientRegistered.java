package pl.przychodnia360.patients.api;

import java.time.Instant;
import java.util.UUID;
import pl.przychodnia360.shared.DomainEvent;

/** Zarejestrowano pacjenta. Tylko identyfikatory (bez danych osobowych). */
public record PatientRegistered(UUID tenantId, UUID aggregateId, Instant occurredAt) implements DomainEvent {
}
