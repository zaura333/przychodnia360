package pl.przychodnia360.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.shared.DemoIds;

/**
 * Seed iam-service w profilu local: dwie placówki, logowanie każdej roli i identyfikatory zgodne z seedami
 * catalog-service i scheduling-service ({@link DemoIds}). Widoki grafików i wizyt sprawdza e2e na trzech usługach.
 */
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

    @Test
    void seedCreatesTwoClinicsWithLoginsForEveryRoleAndIsIdempotent() {
        seed.run(null);
        seed.run(null);

        for (String slug : new String[] {"klinika-a", "klinika-b"}) {
            String domain = slug + ".example.test";
            assertThat(fetch("/api/v1/tenant", login(slug, "admin@" + domain)).json().get("id").asString())
                    .isEqualTo(DemoIds.tenant(slug).toString());
            for (String employee : new String[] {"lekarz1", "lekarz2", "pielegniarka", "rejestracja"}) {
                Resp me = fetch("/api/v1/me", login(slug, employee + "@" + domain));
                assertThat(me.json().get("employeeId").asString()).as(employee)
                        .isEqualTo(DemoIds.employee(slug, employee).toString());
            }
            String patient = login(slug, "pacjent1@" + domain);
            assertThat(fetch("/api/v1/me/patient", patient).status()).isEqualTo(200);
            inTenant(DemoIds.tenant(slug), () -> assertThat(patientFacade.find(DemoIds.patient(slug, "bez-konta")))
                    .as("pacjent bez konta z wizytami z seeda scheduling-service").isPresent());
        }
    }
}
