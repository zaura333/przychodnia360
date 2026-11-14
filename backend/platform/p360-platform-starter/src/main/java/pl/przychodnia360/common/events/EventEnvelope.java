package pl.przychodnia360.common.events;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Koperta zdarzenia przesyłanego między usługami (kontrakt w api/events/envelope.schema.json). Niesie wyłącznie
 * identyfikatory i dane techniczne; {@code tenantId} jest obowiązkowy (zdarzenie bez niego trafia do DLQ).
 * {@code traceparent} (W3C) wypełnia observability (fala 1.6); do tego czasu może być null.
 */
public record EventEnvelope(UUID eventId, String type, int schemaVersion, UUID tenantId, UUID aggregateId,
        Instant occurredAt, String traceparent, JsonNode payload) {
}
