package pl.przychodnia360.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.StubHttpServer;
import pl.przychodnia360.StubHttpServer.Reply;
import pl.przychodnia360.TestTokenIssuer;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.shared.SubjectIds;

/**
 * Seed scheduling w profilu local (S3): loguje się w iam jako administrator placówki (tu atrapa HTTP iam), zakłada
 * katalog, pracowników sagą, grafiki, pacjenta bez konta i jego wizyty; drugi przebieg niczego
 * nie duplikuje.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"seed.password=seed-test-passphrase-1", "seed.enabled=true"})
@ActiveProfiles({"test", "local"})
class SeedRunnerTest extends AbstractApiTest {

    private static final Map<String, UUID> TENANTS = Map.of("klinika-a", UUID.randomUUID(), "klinika-b", UUID.randomUUID());
    private static final StubHttpServer IAM = new StubHttpServer().respond(SeedRunnerTest::iam);

    @Autowired SeedRunner seed;

    /** Atrapa iam: logowanie administratora placówki seedu zwraca token z tą placówką (tid). */
    private static Reply iam(StubHttpServer.Received request) {
        if (!request.path().equals("/api/v1/auth/login") || !request.body().contains("seed-test-passphrase-1")) {
            return Reply.status(401);
        }
        for (var tenant : TENANTS.entrySet()) {
            if (request.body().contains("\"" + tenant.getKey() + "\"")) {
                return Reply.ok("{\"accessToken\":\"" + TestTokenIssuer.token(tenant.getValue(), Role.TENANT_ADMIN,
                        UUID.randomUUID()) + "\",\"tokenType\":\"Bearer\",\"expiresIn\":600}");
            }
        }
        return Reply.status(401);
    }

    @DynamicPropertySource
    static void iamUrl(DynamicPropertyRegistry registry) {
        registry.add("p360.iam.url", IAM::url);
    }

    @AfterAll
    static void stop() {
        IAM.close();
    }

    private String employeeToken(UUID tenant, String email, Role role) {
        UUID employeeId = SubjectIds.employee(tenant, email);
        return TestTokenIssuer.token(tenant, role, UUID.randomUUID(), employeeId, null);
    }

    @Test
    void seedFillsBothClinicsCreatedByIamAndIsIdempotent() throws Exception {
        seed.run(null);
        seed.run(null);

        for (var entry : TENANTS.entrySet()) {
            String domain = entry.getKey() + ".example.test";
            UUID tenant = entry.getValue();
            String admin = TestTokenIssuer.token(tenant, Role.TENANT_ADMIN, UUID.randomUUID());
            String patient = TestTokenIssuer.token(tenant, Role.PATIENT, UUID.randomUUID());
            String reception = employeeToken(tenant, "rejestracja@" + domain, Role.RECEPTIONIST);
            String doctor = employeeToken(tenant, "lekarz1@" + domain, Role.DOCTOR);

            assertThat(fetch("/api/v1/doctors", patient).json()).hasSize(2);
            assertThat(fakeCatalog.servicesOf(tenant)).as("katalog z seedu w " + entry.getKey()).hasSize(4);
            assertThat(fakePatients.patientsOf(tenant)).as("pacjent bez konta, bez duplikatu").hasSize(1);
            assertThat(fetch("/api/v1/admin/employees", admin).json()).as("bez duplikatów po drugim przebiegu").hasSize(4);
            LocalDate from = LocalDate.now();
            Resp terminarz = fetch("/api/v1/reception/appointments?from=" + from + "&to=" + from.plusDays(7), reception);
            assertThat(terminarz.json()).as("wizyty z seeda w " + entry.getKey()).hasSize(2);
            assertThat(fetch("/api/v1/me/schedule?from=" + from + "&to=" + from.plusDays(7), doctor)
                    .json().get("appointments")).hasSize(2);
        }
        String patientA = TestTokenIssuer.token(TENANTS.get("klinika-a"), Role.PATIENT, UUID.randomUUID());
        assertThat(fetch("/api/v1/doctors", patientA).json().toString()).doesNotContain("Wiśniewska");
    }
}
