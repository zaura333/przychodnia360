package pl.przychodnia360.patients;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractPatientApiTest;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.patients.api.PatientFacade;

class PatientApiTest extends AbstractPatientApiTest {

    @Autowired PatientFacade patients;

    private String create(String token, String first, String last) {
        Resp r = call("POST", "/api/v1/reception/patients", token,
                "{\"firstName\":\"" + first + "\",\"lastName\":\"" + last + "\",\"birthDate\":\"1990-05-17\",\"contactEmail\":\"x@example.test\"}");
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json().get("id").asString();
    }

    @Test
    void receptionistRegistersEditsAndSearchesPatients() {
        UUID tenant = newTenant();
        String reception = tokenFor(tenant, Role.RECEPTIONIST);
        String id = create(reception, "Anna", "Kowalska");
        create(reception, "Jan", "Kowalski");
        create(reception, "Piotr", "Nowak");

        Resp byLast = call("POST", "/api/v1/reception/patients/search", reception, "{\"query\":\"kowal\"}");
        assertThat(byLast.json().get("total").asLong()).isEqualTo(2);
        Resp byBoth = call("POST", "/api/v1/reception/patients/search", reception, "{\"query\":\"jan kowal\"}");
        assertThat(byBoth.json().get("total").asLong()).isEqualTo(1);
        assertThat(call("POST", "/api/v1/reception/patients/search", reception, "{\"query\":\"%\"}").json()
                .get("total").asLong()).as("znak LIKE nie jest wildcardem").isZero();
        assertThat(call("POST", "/api/v1/reception/patients/search", reception, "{\"query\":\"  \"}").status()).isEqualTo(400);

        Resp edit = call("PATCH", "/api/v1/reception/patients/" + id + "/contact", reception, "{\"contactEmail\":\"nowy@example.test\"}");
        assertThat(edit.json().get("contactEmail").asString()).isEqualTo("nowy@example.test");
        assertThat(call("PATCH", "/api/v1/reception/patients/" + id + "/contact", reception, "{\"contactEmail\":\"nie-email\"}").status()).isEqualTo(400);
    }

    @Test
    void patientSeesAndEditsOnlyOwnRecord() {
        UUID tenant = newTenant();
        UUID accountId = UUID.randomUUID();
        UUID patientId = inTenantResult(tenant, () -> {
            UUID id = patients.create("Ewa", "Zielińska", java.time.LocalDate.of(1985, 3, 2), null);
            patients.linkAccount(id, accountId);
            return id;
        });
        String token = tokenFor(tenant, Role.PATIENT, patientId);

        Resp me = fetch("/api/v1/me/patient", token);
        assertThat(me.json().get("id").asString()).isEqualTo(patientId.toString());
        assertThat(me.json().get("hasAccount").asBoolean()).isTrue();
        assertThat(call("PATCH", "/api/v1/me/patient", token, "{\"contactEmail\":\"ewa@example.test\"}").json()
                .get("contactEmail").asString()).isEqualTo("ewa@example.test");
        assertThat(call("POST", "/api/v1/reception/patients/search", token, "{\"query\":\"ewa\"}").status()).isEqualTo(403);
    }

    @Test
    void patientWithoutRecordAndOtherRolesAreRejected() {
        UUID tenant = newTenant();
        assertThat(fetch("/api/v1/me/patient", tokenFor(tenant, Role.PATIENT)).status()).isEqualTo(404);
        assertThat(fetch("/api/v1/me/patient", tokenFor(tenant, Role.RECEPTIONIST)).status()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/reception/patients", tokenFor(tenant, Role.DOCTOR),
                "{\"firstName\":\"A\",\"lastName\":\"B\",\"birthDate\":\"1990-01-01\"}").status()).isEqualTo(403);
    }

    @Test
    void patientsAreIsolatedBetweenTenants() {
        UUID a = newTenant();
        UUID b = newTenant();
        String receptionA = tokenFor(a, Role.RECEPTIONIST);
        String receptionB = tokenFor(b, Role.RECEPTIONIST);
        String id = create(receptionA, "Tajna", "Pacjentka");

        assertThat(call("POST", "/api/v1/reception/patients/search", receptionB, "{\"query\":\"pacjentka\"}").json()
                .get("total").asLong()).isZero();
        assertThat(call("PATCH", "/api/v1/reception/patients/" + id + "/contact", receptionB,
                "{\"contactEmail\":\"x@example.test\"}").status()).isEqualTo(404);
    }

    @Test
    void invalidRegistrationDataIsRejected() {
        UUID tenant = newTenant();
        String reception = tokenFor(tenant, Role.RECEPTIONIST);
        assertThat(call("POST", "/api/v1/reception/patients", reception,
                "{\"firstName\":\"\",\"lastName\":\"B\",\"birthDate\":\"1990-01-01\"}").status()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/reception/patients", reception,
                "{\"firstName\":\"A\",\"lastName\":\"B\",\"birthDate\":\"2999-01-01\"}").status()).isEqualTo(400);
    }
}
