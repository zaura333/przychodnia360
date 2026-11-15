package pl.przychodnia360.patients;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import pl.przychodnia360.AbstractPatientApiTest;
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.patients.api.PatientRegistered;
import pl.przychodnia360.patients.api.PatientUpdated;
import pl.przychodnia360.patients.application.PatientService;

/** 0R.6: zdarzenie domenowe trafia do procesu po commit i niesie tylko identyfikatory. */
@RecordApplicationEvents
class PatientRegisteredEventTest extends AbstractPatientApiTest {

    @Autowired
    PatientFacade patientsApi;

    @Autowired
    ApplicationEvents events;

    @Autowired
    PatientService patientService;

    @Test
    void publishesPatientRegisteredAfterCommit() {
        UUID tenant = newTenant();
        UUID patientId = inTenantResult(tenant,
                () -> patientsApi.create("Anna", "Nowak", LocalDate.of(1990, 1, 1), null));

        assertThat(events.stream(PatientRegistered.class)).singleElement().satisfies(e -> {
            assertThat(e.tenantId()).isEqualTo(tenant);
            assertThat(e.aggregateId()).isEqualTo(patientId);
            assertThat(e.toString()).doesNotContain("Anna", "Nowak");
        });
    }

    @Test
    void publishesPatientUpdatedWithoutPersonalData() {
        UUID tenant = newTenant();
        UUID patientId = inTenantResult(tenant,
                () -> patientsApi.create("Anna", "Nowak", LocalDate.of(1990, 1, 1), null));
        inTenantResult(tenant, () -> patientService.updateContactEmail(patientId, "anna@example.test"));

        assertThat(events.stream(PatientUpdated.class)).singleElement().satisfies(e -> {
            assertThat(e.tenantId()).isEqualTo(tenant);
            assertThat(e.aggregateId()).isEqualTo(patientId);
            assertThat(e.toString()).doesNotContain("anna@example.test", "Nowak");
        });
    }
}
