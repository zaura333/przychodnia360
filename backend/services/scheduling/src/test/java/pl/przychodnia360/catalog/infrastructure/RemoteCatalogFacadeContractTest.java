package pl.przychodnia360.catalog.infrastructure;

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
import pl.przychodnia360.catalog.api.CatalogFacade.ServiceInfo;
import pl.przychodnia360.common.http.CircuitBreaker;
import pl.przychodnia360.common.http.RemoteServiceClient;
import pl.przychodnia360.testsupport.contract.InternalApiContract;

/**
 * Adapter scheduling do catalog-service kontra kontrakt wewnętrzny dostawcy ({@code api/internal/catalog.json}): każde
 * wywołanie adaptera musi trafiać w operację, którą catalog-service deklaruje, a rekord konsumenta musi dać się
 * wypełnić z deklarowanej odpowiedzi.
 */
class RemoteCatalogFacadeContractTest {

    private static final InternalApiContract CATALOG = InternalApiContract.of("catalog");

    @Test
    void adapterCallsMatchTheCatalogInternalContract() {
        List<String> problems = new CopyOnWriteArrayList<>();
        try (StubHttpServer stub = new StubHttpServer().respond(r -> {
            var verdict = CATALOG.verify(r.method(), r.path(), r.query(), r.body());
            problems.addAll(verdict.problems());
            return new Reply(verdict.status(), verdict.body(), 0);
        })) {
            RemoteCatalogFacade catalog = new RemoteCatalogFacade(new RemoteServiceClient("catalog", stub.url(),
                    Duration.ofMillis(500), Duration.ofSeconds(2),
                    new CircuitBreaker(5, Duration.ofSeconds(30), Clock.systemUTC()), RestClient.builder(),
                    () -> "svc-token"));
            UUID id = UUID.randomUUID();

            assertThat(catalog.service(id)).as("przykład z kontraktu da się wczytać").isPresent();
            assertThat(catalog.services(List.of(id))).hasSize(1);

            assertThat(stub.received()).as("adapter wykonał oba wywołania").hasSize(2);
        }
        problems.addAll(CATALOG.responseProblems("GET", "/api/v1/internal/services/{id}", ServiceInfo.class));
        problems.addAll(CATALOG.responseProblems("GET", "/api/v1/internal/services", ServiceInfo.class));

        assertThat(problems).isEmpty();
    }
}
