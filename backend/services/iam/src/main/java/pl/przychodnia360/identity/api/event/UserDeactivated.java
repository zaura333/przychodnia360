package pl.przychodnia360.identity.api.event;

import java.time.Instant;
import java.util.UUID;
import pl.przychodnia360.shared.DomainEvent;

/** Konto użytkownika zdezaktywowane (odwołanie dostępu). Tylko identyfikatory (bez danych osobowych). */
public record UserDeactivated(UUID tenantId, UUID aggregateId, Instant occurredAt) implements DomainEvent {
}
