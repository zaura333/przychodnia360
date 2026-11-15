package pl.przychodnia360.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.AbstractCatalogApiTest;
import pl.przychodnia360.common.security.Role;

/** Wewnętrzne API katalogu: batch po identyfikatorach z usługami nieaktywnymi, izolacja placówek, brak danych bez tokenu. */
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
                tokenFor(tenant, Role.RECEPTIONIST));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json()).hasSize(2);
        assertThat(r.json().findValuesAsString("name")).containsExactlyInAnyOrder("Aktywna", "Usunięta");
        assertThat(r.json().findValues("active").stream().map(n -> n.asBoolean()).toList())
                .containsExactlyInAnyOrder(true, false);
        assertThat(r.json().get(0).get("performerType").asString()).isEqualTo("DOCTOR");
        assertThat(fetch("/api/v1/internal/services/" + gone, admin).json().get("active").asBoolean()).isFalse();
        assertThat(fetch("/api/v1/internal/services/" + UUID.randomUUID(), admin).status()).isEqualTo(404);
    }

    @Test
    void anotherTenantAndAnonymousSeeNothing() {
        UUID a = newTenant();
        UUID b = newTenant();
        String adminA = tokenFor(a, Role.TENANT_ADMIN);
        UUID cat = UUID.fromString(call("POST", "/api/v1/admin/service-categories", adminA, "{\"name\":\"Prywatna\"}")
                .json().get("id").asString());
        String id = createService(adminA, cat, "Usługa A");

        assertThat(fetch("/api/v1/internal/services?ids=" + id, tokenFor(b, Role.TENANT_ADMIN)).json()).isEmpty();
        assertThat(fetch("/api/v1/internal/services/" + id, tokenFor(b, Role.TENANT_ADMIN)).status()).isEqualTo(404);
        assertThat(fetch("/api/v1/internal/services?ids=" + id, null).status()).isEqualTo(401);
    }

    @Test
    void tooManyIdsAreRejected() {
        UUID tenant = newTenant();
        String ids = java.util.stream.IntStream.range(0, 201).mapToObj(i -> UUID.randomUUID().toString())
                .collect(java.util.stream.Collectors.joining(","));
        assertThat(fetch("/api/v1/internal/services?ids=" + ids, tokenFor(tenant, Role.TENANT_ADMIN)).status())
                .isEqualTo(400);
    }
}
