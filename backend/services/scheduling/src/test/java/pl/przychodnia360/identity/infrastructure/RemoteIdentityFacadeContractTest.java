package pl.przychodnia360.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.przychodnia360.StubHttpServer;
import pl.przychodnia360.StubHttpServer.Reply;
import pl.przychodnia360.common.http.CircuitBreaker;
import pl.przychodnia360.common.http.RemoteServiceClient;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.testsupport.contract.InternalApiContract;

/**
 * Adapter scheduling do iam kontra kontrakt wewnętrzny dostawcy ({@code api/internal/iam.json}): zakładanie i
 * dezaktywacja konta pracownika muszą trafiać w operacje, które iam deklaruje, z polami i wartościami enum zgodnymi z
 * kontraktem, a odpowiedź musi dać się wczytać.
 */
class RemoteIdentityFacadeContractTest {

    private static final InternalApiContract IAM = InternalApiContract.of("iam");

    @Test
    void adapterCallsMatchTheIamInternalContract() {
        List<String> problems = new CopyOnWriteArrayList<>();
        try (StubHttpServer stub = new StubHttpServer().respond(r -> {
            var verdict = IAM.verify(r.method(), r.path(), r.query(), r.body());
            problems.addAll(verdict.problems());
            return new Reply(verdict.status(), verdict.body(), 0);
        })) {
            RemoteIdentityFacade identity = new RemoteIdentityFacade(new RemoteServiceClient("iam", stub.url(),
                    Duration.ofMillis(500), Duration.ofSeconds(2),
                    new CircuitBreaker(5, Duration.ofSeconds(30), Clock.systemUTC()), RestClient.builder(),
                    () -> "svc-token"));

            for (Role role : List.of(Role.DOCTOR, Role.MEDICAL_STAFF)) {
                assertThat(identity.ensureEmployeeAccount(UUID.randomUUID(), "pracownik@example.test", role))
                        .as("odpowiedź z kontraktu da się wczytać").isNotNull();
            }
            identity.deactivate(UUID.randomUUID());

            assertThat(stub.received()).as("adapter wykonał wszystkie wywołania").hasSize(3);
        }
        problems.addAll(IAM.responseProblems("PUT", "/api/v1/internal/employee-accounts/{employeeId}",
                RemoteIdentityFacade.AccountResponse.class));

        assertThat(problems).isEmpty();
    }
}
