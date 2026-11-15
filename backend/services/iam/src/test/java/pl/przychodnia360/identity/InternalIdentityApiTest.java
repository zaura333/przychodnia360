package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.infrastructure.JwtService;

/**
 * S3 krok 1 (ADR-M17): tokeny usługowe wydawane innym usługom za sekretem i wewnętrzne API kont pracowników
 * (saga zakładania i dezaktywacji pracownika w scheduling).
 */
class InternalIdentityApiTest extends AbstractApiTest {

    private static final String SECRET = "test-scheduling-client-secret";

    @Autowired JwtService jwt;

    private Resp requestToken(String client, String secret, UUID tenant, String audience) {
        return call("POST", "/api/v1/internal/service-tokens", null, "{\"clientId\":\"" + client + "\",\"clientSecret\":\""
                + secret + "\",\"tenantId\":\"" + tenant + "\",\"audience\":\"" + audience + "\"}");
    }

    private String serviceToken(UUID tenant) {
        Resp r = requestToken("scheduling", SECRET, tenant, "iam");
        assertThat(r.status()).as(r.body()).isEqualTo(200);
        return r.json().get("accessToken").asString();
    }

    private Resp ensure(String token, UUID employeeId, String email, String role) {
        return call("PUT", "/api/v1/internal/employee-accounts/" + employeeId, token,
                "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}");
    }

    @Test
    void aKnownServiceGetsAShortLivedTokenNamingItAsTheCaller() {
        UUID tenant = newTenant();
        Resp r = requestToken("scheduling", SECRET, tenant, "catalog");

        assertThat(r.status()).as(r.body()).isEqualTo(200);
        assertThat(r.json().get("expiresIn").asLong()).isEqualTo(60);
        // token dla catalog nie działa w iam (inny aud)
        assertThat(jwt.verify(r.json().get("accessToken").asString())).isEmpty();
        var principal = jwt.verify(serviceToken(tenant)).orElseThrow();
        assertThat(principal.role()).isEqualTo(Role.SERVICE);
        assertThat(principal.tenantId()).isEqualTo(tenant);
        assertThat(principal.userId()).isEqualTo(JwtService.serviceSubject("scheduling"));
    }

    @Test
    void wrongSecretUnknownServiceForbiddenAudienceAndUnknownTenantAreRejected() {
        UUID tenant = newTenant();
        assertThat(requestToken("scheduling", "wrong", tenant, "iam").status()).isEqualTo(401);
        assertThat(requestToken("billing", SECRET, tenant, "iam").status()).isEqualTo(401);
        assertThat(requestToken("scheduling", SECRET, tenant, "billing").status()).as("usługa spoza listy").isEqualTo(403);
        assertThat(requestToken("scheduling", SECRET, UUID.randomUUID(), "iam").status()).as("brak placówki")
                .isEqualTo(403);
    }

    @Test
    void employeeAccountIsCreatedLinkedAndTheRetryReturnsTheSameAccountWithoutASecondMail() {
        UUID tenant = newTenant();
        String token = serviceToken(tenant);
        UUID employeeId = UUID.randomUUID();
        String email = "lekarz." + unique() + "@example.test";

        Resp first = ensure(token, employeeId, email, "DOCTOR");
        Resp retry = ensure(token, employeeId, email, "DOCTOR");

        assertThat(first.status()).as(first.body()).isEqualTo(200);
        assertThat(retry.status()).as(retry.body()).isEqualTo(200);
        String userId = first.json().get("userId").asString();
        assertThat(retry.json().get("userId").asString()).isEqualTo(userId);
        inTenant(tenant, () -> assertThat(jdbc.sql("SELECT employee_id FROM users WHERE id = ?::uuid").param(userId)
                .query(UUID.class).single()).isEqualTo(employeeId));
        assertThat(mails.sentTo(email)).as("jeden e-mail z linkiem ustawienia hasła").hasSize(1);
    }

    @Test
    void anEmailOfAnotherAccountIsAConflictAndNonEmployeeRolesAreRejected() {
        UUID tenant = newTenant();
        String token = serviceToken(tenant);
        String email = "zajety." + unique() + "@example.test";
        assertThat(ensure(token, UUID.randomUUID(), email, "DOCTOR").status()).isEqualTo(200);

        assertThat(ensure(token, UUID.randomUUID(), email, "DOCTOR").status()).as("inny pracownik").isEqualTo(409);
        assertThat(ensure(token, UUID.randomUUID(), "admin." + unique() + "@example.test", "TENANT_ADMIN").status())
                .isEqualTo(400);
    }

    @Test
    void deactivationIsIdempotentAndCutsAccess() {
        UUID tenant = newTenant();
        var doctor = newUser(tenant, Role.DOCTOR);
        String userToken = login(tenant, doctor);
        String token = serviceToken(tenant);

        assertThat(call("POST", "/api/v1/internal/users/" + doctor.id() + "/deactivate", token, null).status())
                .isEqualTo(204);
        assertThat(call("POST", "/api/v1/internal/users/" + doctor.id() + "/deactivate", token, null).status())
                .isEqualTo(204);
        assertThat(call("GET", "/api/v1/me", userToken, null).status()).isEqualTo(401);
    }

    @Test
    void userTokensAndServiceTokensForOtherServicesCannotUseTheInternalApi() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        String forPatient = jwt.issueService("scheduling", tenant, "patient");

        assertThat(ensure(admin, UUID.randomUUID(), "x." + unique() + "@example.test", "DOCTOR").status()).isEqualTo(403);
        assertThat(ensure(forPatient, UUID.randomUUID(), "y." + unique() + "@example.test", "DOCTOR").status())
                .isEqualTo(401);
        assertThat(ensure(null, UUID.randomUUID(), "z." + unique() + "@example.test", "DOCTOR").status()).isEqualTo(401);
    }
}
