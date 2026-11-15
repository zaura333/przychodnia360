package pl.przychodnia360.common.events;

import java.util.Set;
import pl.przychodnia360.CatalogIntegrationTest;
import pl.przychodnia360.testsupport.contract.DomainEventSchemaContract;

/** Kontrakt zdarzeń z p360-test-support: catalog publikuje zdarzenia usługi `catalog`. */
@CatalogIntegrationTest
class DomainEventSchemaTest extends DomainEventSchemaContract {

    @Override
    protected Set<String> producedServices() {
        return Set.of("catalog");
    }
}
