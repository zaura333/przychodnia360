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
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.api.event.UserDeactivated;
import pl.przychodnia360.identity.application.PasswordHasher;
import pl.przychodnia360.identity.application.UserRepository;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.patients.api.PatientRegistry;
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
    @Autowired protected PatientRegistry patientRegistry;
    @Autowired protected FakePatients fakePatients;
    @Autowired protected pl.przychodnia360.shared.DomainEventPublisher domainEvents;
    @Autowired protected RecordingMailSender mails;

    /** Konto pacjenta z rekordem pacjenta i tokenem. */
    protected record PatientAccount(User user, UUID patientId, String token) {
    }

    /** Odpowiedź HTTP z wygodnym dostępem do JSON. */
    protected record Resp(int status, String body, HttpHeaders headers) {
        public JsonNode json() {
            return JSON.readTree(body);
        }
    }

    /**
     * Publikuje przykładowe zdarzenie domenowe iam (UserDeactivated) w bieżącej transakcji i placówce, żeby testy outboxu,
     * przekaźnika i śladu miały producenta zdarzeń w clinic-core. Zwraca identyfikator agregatu.
     */
    protected UUID publishSampleEvent() {
        UUID aggregate = UUID.randomUUID();
        domainEvents.publish(new pl.przychodnia360.identity.api.event.UserDeactivated(TenantContext.requireTenant().value(),
                aggregate, java.time.Instant.now()));
        return aggregate;
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

    /** Pacjent z kontem powiązanym z rekordem pacjenta, już zalogowany. */
    protected PatientAccount newPatientAccount(UUID tenantId, String firstName, String lastName) {
        User user = newUser(tenantId, Role.PATIENT);
        UUID patientId;
        try (var h = TenantContext.open(new TenantId(tenantId))) {
            patientId = tx.execute(s -> {
                UUID id = Ids.newId();
                user.linkPatient(id);
                users.update(user);
                patientRegistry.ensure(id, firstName, lastName, java.time.LocalDate.of(1990, 1, 1), null, user.id());
                return id;
            });
        }
        return new PatientAccount(user, patientId, login(tenantId, user));
    }

    /** Ustawia hasło konta utworzonego bez hasła (np. pracownika) i zwraca token po zalogowaniu. */
    protected String loginWithNewPassword(UUID tenantId, String userId) {
        User user = inTenantResult(tenantId, () -> users.findById(UUID.fromString(userId)).orElseThrow());
        user.changePassword(hasher.hash(PASSWORD));
        inTenant(tenantId, () -> users.update(user));
        return login(tenantId, user);
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

    protected Resp fetch(String path, String token) {
        return call("GET", path, token, null);
    }
}
