package pl.przychodnia360.shared;

import java.time.Instant;

/** Znacznik zdarzenia domenowego. Konkretne zdarzenia powstają w modułach domenowych. */
public interface DomainEvent {

    Instant occurredAt();
}
