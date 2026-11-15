package pl.przychodnia360.patients.infrastructure;

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

/** Zdalna fasada pacjentów (S2): odczyt przez REST, degradacja widoków, awaria przy pojedynczym odczycie, brak zapisu bez sagi. */
class RemotePatientFacadeTest {

    private static final String PATIENT = "{\"id\":\"%s\",\"userId\":null,\"firstName\":\"Anna\",\"lastName\":\"Nowak\","
            + "\"birthDate\":\"1990-05-17\",\"contactEmail\":null}";

    private static RemoteServiceClient client(StubHttpServer stub, int threshold, Duration read) {
        return new RemoteServiceClient("patient", stub.url(), Duration.ofMillis(500), read,
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
    void readsPatientsAndForwardsTheCallersToken() {
        UUID id = UUID.randomUUID();
        try (StubHttpServer stub = new StubHttpServer().respond(r -> r.path().endsWith("/" + id)
                ? Reply.ok(String.format(PATIENT, id)) : Reply.ok("[" + String.format(PATIENT, id) + "]"))) {
            RemotePatientFacade facade = new RemotePatientFacade(client(stub, 5, Duration.ofSeconds(2)));
            asCaller("token-xyz");

            var one = facade.find(id).orElseThrow();
            assertThat(one.fullName()).isEqualTo("Anna Nowak");
            assertThat(one.birthDate()).isEqualTo(java.time.LocalDate.of(1990, 5, 17));
            assertThat(facade.findAll(List.of(id))).containsOnlyKeys(id);
            assertThat(stub.received()).allSatisfy(r -> assertThat(r.authorization()).isEqualTo("Bearer token-xyz"));
        }
    }

    @Test
    void unknownPatientIsEmpty() {
        try (StubHttpServer stub = new StubHttpServer().respond(r -> Reply.status(404))) {
            assertThat(new RemotePatientFacade(client(stub, 5, Duration.ofSeconds(2))).find(UUID.randomUUID())).isEmpty();
        }
    }

    @Test
    void outageDegradesViewsButFailsSingleReads() {
        try (StubHttpServer stub = new StubHttpServer().respond(r -> Reply.status(500))) {
            RemotePatientFacade facade = new RemotePatientFacade(client(stub, 5, Duration.ofSeconds(2)));
            asCaller("t");

            assertThat(facade.findAll(List.of(UUID.randomUUID()))).as("etykieta zastępcza po stronie widoku").isEmpty();
            assertThatThrownBy(() -> facade.find(UUID.randomUUID())).isInstanceOf(ServiceUnavailableException.class);
        }
    }

    @Test
    void writesAreExplicitlyUnavailableUntilTheRegistrationSagaExists() {
        try (StubHttpServer stub = new StubHttpServer()) {
            RemotePatientFacade facade = new RemotePatientFacade(client(stub, 5, Duration.ofSeconds(2)));

            assertThatThrownBy(() -> facade.create("A", "B", java.time.LocalDate.of(1990, 1, 1), null))
                    .isInstanceOf(ServiceUnavailableException.class);
            assertThatThrownBy(() -> facade.linkAccount(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(ServiceUnavailableException.class);
            assertThat(stub.received()).isEmpty();
        }
    }
}
