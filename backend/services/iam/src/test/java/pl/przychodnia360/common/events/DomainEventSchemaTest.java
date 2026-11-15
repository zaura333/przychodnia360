package pl.przychodnia360.common.events;

import java.util.Set;
import pl.przychodnia360.IamIntegrationTest;
import pl.przychodnia360.identity.api.event.UserDeactivated;
import pl.przychodnia360.testsupport.contract.DomainEventSchemaContract;

/** Kontrakt zdarzeń z p360-test-support: iam publikuje zdarzenia kont (UserDeactivated). */
@IamIntegrationTest
class DomainEventSchemaTest extends DomainEventSchemaContract {

    @Override
    protected Set<String> producedServices() {
        return Set.of("iam");
    }
}
