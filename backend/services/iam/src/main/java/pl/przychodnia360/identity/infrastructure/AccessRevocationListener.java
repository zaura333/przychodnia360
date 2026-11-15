package pl.przychodnia360.identity.infrastructure;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.przychodnia360.common.security.AccessRevocations;
import pl.przychodnia360.identity.api.event.UserDeactivated;

/** Dezaktywacja konta odcina dostęp od razu: odwołanie trafia na listę sprawdzaną przy weryfikacji tokenu. */
@Component
class AccessRevocationListener {

    private final AccessRevocations revocations;

    AccessRevocationListener(AccessRevocations revocations) {
        this.revocations = revocations;
    }

    @EventListener
    void on(UserDeactivated event) {
        revocations.revoke(event.aggregateId());
    }
}
