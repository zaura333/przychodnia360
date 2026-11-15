package pl.przychodnia360.catalog.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pl.przychodnia360.shared.DomainEvent;

/** Zmieniono usługę. Dane techniczne usługi (bez danych osobowych); konsument aktualizuje kopię {@code service_ref}. */
public record ServiceUpdated(UUID tenantId, UUID aggregateId, String name, BigDecimal price, int durationMinutes,
        String performerType, boolean active, Instant occurredAt) implements DomainEvent {
}
