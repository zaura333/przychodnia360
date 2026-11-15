package pl.przychodnia360.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pl.przychodnia360.common.internal.InternalApi;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.PerformerType;

/**
 * Klienci wewnętrznego API na prawdziwym HTTP (lokalny serwer JDK zamiast catalog-service i iam-service):
 * ścieżki, nagłówek usługowy, przekazanie tokenu użytkownika, odczyt odpowiedzi i odtworzenie błędów domenowych.
 */
class HttpRemoteClientsTest {

    private static final String TOKEN = "internal-test";

    private HttpServer server;
    private String baseUrl;
    private final Map<String, String> seenHeaders = new ConcurrentHashMap<>();
    private final Map<String, String> seenBodies = new ConcurrentHashMap<>();
    private final HttpRemoteClients config = new HttpRemoteClients();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void stop() {
        RequestContextHolder.resetRequestAttributes();
        server.stop(0);
    }

    private void respond(String path, int status, String json) {
        server.createContext(InternalApi.PREFIX + path, exchange -> {
            seenHeaders.put(path + ":auth", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            seenHeaders.put(path + ":internal",
                    String.valueOf(exchange.getRequestHeaders().getFirst(InternalApi.TOKEN_HEADER)));
            seenBodies.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type",
                    status >= 400 ? "application/problem+json" : "application/json");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
    }

    @Test
    void catalogLookupSendsIdsWithHeadersAndMapsServices() {
        UUID id = UUID.randomUUID();
        respond("/services/lookup", 200, "[{\"id\":\"" + id + "\",\"name\":\"USG\",\"price\":200.00,"
                + "\"durationMinutes\":45,\"performerType\":\"DOCTOR\",\"active\":true}]");
        CatalogFacade catalog = config.catalogFacade(baseUrl, TOKEN);

        CatalogFacade.ServiceInfo service = catalog.service(id).orElseThrow();
        assertThat(service.durationMinutes()).isEqualTo(45);
        assertThat(service.performerType()).isEqualTo(PerformerType.DOCTOR);
        assertThat(seenBodies.get("/services/lookup")).contains(id.toString());
        assertThat(seenHeaders.get("/services/lookup:auth")).isEqualTo("Bearer user-token");
        assertThat(seenHeaders.get("/services/lookup:internal")).isEqualTo(TOKEN);
        assertThat(catalog.services(List.of())).as("pusta lista bez wywołania").isEmpty();
    }

    @Test
    void unknownPatientIsEmptyNotError() {
        respond("/patients/lookup", 200, "[]");
        assertThat(config.patientFacade(baseUrl, TOKEN).find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void conflictFromIamKeepsProblemTypeAndDetail() {
        respond("/employee-accounts", 409, "{\"type\":\"/problems/email-taken\",\"title\":\"Konflikt\",\"status\":409,"
                + "\"detail\":\"Konto z tym adresem e-mail już istnieje.\"}");
        IdentityFacade identity = config.identityFacade(baseUrl, TOKEN);

        assertThatThrownBy(() -> identity.createEmployeeAccount("a@example.test", Role.DOCTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("już istnieje");
        assertThat(seenBodies.get("/employee-accounts")).contains("\"role\":\"DOCTOR\"");
    }

    @Test
    void createdAccountIdIsReturned() {
        UUID userId = UUID.randomUUID();
        respond("/employee-accounts", 200, "{\"userId\":\"" + userId + "\"}");
        assertThat(config.identityFacade(baseUrl, TOKEN).createEmployeeAccount("b@example.test", Role.RECEPTIONIST))
                .isEqualTo(userId);
    }
}
