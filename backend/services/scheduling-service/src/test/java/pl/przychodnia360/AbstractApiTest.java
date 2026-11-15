package pl.przychodnia360;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.common.security.jwt.JwtService;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wspólne narzędzia testów HTTP usługi bez iam-service: placówka to sam identyfikator (tabela placówek jest w iam),
 * a token dostępu wystawia lokalny {@link JwtService} tym samym kluczem, którym podpisuje iam (ADR-M07).
 */
public abstract class AbstractApiTest extends AbstractIntegrationTest {

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired protected JwtService jwt;
    @Autowired protected TransactionTemplate tx;
    @Autowired protected JdbcClient jdbc;

    /** Odpowiedź HTTP z wygodnym dostępem do JSON. */
    protected record Resp(int status, String body, HttpHeaders headers) {
        public JsonNode json() {
            return JSON.readTree(body);
        }
    }

    protected static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected UUID newTenant() {
        return UUID.randomUUID();
    }

    @Autowired protected RemoteFakes.FakeCatalog catalog;
    @Autowired protected RemoteFakes.FakePatients patients;
    @Autowired protected RemoteFakes.FakeIdentity identity;

    /** Pacjent z kontem (rekord w atrapie iam) i tokenem z identyfikatorem pacjenta. */
    protected record PatientAccount(UUID userId, UUID patientId, String token) {
    }

    protected PatientAccount newPatientAccount(UUID tenantId, String firstName, String lastName) {
        UUID userId = UUID.randomUUID();
        UUID patientId = patients.add(tenantId, userId, firstName, lastName).id();
        return new PatientAccount(userId, patientId,
                token(new AuthenticatedUser(userId, tenantId, Role.PATIENT, null, patientId)));
    }

    /** Token pracownika dla konta założonego przez scheduling-service (rola i pracownik z atrapy iam). */
    protected String loginWithNewPassword(UUID tenantId, String userId) {
        RemoteFakes.Account account = identity.account(UUID.fromString(userId)).orElseThrow();
        return token(new AuthenticatedUser(account.userId(), tenantId, account.role(), account.employeeId(), null));
    }

    protected String tokenFor(UUID tenantId, Role role) {
        return token(new AuthenticatedUser(UUID.randomUUID(), tenantId, role, null, null));
    }

    protected String token(AuthenticatedUser user) {
        return jwt.issue(user);
    }

    protected <T> T inTenantResult(UUID tenantId, java.util.function.Supplier<T> action) {
        try (var h = TenantContext.open(new TenantId(tenantId))) {
            return tx.execute(s -> action.get());
        }
    }

    protected void inTenant(UUID tenantId, Runnable action) {
        try (var h = TenantContext.open(new TenantId(tenantId))) {
            tx.executeWithoutResult(s -> action.run());
        }
    }

    protected Resp call(String method, String path, String token, String jsonBody, String... headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        b.method(method, jsonBody == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody));
        if (jsonBody != null) {
            b.header("Content-Type", "application/json");
        }
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (headers.length > 0) {
            b.headers(headers);
        }
        try {
            HttpResponse<String> r = HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(r.statusCode(), r.body(), r.headers());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    protected Resp fetch(String path, String token) {
        return call("GET", path, token, null);
    }
}
