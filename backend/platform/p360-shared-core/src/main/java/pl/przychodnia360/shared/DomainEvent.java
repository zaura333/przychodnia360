package pl.przychodnia360.shared;

import java.time.Instant;
import java.util.UUID;

/**
 * Zdarzenie domenowe (fakt, który się wydarzył). Koperta wspólna dla zdarzeń w procesie i, od fali 1, dla outboxu:
 * placówka, agregat i czas. Zdarzenia niosą wyłącznie identyfikatory i dane techniczne, nigdy danych osobowych
 * (ADR-M10). Konkretne zdarzenia powstają w pakietach {@code api} modułów domenowych.
 */
public interface DomainEvent {

    UUID tenantId();

    UUID aggregateId();

    Instant occurredAt();

    /** Nazwa typu zdarzenia w kopercie (domyślnie nazwa klasy). */
    default String type() {
        return getClass().getSimpleName();
    }
}
