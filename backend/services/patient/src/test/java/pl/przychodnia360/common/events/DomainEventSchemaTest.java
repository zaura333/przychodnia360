package pl.przychodnia360.common.events;

import java.util.Set;
import pl.przychodnia360.PatientIntegrationTest;
import pl.przychodnia360.testsupport.contract.DomainEventSchemaContract;

/** Kontrakt zdarzeń z p360-test-support: patient publikuje zdarzenia usługi `patient`. */
@PatientIntegrationTest
class DomainEventSchemaTest extends DomainEventSchemaContract {

    @Override
    protected Set<String> producedServices() {
        return Set.of("patient");
    }
}
