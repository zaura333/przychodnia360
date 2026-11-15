package pl.przychodnia360.common.events;

import java.util.Set;
import pl.przychodnia360.ClinicCoreIntegrationTest;
import pl.przychodnia360.testsupport.contract.DomainEventSchemaContract;

/** Kontrakt zdarzeń z p360-test-support: scheduling publikuje zdarzenia wizyt (iam, catalog i patient mają własne testy). */
@ClinicCoreIntegrationTest
class DomainEventSchemaTest extends DomainEventSchemaContract {

    @Override
    protected Set<String> producedServices() {
        return Set.of("scheduling");
    }
}
