package pl.przychodnia360.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import pl.przychodnia360.AbstractCatalogApiTest;
import pl.przychodnia360.catalog.api.PerformerType;
import pl.przychodnia360.catalog.api.ServiceCreated;
import pl.przychodnia360.catalog.api.ServiceDeactivated;
import pl.przychodnia360.catalog.api.ServiceUpdated;
import pl.przychodnia360.catalog.application.ServiceCatalogService;
import pl.przychodnia360.catalog.application.ServiceCatalogService.ServiceCommand;

/** S1: katalog publikuje fakty o usługach, z których konsument buduje kopię {@code service_ref}. */
@RecordApplicationEvents
class CatalogEventsTest extends AbstractCatalogApiTest {

    @Autowired
    ServiceCatalogService catalog;

    @Autowired
    ApplicationEvents events;

    @Test
    void createUpdateAndDeactivatePublishEventsCarryingTheServiceState() {
        UUID tenant = newTenant();
        UUID serviceId = inTenantResult(tenant, () -> {
            UUID category = catalog.createCategory("Konsultacje").id();
            UUID id = catalog.create(new ServiceCommand(category, " Konsultacja ", null, new BigDecimal("150.00"), 30,
                    PerformerType.DOCTOR)).id();
            catalog.update(id, new ServiceCommand(category, "Konsultacja", null, new BigDecimal("160.00"), 45,
                    PerformerType.DOCTOR));
            catalog.deactivate(id);
            catalog.deactivate(id);
            return id;
        });

        assertThat(events.stream(ServiceCreated.class)).singleElement().satisfies(e -> {
            assertThat(e.tenantId()).isEqualTo(tenant);
            assertThat(e.aggregateId()).isEqualTo(serviceId);
            assertThat(e.name()).isEqualTo("Konsultacja");
            assertThat(e.price()).isEqualByComparingTo("150.00");
            assertThat(e.durationMinutes()).isEqualTo(30);
            assertThat(e.performerType()).isEqualTo("DOCTOR");
            assertThat(e.active()).isTrue();
        });
        assertThat(events.stream(ServiceUpdated.class)).singleElement().satisfies(e -> {
            assertThat(e.price()).isEqualByComparingTo("160.00");
            assertThat(e.durationMinutes()).isEqualTo(45);
        });
        assertThat(events.stream(ServiceDeactivated.class)).as("druga dezaktywacja nie publikuje zdarzenia")
                .singleElement().satisfies(e -> assertThat(e.aggregateId()).isEqualTo(serviceId));
    }
}
