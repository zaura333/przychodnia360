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
    void outageFailsSingleReads() {
        try (StubHttpServer stub = new StubHttpServer().respond(r -> Reply.status(500))) {
            RemotePatientFacade facade = new RemotePatientFacade(client(stub, 5, Duration.ofSeconds(2)));
            asCaller("t");

            assertThatThrownBy(() -> facade.find(UUID.randomUUID())).isInstanceOf(ServiceUnavailableException.class);
        }
    }

    @Test
    void writesGoToTheInternalEndpointsWithTheServiceTokenAndFailLoudly() {
        UUID patientId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        try (StubHttpServer stub = new StubHttpServer().respond(r -> Reply.status(204))) {
            RemotePatientFacade facade = new RemotePatientFacade(serviceClient(stub, "svc-token"));
            asCaller("user-token");

            facade.ensure(patientId, "Anna", "Nowak", java.time.LocalDate.of(1990, 5, 17), "a@example.test", userId);
            UUID withoutAccount = UUID.randomUUID();
            facade.ensure(withoutAccount, "Ewa", "Lis", java.time.LocalDate.of(1985, 1, 1), null, null);

            assertThat(stub.received()).hasSize(2).allSatisfy(r -> {
                assertThat(r.method()).isEqualTo("PUT");
                assertThat(r.authorization()).as("tożsamość usługi, nie użytkownika").isEqualTo("Bearer svc-token");
            });
            assertThat(stub.received().get(0).path()).isEqualTo("/api/v1/internal/patients/" + patientId);
            assertThat(stub.received().get(0).body()).contains("\"firstName\":\"Anna\"", "\"userId\":\"" + userId + "\"");
            assertThat(stub.received().get(1).path()).isEqualTo("/api/v1/internal/patients/" + withoutAccount);
            assertThat(stub.received().get(1).body()).contains("\"userId\":null");
        }
        try (StubHttpServer down = new StubHttpServer().respond(r -> Reply.status(503))) {
            RemotePatientFacade facade = new RemotePatientFacade(serviceClient(down, "svc-token"));
            assertThatThrownBy(() -> facade.ensure(patientId, "A", "B", java.time.LocalDate.of(1990, 1, 1), null, null))
                    .isInstanceOf(ServiceUnavailableException.class);
        }
    }

    private static RemoteServiceClient serviceClient(StubHttpServer stub, String token) {
        return new RemoteServiceClient("patient", stub.url(), Duration.ofMillis(500), Duration.ofSeconds(2),
                new CircuitBreaker(5, Duration.ofSeconds(30), Clock.systemUTC()), RestClient.builder(), () -> token);
    }
}
