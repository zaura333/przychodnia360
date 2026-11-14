package pl.przychodnia360.catalog.api;

import java.time.Instant;
import java.util.UUID;
import pl.przychodnia360.shared.DomainEvent;

/** Dezaktywowano usługę (usunięcie logiczne). Tylko identyfikatory. */
public record ServiceDeactivated(UUID tenantId, UUID aggregateId, Instant occurredAt) implements DomainEvent {
}
