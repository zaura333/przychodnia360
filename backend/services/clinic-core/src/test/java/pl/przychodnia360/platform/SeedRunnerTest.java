package pl.przychodnia360.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.przychodnia360.AbstractApiTest;

/** Seed uruchamiany w profilu local: dwie placówki, logowanie ról i scenariusz rejestratora. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"seed.password=seed-test-passphrase-1", "seed.enabled=true"})
@ActiveProfiles({"test", "local"})
class SeedRunnerTest extends AbstractApiTest {

    @Autowired SeedRunner seed;

    private String login(String slug, String email) {
        Resp r = call("POST", "/api/v1/auth/login", null, "{\"tenantSlug\":\"" + slug + "\",\"email\":\"" + email
                + "\",\"password\":\"seed-test-passphrase-1\"}");
        assertThat(r.status()).as(email + " " + r.body()).isEqualTo(200);
        return r.json().get("accessToken").asString();
    }

    private java.util.UUID tenantIdOf(String slug) {
        try (var h = pl.przychodnia360.shared.TenantContext.openWithoutTenant()) {
            return tx.execute(s -> jdbc.sql("SELECT id FROM tenants WHERE slug = ?").param(slug)
                    .query(java.util.UUID.class).single());
        }
    }

    @Test
    void seedCreatesTwoClinicsWithLoginsForEveryRoleAndIsIdempotent() throws Exception {
        seed.run(null);
        seed.run(null);

        for (String slug : new String[] {"klinika-a", "klinika-b"}) {
            String domain = slug + ".example.test";
            String admin = login(slug, "admin@" + domain);
            String reception = login(slug, "rejestracja@" + domain);
            String doctor = login(slug, "lekarz1@" + domain);
            login(slug, "lekarz2@" + domain);
            login(slug, "pielegniarka@" + domain);
            String patient = login(slug, "pacjent1@" + domain);

            assertThat(fetch("/api/v1/doctors", patient).json()).hasSize(2);
            assertThat(fakeCatalog.servicesOf(tenantIdOf(slug))).as("katalog z seedu w " + slug).hasSize(4);
            assertThat(fakePatients.patientsOf(tenantIdOf(slug))).as("pacjenci z seedu w " + slug).hasSize(4);
            assertThat(fetch("/api/v1/admin/employees", admin).json()).hasSize(4);
            LocalDate from = LocalDate.now();
            Resp terminarz = fetch("/api/v1/reception/appointments?from=" + from + "&to=" + from.plusDays(7), reception);
            assertThat(terminarz.json()).as("wizyty z seeda w " + slug).hasSize(2);
            assertThat(fetch("/api/v1/me/schedule?from=" + from + "&to=" + from.plusDays(7), doctor)
                    .json().get("appointments")).hasSize(2);
        }
        assertThat(fetch("/api/v1/doctors", login("klinika-a", "pacjent2@klinika-a.example.test")).json().toString())
                .doesNotContain("Wiśniewska");
    }
}
