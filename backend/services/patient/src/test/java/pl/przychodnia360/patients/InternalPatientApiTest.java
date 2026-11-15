package pl.przychodnia360.patients;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractPatientApiTest;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.patients.api.PatientFacade;

/** Wewnętrzne API pacjentów: batch dla personelu, pacjent tylko własny rekord, izolacja placówek. */
class InternalPatientApiTest extends AbstractPatientApiTest {

    @Autowired PatientFacade patients;

    private UUID patient(UUID tenant, String first) {
        return inTenantResult(tenant, () -> patients.create(first, "Testowy", LocalDate.of(1990, 1, 1), null));
    }

    @Test
    void staffReadsBatchAndUnknownIdsAreSkipped() {
        UUID tenant = newTenant();
        UUID a = patient(tenant, "Anna");
        UUID b = patient(tenant, "Beata");

        Resp r = fetch("/api/v1/internal/patients?ids=" + a + "," + b + "," + UUID.randomUUID(),
                tokenFor(tenant, Role.RECEPTIONIST));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().findValuesAsString("firstName")).containsExactlyInAnyOrder("Anna", "Beata");
        assertThat(fetch("/api/v1/internal/patients/" + a, tokenFor(tenant, Role.DOCTOR)).json().get("lastName").asString())
                .isEqualTo("Testowy");
        assertThat(fetch("/api/v1/internal/patients/" + UUID.randomUUID(), tokenFor(tenant, Role.DOCTOR)).status())
                .isEqualTo(404);
    }

    @Test
    void patientSeesOnlyTheOwnRecord() {
        UUID tenant = newTenant();
        UUID mine = patient(tenant, "Moja");
        UUID other = patient(tenant, "Cudza");
        String token = tokenFor(tenant, Role.PATIENT, mine);

        assertThat(fetch("/api/v1/internal/patients?ids=" + mine, token).json().findValuesAsString("firstName"))
                .containsExactly("Moja");
        assertThat(fetch("/api/v1/internal/patients?ids=" + mine + "," + other, token).status()).isEqualTo(403);
        assertThat(fetch("/api/v1/internal/patients/" + other, token).status()).isEqualTo(403);
        assertThat(fetch("/api/v1/internal/patients/" + mine, tokenFor(tenant, Role.PATIENT)).status())
                .as("pacjent bez rekordu (brak pid)").isEqualTo(403);
    }

    @Test
    void anotherTenantAndAnonymousSeeNothing() {
        UUID a = newTenant();
        UUID b = newTenant();
        UUID id = patient(a, "Prywatna");

        assertThat(fetch("/api/v1/internal/patients?ids=" + id, tokenFor(b, Role.RECEPTIONIST)).json()).isEmpty();
        assertThat(fetch("/api/v1/internal/patients/" + id, tokenFor(b, Role.RECEPTIONIST)).status()).isEqualTo(404);
        assertThat(fetch("/api/v1/internal/patients?ids=" + id, null).status()).isEqualTo(401);
    }
}
