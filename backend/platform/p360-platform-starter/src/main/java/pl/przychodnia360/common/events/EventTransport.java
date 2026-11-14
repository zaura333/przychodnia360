package pl.przychodnia360.common.events;

/**
 * Wysyłka kopert poza proces. Implementacje: SNS (fala 1.4) i {@link LoggingEventTransport} jako domyślna,
 * gdy broker nie jest skonfigurowany. Metoda ma rzucić wyjątek, jeśli wysyłka się nie powiodła: przekaźnik
 * ponowi próbę i zachowa kolejność.
 */
public interface EventTransport {

    void publish(EventEnvelope envelope);
}
