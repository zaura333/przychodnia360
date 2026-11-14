package pl.przychodnia360.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.identity.api.Role;

class CatalogApiTest extends AbstractApiTest {

    private String body(UUID categoryId, String name, String price, int minutes, String performer) {
        return "{\"categoryId\":\"" + categoryId + "\",\"name\":\"" + name + "\",\"description\":\"opis\",\"price\":"
                + price + ",\"durationMinutes\":" + minutes + ",\"performerType\":\"" + performer + "\"}";
    }

    private UUID category(String admin, String name) {
        Resp r = call("POST", "/api/v1/admin/service-categories", admin, "{\"name\":\"" + name + "\"}");
        assertThat(r.status()).isEqualTo(201);
        return UUID.fromString(r.json().get("id").asString());
    }

    @Test
    void adminManagesCatalogAndPatientFiltersIt() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        String patient = tokenFor(tenant, Role.PATIENT);
        UUID consult = category(admin, "Konsultacje");
        UUID diag = category(admin, "Diagnostyka");

        assertThat(call("POST", "/api/v1/admin/services", admin, body(consult, "Konsultacja", "150.00", 30, "DOCTOR")).status()).isEqualTo(201);
        assertThat(call("POST", "/api/v1/admin/services", admin, body(diag, "Pobranie krwi", "40.00", 15, "MEDICAL_STAFF")).status()).isEqualTo(201);
        assertThat(call("POST", "/api/v1/admin/services", admin, body(diag, "USG", "200.00", 45, "DOCTOR")).status()).isEqualTo(201);

        assertThat(fetch("/api/v1/services", patient).json().get("total").asLong()).isEqualTo(3);
        assertThat(fetch("/api/v1/services?categoryId=" + diag, patient).json().get("total").asLong()).isEqualTo(2);
        assertThat(fetch("/api/v1/services?performerType=MEDICAL_STAFF", patient).json().get("total").asLong()).isEqualTo(1);
        assertThat(fetch("/api/v1/services?minPrice=100&maxPrice=160", patient).json().get("items").get(0).get("name").asString())
                .isEqualTo("Konsultacja");
        assertThat(fetch("/api/v1/services?minPrice=300&maxPrice=100", patient).status()).isEqualTo(400);
        assertThat(fetch("/api/v1/service-categories", patient).json()).hasSize(2);
    }

    @Test
    void deactivatedServiceDisappearsFromListAndDetails() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        UUID cat = category(admin, "Kategoria");
        Resp created = call("POST", "/api/v1/admin/services", admin, body(cat, "Usługa", "10", 20, "DOCTOR"));
        String id = created.json().get("id").asString();

        assertThat(call("DELETE", "/api/v1/admin/services/" + id, admin, null).status()).isEqualTo(204);
        assertThat(fetch("/api/v1/services/" + id, admin).status()).isEqualTo(404);
        assertThat(fetch("/api/v1/services", admin).json().get("total").asLong()).isZero();
    }

    @Test
    void categoryWithActiveServicesCannotBeDeleted() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        UUID cat = category(admin, "Zajeta");
        call("POST", "/api/v1/admin/services", admin, body(cat, "Usługa", "10", 20, "DOCTOR"));

        Resp r = call("DELETE", "/api/v1/admin/service-categories/" + cat, admin, null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(call("POST", "/api/v1/admin/service-categories", admin, "{\"name\":\"zajeta\"}").status())
                .as("nazwa kategorii unikalna bez względu na wielkość liter").isEqualTo(409);
    }

    @Test
    void validationAndRoleChecks() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        String receptionist = tokenFor(tenant, Role.RECEPTIONIST);
        UUID cat = category(admin, "K");

        assertThat(call("POST", "/api/v1/admin/services", admin, body(cat, "X", "-1", 20, "DOCTOR")).status()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/admin/services", admin, body(cat, "X", "10", 3, "DOCTOR")).status()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/admin/services", admin, body(UUID.randomUUID(), "X", "10", 20, "DOCTOR")).status()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/admin/services", receptionist, body(cat, "X", "10", 20, "DOCTOR")).status()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/admin/services", null, body(cat, "X", "10", 20, "DOCTOR")).status()).isEqualTo(401);
    }

    @Test
    void anotherTenantCannotSeeOrChangeServices() {
        UUID a = newTenant();
        UUID b = newTenant();
        String adminA = tokenFor(a, Role.TENANT_ADMIN);
        String adminB = tokenFor(b, Role.TENANT_ADMIN);
        UUID cat = category(adminA, "Prywatna");
        String id = call("POST", "/api/v1/admin/services", adminA, body(cat, "Usługa A", "10", 20, "DOCTOR")).json().get("id").asString();

        assertThat(fetch("/api/v1/services/" + id, adminB).status()).isEqualTo(404);
        assertThat(fetch("/api/v1/services", adminB).json().get("total").asLong()).isZero();
        assertThat(call("PUT", "/api/v1/admin/services/" + id, adminB, body(cat, "Zmiana", "1", 20, "DOCTOR")).status()).isEqualTo(404);
        assertThat(call("DELETE", "/api/v1/admin/services/" + id, adminB, null).status()).isEqualTo(404);
        assertThat(call("DELETE", "/api/v1/admin/service-categories/" + cat, adminB, null).status()).isEqualTo(404);
    }
}
