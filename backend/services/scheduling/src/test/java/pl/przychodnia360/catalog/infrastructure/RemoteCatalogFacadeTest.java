package pl.przychodnia360.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import pl.przychodnia360.StubHttpServer;
import pl.przychodnia360.StubHttpServer.Reply;
import pl.przychodnia360.common.http.CircuitBreaker;
import pl.przychodnia360.common.http.RemoteServiceClient;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.shared.ServiceUnavailableException;

/** Zdalna fasada katalogu (S1): mapowanie odpowiedzi, przekazanie tokenu, limit czasu, wyłącznik obwodu, degradacja widoków. */
class RemoteCatalogFacadeTest {

    private static final String SERVICE = "{\"id\":\"%s\",\"name\":\"USG\",\"price\":200.00,\"durationMinutes\":45,"
            + "\"performerType\":\"DOCTOR\",\"active\":true}";

    private static RemoteServiceClient client(StubHttpServer stub, int threshold, Duration read) {
        return new RemoteServiceClient("catalog", stub.url(), Duration.ofMillis(500), read,
                new CircuitBreaker(threshold, Duration.ofSeconds(30), Clock.systemUTC()), RestClient.builder());
    }

    private static void asCaller(String token) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), Role.RECEPTIONIST, null, null), token, List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsServicesAndForwardsTheCallersToken() {
        UUID id = UUID.randomUUID();
        try (StubHttpServer stub = new StubHttpServer().respond(r -> r.path().endsWith("/" + id)
                ? Reply.ok(String.format(SERVICE, id)) : Reply.ok("[" + String.format(SERVICE, id) + "]"))) {
            RemoteCatalogFacade facade = new RemoteCatalogFacade(client(stub, 5, Duration.ofSeconds(2)));
            asCaller("token-abc");

            var one = facade.service(id).orElseThrow();
            assertThat(one.name()).isEqualTo("USG");
            assertThat(one.performerType()).isEqualTo("DOCTOR");
            assertThat(one.price()).isEqualByComparingTo("200.00");
            assertThat(facade.services(List.of(id, UUID.randomUUID()))).containsOnlyKeys(id);
            assertThat(stub.received()).allSatisfy(r -> assertThat(r.authorization()).isEqualTo("Bearer token-abc"));
            assertThat(stub.received().get(1).query()).contains("ids=" + id);
        }
    }

    @Test
    void unknownServiceIsEmptyAndNotAFailure() {
        try (StubHttpServer stub = new StubHttpServer().respond(r -> Reply.status(404))) {
            RemoteCatalogFacade facade = new RemoteCatalogFacade(client(stub, 1, Duration.ofSeconds(2)));
            asCaller("t");

            assertThat(facade.service(UUID.randomUUID())).isEmpty();
            assertThat(facade.service(UUID.randomUUID())).as("404 nie otwiera obwodu").isEmpty();
            assertThat(stub.received()).hasSize(2);
        }
    }

    @Test
    void outageFailsBookingLookupWithTheBreakerAndDegradesViews() {
        try (StubHttpServer stub = new StubHttpServer().respond(r -> Reply.status(503))) {
            RemoteCatalogFacade facade = new RemoteCatalogFacade(client(stub, 2, Duration.ofSeconds(2)));
            asCaller("t");

            assertThatThrownBy(() -> facade.service(UUID.randomUUID())).isInstanceOf(ServiceUnavailableException.class);
            assertThat(facade.services(List.of(UUID.randomUUID()))).as("widok bez nazw, bez wyjątku").isEmpty();
            int hits = stub.received().size();
            assertThatThrownBy(() -> facade.service(UUID.randomUUID())).isInstanceOf(ServiceUnavailableException.class);
            assertThat(stub.received()).as("obwód otwarty: kolejne wywołania nie dochodzą do usługi").hasSize(hits);
        }
    }

    @Test
    void slowServiceHitsTheReadTimeout() {
        try (StubHttpServer stub = new StubHttpServer().respond(r -> Reply.slow("[]", 1500))) {
            RemoteCatalogFacade facade = new RemoteCatalogFacade(client(stub, 5, Duration.ofMillis(200)));
            asCaller("t");

            long start = System.nanoTime();
            assertThatThrownBy(() -> facade.service(UUID.randomUUID())).isInstanceOf(ServiceUnavailableException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        }
    }
}
