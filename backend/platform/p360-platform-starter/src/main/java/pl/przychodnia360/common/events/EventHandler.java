package pl.przychodnia360.common.events;

/**
 * Obsługa zdarzenia jednego typu po stronie konsumenta. Wywoływana w transakcji z kontekstem placówki zdarzenia
 * i z inboxem ({@link InboxGuard}), więc może bezpiecznie zapisywać dane; ma być idempotentna także sama w sobie.
 */
public interface EventHandler {

    /** Typ zdarzenia (np. {@code AppointmentBooked}). */
    String eventType();

    void handle(EventEnvelope event);
}
