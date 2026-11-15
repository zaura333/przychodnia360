package pl.przychodnia360.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractPatientApiTest;
import pl.przychodnia360.common.events.EventEnvelope;
import pl.przychodnia360.common.events.EventHandler;
import tools.jackson.databind.node.JsonNodeFactory;

/** S2: zdarzenie UserDeactivated z iam odcina dostęp w tej usłudze od razu (bez czekania na wygaśnięcie tokenu). */
class AccessRevocationTest extends AbstractPatientApiTest {

    @Autowired
    List<EventHandler> handlers;

    @Test
    void userDeactivatedEventRevokesTheTokenImmediately() {
        UUID tenant = newTenant();
        UUID userId = UUID.randomUUID();
        String token = tokenForUser(tenant, userId);
        assertThat(fetch("/api/v1/me/patient", token).status()).as("token ważny").isNotEqualTo(401);

        EventHandler handler = handlers.stream().filter(h -> h.eventType().equals("UserDeactivated")).findFirst()
                .orElseThrow();
        handler.handle(new EventEnvelope(UUID.randomUUID(), "UserDeactivated", 1, tenant, userId, Instant.now(), null,
                JsonNodeFactory.instance.objectNode()));

        assertThat(fetch("/api/v1/me/patient", token).status()).isEqualTo(401);
        assertThat(fetch("/api/v1/me/patient", tokenForUser(tenant, UUID.randomUUID())).status())
                .as("inny użytkownik nie jest odwołany").isNotEqualTo(401);
    }
}
