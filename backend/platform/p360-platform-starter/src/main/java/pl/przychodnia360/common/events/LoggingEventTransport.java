package pl.przychodnia360.common.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Domyślny transport bez brokera: zapisuje w logu typ i identyfikatory (bez ładunku). */
public class LoggingEventTransport implements EventTransport {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingEventTransport.class);

    @Override
    public void publish(EventEnvelope envelope) {
        LOG.info("Zdarzenie {} {} (agregat {}, placówka {}) nie wysłane: brak skonfigurowanego brokera",
                envelope.type(), envelope.eventId(), envelope.aggregateId(), envelope.tenantId());
    }
}
