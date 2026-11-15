package pl.przychodnia360.common.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import pl.przychodnia360.StubHttpServer;
import pl.przychodnia360.StubHttpServer.Reply;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Role;

/** Wybór tożsamości wywołania: token usługowy (domyślnie, gdy jest), token wywołującego (asCaller albo brak tokenu usługowego). */
class RemoteServiceClientTokenTest {

    private static final ParameterizedTypeReference<List<Object>> LIST = new ParameterizedTypeReference<>() { };

    private static RemoteServiceClient client(StubHttpServer stub, Supplier<String> serviceToken) {
        return new RemoteServiceClient("x", stub.url(), Duration.ofMillis(500), Duration.ofSeconds(2),
                new CircuitBreaker(5, Duration.ofSeconds(30), Clock.systemUTC()), RestClient.builder(), serviceToken);
    }

    private static void asCaller(String token) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), Role.TENANT_ADMIN, null, null), token, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serviceTokenWinsAndWorksWithoutAnyRequestContext() {
        try (StubHttpServer stub = new StubHttpServer().respond(r -> Reply.ok("[]"))) {
            RemoteServiceClient client = client(stub, () -> "svc");

            client.get("/a", LIST);
            asCaller("user");
            client.get("/b", LIST);

            assertThat(stub.received()).extracting(StubHttpServer.Received::authorization)
                    .containsExactly("Bearer svc", "Bearer svc");
        }
    }

    @Test
    void asCallerForwardsTheCallersTokenAndFallbackApplies() {
        try (StubHttpServer stub = new StubHttpServer().respond(r -> Reply.ok("[]"))) {
            asCaller("user");
            client(stub, () -> "svc").asCaller().get("/a", LIST);
            client(stub, () -> null).get("/b", LIST);
            client(stub, null).get("/c", LIST);

            assertThat(stub.received()).extracting(StubHttpServer.Received::authorization)
                    .containsExactly("Bearer user", "Bearer user", "Bearer user");
        }
    }

    @Test
    void noTokenAtAllSendsNoAuthorizationHeader() {
        try (StubHttpServer stub = new StubHttpServer().respond(r -> Reply.ok("[]"))) {
            client(stub, () -> null).get("/a", LIST);

            assertThat(stub.received().get(0).authorization()).isNull();
        }
    }
}
