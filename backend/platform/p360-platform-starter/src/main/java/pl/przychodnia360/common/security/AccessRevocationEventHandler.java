package pl.przychodnia360.common.security;

import org.springframework.stereotype.Component;
import pl.przychodnia360.common.events.EventEnvelope;
import pl.przychodnia360.common.events.EventHandler;

/**
 * Konsument {@code UserDeactivated} w usługach innych niż iam (ADR-M04): dezaktywacja konta odcina dostęp od razu,
 * a nie po wygaśnięciu tokenu. Zdarzenie niesie identyfikator użytkownika w {@code aggregateId}. Idempotentny:
 * powtórne odwołanie tylko przesuwa znacznik czasu.
 */
@Component
class AccessRevocationEventHandler implements EventHandler {

    private final AccessRevocations revocations;

    AccessRevocationEventHandler(AccessRevocations revocations) {
        this.revocations = revocations;
    }

    @Override
    public String eventType() {
        return "UserDeactivated";
    }

    @Override
    public void handle(EventEnvelope event) {
        revocations.revoke(event.aggregateId());
    }
}
