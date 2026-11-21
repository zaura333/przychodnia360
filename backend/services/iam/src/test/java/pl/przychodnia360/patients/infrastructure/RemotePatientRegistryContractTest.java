package pl.przychodnia360.patients.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.przychodnia360.StubHttpServer;
import pl.przychodnia360.StubHttpServer.Reply;
import pl.przychodnia360.common.http.CircuitBreaker;
import pl.przychodnia360.common.http.RemoteServiceClient;
import pl.przychodnia360.patients.api.PatientRegistry.PatientInfo;
import pl.przychodnia360.testsupport.contract.InternalApiContract;

/**
 * Adapter iam do patient-service kontra kontrakt wewnętrzny dostawcy ({@code api/internal/patient.json}): każde
 * wywołanie adaptera musi trafiać w operację, którą patient-service deklaruje, z polami i typami zgodnymi z
 * kontraktem, a rekord konsumenta musi dać się wypełnić z deklarowanej odpowiedzi.
 */
class RemotePatientRegistryContractTest {

    private static final InternalApiContract PATIENT = InternalApiContract.of("patient");

    @Test
    void adapterCallsMatchThePatientInternalContract() {
        List<String> problems = new CopyOnWriteArrayList<>();
        try (StubHttpServer stub = new StubHttpServer().respond(r -> {
            var verdict = PATIENT.verify(r.method(), r.path(), r.query(), r.body());
            problems.addAll(verdict.problems());
            return new Reply(verdict.status(), verdict.body(), 0);
        })) {
            RemotePatientRegistry registry = new RemotePatientRegistry(new RemoteServiceClient("patient", stub.url(),
                    Duration.ofMillis(500), Duration.ofSeconds(2),
                    new CircuitBreaker(5, Duration.ofSeconds(30), Clock.systemUTC()), RestClient.builder(),
                    () -> "svc-token"));
            UUID id = UUID.randomUUID();

            registry.ensure(id, "Anna", "Nowak", LocalDate.of(1990, 5, 17), "a@example.test", UUID.randomUUID());
            registry.ensure(id, "Anna", "Nowak", LocalDate.of(1990, 5, 17), null, null);
            assertThat(registry.find(id)).isPresent();

            assertThat(stub.received()).as("adapter wykonał wszystkie trzy wywołania").hasSize(3);
        }
        problems.addAll(PATIENT.responseProblems("GET", "/api/v1/internal/patients/{id}", PatientInfo.class));

        assertThat(problems).isEmpty();
    }
}
