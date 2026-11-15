package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.domain.PasswordPolicy;
import pl.przychodnia360.shared.ValidationException;

class AccountFlowTest extends AbstractApiTest {

    private static final String NEW_PASSWORD = "another strong passphrase";

    private String registerBody(String slug, String email, String password) {
        return "{\"tenantSlug\":\"" + slug + "\",\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"firstName\":\"Maria\",\"lastName\":\"Nowak\",\"birthDate\":\"1991-04-12\"}";
    }

    private Resp loginAs(String slug, String email, String password) {
        return call("POST", "/api/v1/auth/login", null, "{\"tenantSlug\":\"" + slug + "\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}");
    }

    @Test
    void patientRegistersAndThenLogsInWithLinkedRecord() {
        UUID tenant = newTenant();
        String slug = slugOf(tenant);
        String email = "maria." + unique() + "@example.test";

        Resp r = call("POST", "/api/v1/auth/register-patient", null, registerBody(slug, email, PASSWORD));
        assertThat(r.status()).isEqualTo(202);

        Resp login = loginAs(slug, email, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        String token = login.json().get("accessToken").asString();
        // rekord pacjenta powstał w usłudze pacjentów (atrapa) i jest powiązany z kontem w obie strony
        var user = inTenantResult(tenant, () -> users.findByEmail(tenant, email)).orElseThrow();
        var patient = fakePatients.find(tenant, user.patientId()).orElseThrow();
        assertThat(patient.firstName()).isEqualTo("Maria");
        assertThat(patient.userId()).isEqualTo(user.id());
        assertThat(mails.sentTo(email)).hasSize(1);
    }

    @Test
    void registrationResponseIsTheSameForNewTakenAndUnknownClinic() {
        UUID tenant = newTenant();
        String slug = slugOf(tenant);
        String email = "dup." + unique() + "@example.test";

        Resp first = call("POST", "/api/v1/auth/register-patient", null, registerBody(slug, email, PASSWORD));
        Resp taken = call("POST", "/api/v1/auth/register-patient", null, registerBody(slug, email, PASSWORD));
        Resp unknown = call("POST", "/api/v1/auth/register-patient", null,
                registerBody("no-such-clinic", "x." + unique() + "@example.test", PASSWORD));

        assertThat(first.status()).isEqualTo(202);
        assertThat(taken.status()).isEqualTo(202);
        assertThat(unknown.status()).isEqualTo(202);
        assertThat(first.body()).isEqualTo(taken.body()).isEqualTo(unknown.body());
        assertThat(mails.sentTo(email)).hasSize(2);
        assertThat(mails.sentTo(email).getLast().body()).contains("już istnieje");
        // zajęty e-mail nie utworzył drugiego rekordu pacjenta
        assertThat(fakePatients.patientsOf(tenant)).hasSize(1);
    }

    @Test
    void weakPasswordsAreRejectedByRegistrationAndPolicy() {
        UUID tenant = newTenant();
        Resp r = call("POST", "/api/v1/auth/register-patient", null,
                registerBody(slugOf(tenant), "w." + unique() + "@example.test", "short"));
        assertThat(r.status()).isEqualTo(400);
        for (String weak : new String[] {"short", "password1234", "aaaaaaaaaaaaaaaa", "x".repeat(129)}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> PasswordPolicy.validate(weak))
                    .isInstanceOf(ValidationException.class);
        }
        PasswordPolicy.validate("a perfectly fine passphrase");
    }

    @Test
    void resetRequestLooksTheSameForExistingAndMissingAccounts() {
        UUID tenant = newTenant();
        var user = newUser(tenant, Role.RECEPTIONIST);
        String slug = slugOf(tenant);

        Resp existing = call("POST", "/api/v1/auth/password-reset/request", null,
                "{\"tenantSlug\":\"" + slug + "\",\"email\":\"" + user.email() + "\"}");
        Resp missing = call("POST", "/api/v1/auth/password-reset/request", null,
                "{\"tenantSlug\":\"" + slug + "\",\"email\":\"nobody@example.test\"}");
        Resp noClinic = call("POST", "/api/v1/auth/password-reset/request", null,
                "{\"tenantSlug\":\"no-such-clinic\",\"email\":\"" + user.email() + "\"}");

        assertThat(existing.status()).isEqualTo(202);
        assertThat(existing.body()).isEqualTo(missing.body()).isEqualTo(noClinic.body());
        assertThat(mails.sentTo(user.email())).hasSize(1);
        assertThat(mails.sentTo("nobody@example.test")).isEmpty();
    }

    @Test
    void resetTokenSetsNewPasswordOnceAndRevokesSessions() {
        UUID tenant = newTenant();
        var user = newUser(tenant, Role.RECEPTIONIST);
        String slug = slugOf(tenant);
        Resp login = loginAs(slug, user.email(), PASSWORD);
        String refreshCookie = login.headers().allValues("set-cookie").getFirst().split(";")[0];

        call("POST", "/api/v1/auth/password-reset/request", null,
                "{\"tenantSlug\":\"" + slug + "\",\"email\":\"" + user.email() + "\"}");
        String token = mails.lastTokenFor(user.email());

        assertThat(call("POST", "/api/v1/auth/password-reset/confirm", null,
                "{\"token\":\"" + token + "\",\"newPassword\":\"short\"}").status()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/auth/password-reset/confirm", null,
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}").status()).isEqualTo(204);

        assertThat(loginAs(slug, user.email(), NEW_PASSWORD).status()).isEqualTo(200);
        assertThat(loginAs(slug, user.email(), PASSWORD).status()).as("stare hasło nie działa").isEqualTo(401);
        assertThat(call("POST", "/api/v1/auth/refresh", null, null, "Cookie", refreshCookie).status())
                .as("stara sesja unieważniona po zmianie hasła").isEqualTo(401);
        Resp reuse = call("POST", "/api/v1/auth/password-reset/confirm", null,
                "{\"token\":\"" + token + "\",\"newPassword\":\"yet another passphrase\"}");
        assertThat(reuse.status()).as("token jednorazowy").isEqualTo(422);
        assertThat(reuse.body()).contains("invalid-or-expired-token");
    }

    @Test
    void expiredAndForgedTokensAreRejected() {
        UUID tenant = newTenant();
        var user = newUser(tenant, Role.PATIENT);
        call("POST", "/api/v1/auth/password-reset/request", null,
                "{\"tenantSlug\":\"" + slugOf(tenant) + "\",\"email\":\"" + user.email() + "\"}");
        String token = mails.lastTokenFor(user.email());
        inTenant(tenant, () -> jdbc.sql("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'").update());

        assertThat(call("POST", "/api/v1/auth/password-reset/confirm", null,
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}").status()).isEqualTo(422);
        assertThat(call("POST", "/api/v1/auth/password-reset/confirm", null,
                "{\"token\":\"garbage\",\"newPassword\":\"" + NEW_PASSWORD + "\"}").status()).isEqualTo(422);
        // token z cudzej placówki w prefiksie nie znajduje wiersza
        String foreign = newTenant() + token.substring(token.indexOf('.'));
        assertThat(call("POST", "/api/v1/auth/password-reset/confirm", null,
                "{\"token\":\"" + foreign + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}").status()).isEqualTo(422);
    }

    @Test
    void newEmployeeSetsPasswordFromEmailedLink() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        String email = "nowy." + unique() + "@example.test";
        Resp created = call("POST", "/api/v1/admin/employees", admin, "{\"email\":\"" + email
                + "\",\"type\":\"RECEPTIONIST\",\"firstName\":\"Nowy\",\"lastName\":\"Pracownik\"}");
        assertThat(created.status()).isEqualTo(201);

        assertThat(loginAs(slugOf(tenant), email, NEW_PASSWORD).status()).as("konto bez hasła").isEqualTo(401);
        String token = mails.lastTokenFor(email);
        assertThat(call("POST", "/api/v1/auth/password-reset/confirm", null,
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}").status()).isEqualTo(204);
        Resp login = loginAs(slugOf(tenant), email, NEW_PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        Resp me = fetch("/api/v1/me", login.json().get("accessToken").asString());
        assertThat(me.json().get("employeeId").asString()).isEqualTo(created.json().get("id").asString());
    }
}
