package pl.przychodnia360.patients.api.event;

import java.time.Instant;
import java.util.UUID;
import pl.przychodnia360.shared.DomainEvent;

/** Zmieniono dane pacjenta. Tylko identyfikatory (bez danych osobowych): konsument dociąga dane zapytaniem. */
public record PatientUpdated(UUID tenantId, UUID aggregateId, Instant occurredAt) implements DomainEvent {
}
