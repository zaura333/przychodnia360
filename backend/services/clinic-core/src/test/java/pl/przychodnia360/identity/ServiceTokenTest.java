package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.common.security.AccessRevocations;
import pl.przychodnia360.common.security.JwksKeyResolver;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.common.security.RsaAccessTokenVerifier;
import pl.przychodnia360.identity.infrastructure.JwtService;

/**
 * Token usługowy iam (S2): rola SERVICE, jedna placówka i jedna usługa docelowa. Usługa go przyjmuje tylko dla swojej
 * nazwy (aud) i z placówką; iam sam go nie przyjmuje; token użytkownika nie przechodzi za usługowy.
 */
class ServiceTokenTest extends AbstractApiTest {

    @Autowired JwtService jwt;

    private RsaAccessTokenVerifier verifierOf(String serviceName) {
        Clock clock = Clock.systemUTC();
        String url = "http://localhost:" + port + "/api/v1/auth/jwks";
        return new RsaAccessTokenVerifier(new JwksKeyResolver(url, 3000), "przychodnia360", clock,
                new AccessRevocations(clock, Duration.ofMinutes(10)), serviceName);
    }

    @Test
    void theTargetServiceAcceptsItsTokenWithTheTenantAndRoleService() {
        UUID tenant = UUID.randomUUID();
        String token = jwt.issueService(tenant, "patient");

        var principal = verifierOf("patient").verify(token).orElseThrow();

        assertThat(principal.role()).isEqualTo(Role.SERVICE);
        assertThat(principal.tenantId()).isEqualTo(tenant);
        assertThat(principal.patientId()).isNull();
        assertThat(principal.employeeId()).isNull();
    }

    @Test
    void otherServicesAndVerifiersWithoutAnAudienceRejectIt() {
        String token = jwt.issueService(UUID.randomUUID(), "patient");

        assertThat(verifierOf("catalog").verify(token)).as("token dla innej usługi").isEmpty();
        assertThat(verifierOf(null).verify(token)).as("weryfikator bez nazwy usługi").isEmpty();
        assertThat(jwt.verify(token)).as("iam nie przyjmuje tokenów usługowych").isEmpty();
    }

    @Test
    void userTokensStillWorkForEveryServiceAndAreNotServiceTokens() {
        UUID tenant = newTenant();
        String userToken = tokenFor(tenant, Role.RECEPTIONIST);

        assertThat(verifierOf("patient").verify(userToken).orElseThrow().role()).isEqualTo(Role.RECEPTIONIST);
        assertThat(verifierOf("catalog").verify(userToken).orElseThrow().role()).isEqualTo(Role.RECEPTIONIST);
    }
}
