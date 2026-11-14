package pl.przychodnia360.common.events;

import java.util.Set;
import pl.przychodnia360.ClinicCoreIntegrationTest;
import pl.przychodnia360.testsupport.contract.DomainEventSchemaContract;

/** Kontrakt zdarzeń z p360-test-support: clinic-core publikuje zdarzenia wszystkich czterech przyszłych usług. */
@ClinicCoreIntegrationTest
class DomainEventSchemaTest extends DomainEventSchemaContract {

    @Override
    protected Set<String> producedServices() {
        return Set.of("iam", "patient", "catalog", "scheduling");
    }
}
