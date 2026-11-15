package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.application.PlatformAccountService;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.ValidationException;

class PlatformApiTest extends AbstractApiTest {

    @Autowired PlatformAccountService platformAccounts;

    private String platformToken() {
        String email = "root." + unique() + "@platform.test";
        try (var h = TenantContext.openPlatformScope()) {
            tx.executeWithoutResult(s -> users.insert(
                    User.create(Ids.newId(), null, email, Role.PLATFORM_ADMIN, hasher.hash(PASSWORD))));
        }
        Resp login = call("POST", "/api/v1/auth/platform/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
        return login.json().get("accessToken").asString();
    }

    @Test
    void platformAdminCreatesTenantAndItsAdminWhoSetsPasswordFromLink() {
        String platform = platformToken();
        String slug = "nowa-" + unique();

        Resp created = call("POST", "/api/v1/platform/tenants", platform,
                "{\"slug\":\"" + slug + "\",\"name\":\"Nowa Klinika\"}");
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String tenantId = created.json().get("id").asString();
        assertThat(call("POST", "/api/v1/platform/tenants", platform,
                "{\"slug\":\"" + slug + "\",\"name\":\"Duplikat\"}").status()).isEqualTo(409);
        assertThat(call("POST", "/api/v1/platform/tenants", platform,
                "{\"slug\":\"Zly Slug\",\"name\":\"X\"}").status()).isEqualTo(400);

        String adminEmail = "admin." + unique() + "@example.test";
        Resp admin = call("POST", "/api/v1/platform/tenants/" + tenantId + "/admins", platform,
                "{\"email\":\"" + adminEmail + "\"}");
        assertThat(admin.status()).as(admin.body()).isEqualTo(201);
        assertThat(call("POST", "/api/v1/platform/tenants/" + tenantId + "/admins", platform,
                "{\"email\":\"" + adminEmail + "\"}").status()).isEqualTo(409);

        String token = mails.lastTokenFor(adminEmail);
        assertThat(call("POST", "/api/v1/auth/password-reset/confirm", null,
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + PASSWORD + "\"}").status()).isEqualTo(204);
        Resp login = call("POST", "/api/v1/auth/login", null, "{\"tenantSlug\":\"" + slug + "\",\"email\":\""
                + adminEmail + "\",\"password\":\"" + PASSWORD + "\"}");
        assertThat(login.status()).isEqualTo(200);
        String adminToken = login.json().get("accessToken").asString();
        assertThat(fetch("/api/v1/tenant", adminToken).json().get("slug").asString()).isEqualTo(slug);
        assertThat(fetch("/api/v1/admin/employees", adminToken).status()).as("administrator nowej placówki ma dostęp").isEqualTo(200);
    }

    @Test
    void platformAdminListsAndDeactivatesTenantsAndInactiveTenantCannotLogIn() {
        String platform = platformToken();
        UUID tenant = newTenant();
        var user = newUser(tenant, Role.RECEPTIONIST);

        assertThat(fetch("/api/v1/platform/tenants", platform).json().toString()).contains(tenant.toString());
        Resp off = call("PATCH", "/api/v1/platform/tenants/" + tenant, platform, "{\"active\":false}");
        assertThat(off.json().get("active").asBoolean()).isFalse();
        assertThat(call("POST", "/api/v1/auth/login", null, "{\"tenantSlug\":\"" + slugOf(tenant) + "\",\"email\":\""
                + user.email() + "\",\"password\":\"" + PASSWORD + "\"}").status()).isEqualTo(401);
        assertThat(call("PATCH", "/api/v1/platform/tenants/" + UUID.randomUUID(), platform,
                "{\"name\":\"X\"}").status()).isEqualTo(404);
    }

    @Test
    void platformAdminHasNoAccessToTenantData() {
        String platform = platformToken();
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        inTenantResult(tenant, this::publishSampleEvent);

        for (String path : new String[] {"/api/v1/doctors", "/api/v1/admin/employees", "/api/v1/tenant"}) {
            assertThat(fetch(path, platform).status()).as(path).isEqualTo(403);
        }
        // nawet bezpośrednie zapytanie w zakresie platformy nie widzi danych placówek (RLS)
        try (var h = TenantContext.openPlatformScope()) {
            Long count = tx.execute(s -> jdbc.sql("SELECT count(*) FROM outbox WHERE tenant_id = ?").param(tenant)
                    .query(Long.class).single());
            assertThat(count).isZero();
        }
    }

    @Test
    void tenantRolesCannotUsePlatformEndpoints() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        assertThat(fetch("/api/v1/platform/tenants", admin).status()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/platform/tenants", admin,
                "{\"slug\":\"x-" + unique() + "\",\"name\":\"X\"}").status()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/platform/tenants/" + tenant + "/admins", admin,
                "{\"email\":\"a@example.test\"}").status()).isEqualTo(403);
        assertThat(fetch("/api/v1/platform/tenants", null).status()).isEqualTo(401);
    }

    @Test
    void bootstrapCreatesFirstPlatformAdminOnlyOnce() {
        String email = "boot." + unique() + "@platform.test";
        assertThat(platformAccounts.ensurePlatformAdmin(email, PASSWORD)).isTrue();
        assertThat(platformAccounts.ensurePlatformAdmin(email, PASSWORD)).isFalse();
        assertThat(call("POST", "/api/v1/auth/platform/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}").status()).isEqualTo(200);
        assertThatThrownBy(() -> platformAccounts.ensurePlatformAdmin(email, "short"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void tenantReadEndpointReturnsOwnTenantForEveryTenantRole() {
        UUID tenant = newTenant();
        for (Role role : new Role[] {Role.PATIENT, Role.RECEPTIONIST, Role.DOCTOR, Role.TENANT_ADMIN}) {
            Resp r = fetch("/api/v1/tenant", tokenFor(tenant, role));
            assertThat(r.status()).as(role.name()).isEqualTo(200);
            assertThat(r.json().get("id").asString()).isEqualTo(tenant.toString());
        }
    }
}
