package pl.przychodnia360.patients;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractPatientApiTest;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.patients.api.PatientFacade;

/**
 * Wewnętrzne API pacjentów: tylko tożsamość usługi (token usługowy iam). Odczyt wsadowy, idempotentne założenie
 * pacjenta (saga rejestracji), powiązanie z kontem, izolacja placówek, odrzucenie ról użytkowników i obcej usługi.
 */
class InternalPatientApiTest extends AbstractPatientApiTest {

    @Autowired PatientFacade patients;

    private UUID patient(UUID tenant, String first) {
        return inTenantResult(tenant, () -> patients.create(first, "Testowy", LocalDate.of(1990, 1, 1), null));
    }

    private static String body(String first, UUID userId) {
        return "{\"firstName\":\"" + first + "\",\"lastName\":\"Nowak\",\"birthDate\":\"1990-05-17\","
                + "\"contactEmail\":\"x@example.test\",\"userId\":" + (userId == null ? "null" : "\"" + userId + "\"") + "}";
    }

    @Test
    void serviceReadsBatchAndUnknownIdsAreSkipped() {
        UUID tenant = newTenant();
        UUID a = patient(tenant, "Anna");
        UUID b = patient(tenant, "Beata");
        String token = serviceTokenFor(tenant);

        Resp r = fetch("/api/v1/internal/patients?ids=" + a + "," + b + "," + UUID.randomUUID(), token);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().findValuesAsString("firstName")).containsExactlyInAnyOrder("Anna", "Beata");
        assertThat(fetch("/api/v1/internal/patients/" + a, token).json().get("lastName").asString()).isEqualTo("Testowy");
        assertThat(fetch("/api/v1/internal/patients/" + UUID.randomUUID(), token).status()).isEqualTo(404);
    }

    @Test
    void userRolesAndTokensForAnotherServiceAreRejected() {
        UUID tenant = newTenant();
        UUID id = patient(tenant, "Anna");
        for (Role role : Role.values()) {
            if (role == Role.SERVICE || role == Role.PLATFORM_ADMIN) {
                continue;
            }
            assertThat(fetch("/api/v1/internal/patients/" + id, tokenFor(tenant, role, id)).status())
                    .as("odczyt, rola %s", role).isEqualTo(403);
            assertThat(call("PUT", "/api/v1/internal/patients/" + UUID.randomUUID(), tokenFor(tenant, role, id),
                    body("A", null)).status()).as("zapis, rola %s", role).isEqualTo(403);
        }
        assertThat(fetch("/api/v1/internal/patients/" + id, serviceTokenFor(tenant, "catalog")).status())
                .as("token usługowy dla innej usługi (aud)").isEqualTo(401);
        assertThat(fetch("/api/v1/internal/patients/" + id, null).status()).isEqualTo(401);
    }

    @Test
    void ensureIsIdempotentAndSetsTheAccountLink() {
        UUID tenant = newTenant();
        UUID id = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String token = serviceTokenFor(tenant);

        assertThat(call("PUT", "/api/v1/internal/patients/" + id, token, body("Ola", null)).status()).isEqualTo(204);
        assertThat(call("PUT", "/api/v1/internal/patients/" + id, token, body("Ola", null)).status())
                .as("powtórzenie niczego nie duplikuje").isEqualTo(204);
        assertThat(fetch("/api/v1/internal/patients?ids=" + id, token).json()).hasSize(1);
        assertThat(fetch("/api/v1/internal/patients/" + id, token).json().get("userId").isNull()).isTrue();

        assertThat(call("PUT", "/api/v1/internal/patients/" + id, token, body("Ola", user)).status()).isEqualTo(204);
        assertThat(fetch("/api/v1/internal/patients/" + id, token).json().get("userId").asString()).isEqualTo(user.toString());
        UUID other = UUID.randomUUID();
        assertThat(call("PUT", "/api/v1/internal/patients/" + id, token, body("Ola", other)).status()).isEqualTo(204);
        assertThat(fetch("/api/v1/internal/patients/" + id, token).json().get("userId").asString())
                .as("powiązanie ustawiane na konto z ostatniego wywołania").isEqualTo(other.toString());
    }

    @Test
    void ensureValidation() {
        UUID tenant = newTenant();
        String token = serviceTokenFor(tenant);

        assertThat(call("PUT", "/api/v1/internal/patients/" + UUID.randomUUID(), token,
                "{\"firstName\":\"\",\"lastName\":\"X\",\"birthDate\":\"1990-01-01\"}").status()).isEqualTo(400);
    }

    @Test
    void anotherTenantSeesNothingAndCannotWriteIntoTheRecord() {
        UUID a = newTenant();
        UUID b = newTenant();
        UUID id = patient(a, "Prywatna");

        assertThat(fetch("/api/v1/internal/patients?ids=" + id, serviceTokenFor(b)).json()).isEmpty();
        assertThat(fetch("/api/v1/internal/patients/" + id, serviceTokenFor(b)).status()).isEqualTo(404);
    }
}
