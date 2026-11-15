package pl.przychodnia360;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.testsupport.PostgresIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wspólne narzędzia testów HTTP usługi catalog: placówka to sam identyfikator (usługa nie zna tabeli placówek),
 * a tokeny wystawia {@link TestTokenIssuer}, którego JWKS usługa pobiera jak z iam.
 */
@CatalogIntegrationTest
public abstract class AbstractCatalogApiTest extends PostgresIntegrationTest {

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired protected TransactionTemplate tx;

    /** Odpowiedź HTTP z wygodnym dostępem do JSON. */
    protected record Resp(int status, String body, HttpHeaders headers) {
        public JsonNode json() {
            return JSON.readTree(body);
        }
    }

    protected UUID newTenant() {
        return UUID.randomUUID();
    }

    protected String tokenFor(UUID tenantId, Role role) {
        return TestTokenIssuer.token(tenantId, role);
    }

    protected String tokenForUser(UUID tenantId, UUID userId) {
        return TestTokenIssuer.token(tenantId, Role.TENANT_ADMIN, userId);
    }

    protected <T> T inTenantResult(UUID tenantId, java.util.function.Supplier<T> action) {
        try (var h = TenantContext.open(new TenantId(tenantId))) {
            return tx.execute(s -> action.get());
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
