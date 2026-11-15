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
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wspólne narzędzia testów HTTP scheduling: placówki, użytkownicy i wywołania z tokenem. Konta i placówki należą do
 * iam (S3), więc test nie loguje się przez API: tokeny podpisuje testowy wystawca, którego JWKS usługa weryfikuje tak
 * jak na produkcji, a konta pracowników trzyma atrapa {@link FakeIdentity}.
 */
public abstract class AbstractApiTest extends AbstractIntegrationTest {

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired protected TransactionTemplate tx;
    @Autowired protected JdbcClient jdbc;
    @Autowired protected PatientFacade patientFacade;
    @Autowired protected FakeCatalog fakeCatalog;
    @Autowired protected FakePatients fakePatients;
    @Autowired protected FakeIdentity fakeIdentity;
    @Autowired protected pl.przychodnia360.shared.DomainEventPublisher domainEvents;

    /** Użytkownik testowy (konto w iam): identyfikator, placówka, e-mail i rola. */
    protected record TestUser(UUID id, UUID tenantId, String email, Role role) {
    }

    /** Konto pacjenta z rekordem pacjenta i tokenem. */
    protected record PatientAccount(TestUser user, UUID patientId, String token) {
    }

    /** Odpowiedź HTTP z wygodnym dostępem do JSON. */
    protected record Resp(int status, String body, HttpHeaders headers) {
        public JsonNode json() {
            return JSON.readTree(body);
        }
    }

    /**
     * Publikuje przykładowe zdarzenie domenowe scheduling (AppointmentCancelled) w bieżącej transakcji i placówce, żeby
     * testy outboxu, przekaźnika i śladu miały producenta zdarzeń. Zwraca identyfikator agregatu.
     */
    protected UUID publishSampleEvent() {
        UUID aggregate = UUID.randomUUID();
        domainEvents.publish(new pl.przychodnia360.appointments.api.AppointmentCancelled(
                TenantContext.requireTenant().value(), aggregate, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                java.time.Instant.now()));
        return aggregate;
    }

    protected static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Placówka należy do iam: w scheduling istnieje tylko jako tenant_id w tokenie i w wierszach (RLS). */
    protected UUID newTenant() {
        return UUID.randomUUID();
    }

    /** Użytkownik o roli w placówce (konto w iam, tu tylko tożsamość w tokenie). */
    protected TestUser newUser(UUID tenantId, Role role) {
        return new TestUser(UUID.randomUUID(), tenantId, role.name().toLowerCase() + "." + unique() + "@example.test", role);
    }

    /** Pacjent z rekordem pacjenta (atrapa patient-service) i tokenem z powiązaniem {@code pid}. */
    protected PatientAccount newPatientAccount(UUID tenantId, String firstName, String lastName) {
        TestUser user = newUser(tenantId, Role.PATIENT);
        UUID patientId;
        try (var h = TenantContext.open(new TenantId(tenantId))) {
            patientId = patientFacade.create(firstName, lastName, java.time.LocalDate.of(1990, 1, 1), null);
            patientFacade.linkAccount(patientId, user.id());
        }
        return new PatientAccount(user, patientId, TestTokenIssuer.token(tenantId, Role.PATIENT, user.id(), null, patientId));
    }

    /**
     * Token pracownika założonego przez API (konto w atrapie iam): jak po ustawieniu hasła z linku i zalogowaniu, z
     * rolą i powiązaniem {@code eid} z konta.
     */
    protected String loginWithNewPassword(UUID tenantId, String userId) {
        FakeIdentity.Account account = fakeIdentity.account(UUID.fromString(userId)).orElseThrow();
        return TestTokenIssuer.token(tenantId, account.role(), account.userId(), account.employeeId(), null);
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

    /** Token dostępu użytkownika (wystawca testowy zamiast logowania w iam). */
    protected String login(UUID tenantId, TestUser user) {
        return TestTokenIssuer.token(tenantId, user.role(), user.id());
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
