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
import pl.przychodnia360.identity.api.Role;
import pl.przychodnia360.identity.application.PasswordHasher;
import pl.przychodnia360.identity.application.UserRepository;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Wspólne narzędzia testów HTTP: placówki, użytkownicy, logowanie przez API i wywołania z tokenem. */
public abstract class AbstractApiTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "correct horse battery";
    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired protected UserRepository users;
    @Autowired protected PasswordHasher hasher;
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
        String suffix = unique();
        UUID id = UUID.randomUUID();
        try (var h = TenantContext.openWithoutTenant()) {
            tx.executeWithoutResult(s -> jdbc.sql("INSERT INTO tenants (id, slug, name) VALUES (?, ?, ?)")
                    .params(id, "t-" + suffix, "Klinika " + suffix).update());
        }
        return id;
    }

    protected String slugOf(UUID tenantId) {
        try (var h = TenantContext.openWithoutTenant()) {
            return tx.execute(s -> jdbc.sql("SELECT slug FROM tenants WHERE id = ?").param(tenantId)
                    .query(String.class).single());
        }
    }

    /** Tworzy konto w placówce i zwraca jego dane (identyfikator, e-mail). */
    protected User newUser(UUID tenantId, Role role) {
        User user = User.create(Ids.newId(), tenantId, role.name().toLowerCase() + "." + unique() + "@example.test", role,
                hasher.hash(PASSWORD));
        try (var h = TenantContext.open(new TenantId(tenantId))) {
            tx.executeWithoutResult(s -> users.insert(user));
        }
        return user;
    }

    protected void inTenant(UUID tenantId, Runnable action) {
        try (var h = TenantContext.open(new TenantId(tenantId))) {
            tx.executeWithoutResult(s -> action.run());
        }
    }

    /** Loguje użytkownika przez API i zwraca token dostępu. */
    protected String login(UUID tenantId, User user) {
        Resp r = call("POST", "/api/v1/auth/login", null, "{\"tenantSlug\":\"" + slugOf(tenantId) + "\",\"email\":\""
                + user.email() + "\",\"password\":\"" + PASSWORD + "\"}");
        if (r.status() != 200) {
            throw new IllegalStateException("Logowanie nieudane: " + r.status() + " " + r.body());
        }
        return r.json().get("accessToken").asString();
    }

    protected String tokenFor(UUID tenantId, Role role) {
        return login(tenantId, newUser(tenantId, role));
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

    protected Resp get(String path, String token) {
        return call("GET", path, token, null);
    }
}
