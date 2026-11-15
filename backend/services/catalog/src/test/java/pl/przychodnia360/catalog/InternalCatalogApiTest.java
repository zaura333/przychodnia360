package pl.przychodnia360.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.AbstractCatalogApiTest;
import pl.przychodnia360.common.security.Role;

/** Wewnętrzne API katalogu (tylko tożsamość usługi): batch po identyfikatorach z usługami nieaktywnymi, izolacja placówek, brak danych bez tokenu. */
class InternalCatalogApiTest extends AbstractCatalogApiTest {

    private String createService(String admin, UUID category, String name) {
        Resp r = call("POST", "/api/v1/admin/services", admin, "{\"categoryId\":\"" + category + "\",\"name\":\"" + name
                + "\",\"price\":10,\"durationMinutes\":20,\"performerType\":\"DOCTOR\"}");
        assertThat(r.status()).isEqualTo(201);
        return r.json().get("id").asString();
    }

    @Test
    void batchReturnsActiveAndDeactivatedServicesAndSkipsUnknownIds() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        UUID cat = UUID.fromString(call("POST", "/api/v1/admin/service-categories", admin, "{\"name\":\"K\"}")
                .json().get("id").asString());
        String active = createService(admin, cat, "Aktywna");
        String gone = createService(admin, cat, "Usunięta");
        call("DELETE", "/api/v1/admin/services/" + gone, admin, null);

        Resp r = fetch("/api/v1/internal/services?ids=" + active + "," + gone + "," + UUID.randomUUID(),
                serviceTokenFor(tenant));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json()).hasSize(2);
        assertThat(r.json().findValuesAsString("name")).containsExactlyInAnyOrder("Aktywna", "Usunięta");
        assertThat(r.json().findValues("active").stream().map(n -> n.asBoolean()).toList())
                .containsExactlyInAnyOrder(true, false);
        assertThat(r.json().get(0).get("performerType").asString()).isEqualTo("DOCTOR");
        assertThat(fetch("/api/v1/internal/services/" + gone, serviceTokenFor(tenant)).json().get("active").asBoolean()).isFalse();
        assertThat(fetch("/api/v1/internal/services/" + UUID.randomUUID(), serviceTokenFor(tenant)).status()).isEqualTo(404);
    }

    @Test
    void anotherTenantAndAnonymousSeeNothing() {
        UUID a = newTenant();
        UUID b = newTenant();
        String adminA = tokenFor(a, Role.TENANT_ADMIN);
        UUID cat = UUID.fromString(call("POST", "/api/v1/admin/service-categories", adminA, "{\"name\":\"Prywatna\"}")
                .json().get("id").asString());
        String id = createService(adminA, cat, "Usługa A");

        assertThat(fetch("/api/v1/internal/services?ids=" + id, serviceTokenFor(b)).json()).isEmpty();
        assertThat(fetch("/api/v1/internal/services/" + id, serviceTokenFor(b)).status()).isEqualTo(404);
        assertThat(fetch("/api/v1/internal/services?ids=" + id, null).status()).isEqualTo(401);
    }

    @Test
    void tooManyIdsAreRejected() {
        UUID tenant = newTenant();
        String ids = java.util.stream.IntStream.range(0, 201).mapToObj(i -> UUID.randomUUID().toString())
                .collect(java.util.stream.Collectors.joining(","));
        assertThat(fetch("/api/v1/internal/services?ids=" + ids, serviceTokenFor(tenant)).status())
                .isEqualTo(400);
    }

    @Test
    void userRolesAndTokensForAnotherServiceAreRejected() {
        UUID tenant = newTenant();
        for (Role role : Role.values()) {
            if (role == Role.SERVICE || role == Role.PLATFORM_ADMIN) {
                continue;
            }
            assertThat(fetch("/api/v1/internal/services?ids=" + UUID.randomUUID(), tokenFor(tenant, role)).status())
                    .as("rola %s", role).isEqualTo(403);
        }
        assertThat(fetch("/api/v1/internal/services?ids=" + UUID.randomUUID(), serviceTokenFor(tenant, "patient")).status())
                .as("token usługowy dla innej usługi (aud)").isEqualTo(401);
    }
}
