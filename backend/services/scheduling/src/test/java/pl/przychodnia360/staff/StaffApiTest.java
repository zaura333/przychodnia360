package pl.przychodnia360.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.common.security.Role;

class StaffApiTest extends AbstractApiTest {

    private String employee(String admin, String type, String first, String last) {
        Resp r = call("POST", "/api/v1/admin/employees", admin, "{\"email\":\"" + type.toLowerCase() + "." + unique()
                + "@example.test\",\"type\":\"" + type + "\",\"firstName\":\"" + first + "\",\"lastName\":\"" + last
                + "\",\"specialization\":\"Interna\"}");
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json().get("id").asString();
    }

    private static String week(String entries) {
        return "{\"entries\":[" + entries + "]}";
    }

    private static String entry(String day, String from, String to, String validFrom) {
        return "{\"dayOfWeek\":\"" + day + "\",\"startTime\":\"" + from + "\",\"endTime\":\"" + to
                + "\",\"validFrom\":\"" + validFrom + "\"}";
    }

    @Test
    void adminCreatesEmployeeWithPasswordlessAccountLinkedToIt() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        String id = employee(admin, "DOCTOR", "Adam", "Nowak");

        Resp list = fetch("/api/v1/admin/employees", admin);
        assertThat(list.json()).hasSize(1);
        assertThat(list.json().get(0).get("id").asString()).isEqualTo(id);
        String userId = list.json().get(0).get("userId").asString();
        // konto w iam założone sagą: powiązane z pracownikiem, z rolą wynikającą z typu pracownika
        var account = fakeIdentity.account(UUID.fromString(userId)).orElseThrow();
        assertThat(account.employeeId().toString()).isEqualTo(id);
        assertThat(account.role()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void duplicateEmployeeEmailIsConflict() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        String body = "{\"email\":\"dup@example.test\",\"type\":\"DOCTOR\",\"firstName\":\"A\",\"lastName\":\"B\"}";
        assertThat(call("POST", "/api/v1/admin/employees", admin, body).status()).isEqualTo(201);
        assertThat(call("POST", "/api/v1/admin/employees", admin, body).status()).isEqualTo(409);
    }

    @Test
    void weeklyScheduleOverlapIsRejectedAndReplacementWorks() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        String id = employee(admin, "DOCTOR", "Ewa", "Lis");

        Resp ok = call("PUT", "/api/v1/admin/employees/" + id + "/schedule", admin,
                week(entry("MONDAY", "08:00", "12:00", "2026-01-01") + "," + entry("MONDAY", "12:00", "16:00", "2026-01-01")));
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        assertThat(ok.json()).as("stykające się dyżury są dozwolone").hasSize(2);

        Resp overlap = call("PUT", "/api/v1/admin/employees/" + id + "/schedule", admin,
                week(entry("MONDAY", "08:00", "12:00", "2026-01-01") + "," + entry("MONDAY", "11:00", "14:00", "2026-01-01")));
        assertThat(overlap.status()).isEqualTo(409);
        assertThat(overlap.body()).contains("schedule-overlap");
        // nieudana wymiana nie zmienia dotychczasowego grafiku (transakcja wycofana)
        assertThat(call("GET", "/api/v1/admin/employees/" + id + "/schedule?from=2026-11-16&to=2026-11-16", admin, null)
                .json().get("weekly")).hasSize(2);

        assertThat(call("PUT", "/api/v1/admin/employees/" + id + "/schedule", admin,
                week(entry("MONDAY", "12:00", "08:00", "2026-01-01"))).status()).isEqualTo(400);
    }

    @Test
    void scheduleWindowExceptionsAndIntervalsAreExposed() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        String patient = tokenFor(tenant, Role.PATIENT);
        String id = employee(admin, "DOCTOR", "Jan", "Kot");
        call("PUT", "/api/v1/admin/employees/" + id + "/schedule", admin, week(entry("MONDAY", "08:00", "12:00", "2026-01-01")));

        Resp absence = call("POST", "/api/v1/admin/employees/" + id + "/schedule-exceptions", admin,
                "{\"date\":\"2026-11-16\",\"type\":\"ABSENCE\"}");
        assertThat(absence.status()).isEqualTo(201);
        Resp extra = call("POST", "/api/v1/admin/employees/" + id + "/schedule-exceptions", admin,
                "{\"date\":\"2026-11-17\",\"type\":\"EXTRA_SHIFT\",\"startTime\":\"10:00\",\"endTime\":\"14:00\"}");
        assertThat(extra.status()).isEqualTo(201);
        assertThat(call("POST", "/api/v1/admin/employees/" + id + "/schedule-exceptions", admin,
                "{\"date\":\"2026-11-18\",\"type\":\"EXTRA_SHIFT\"}").status()).isEqualTo(400);

        Resp view = fetch("/api/v1/doctors/" + id + "/schedule?from=2026-11-16&to=2026-11-23", patient);
        assertThat(view.status()).isEqualTo(200);
        // poniedziałek 16.11 to urlop, 17.11 dodatkowa zmiana, poniedziałek 23.11 normalny dyżur (czas zimowy, UTC+1)
        assertThat(view.json().get("intervals")).hasSize(2);
        assertThat(view.json().get("intervals").get(0).get("start").asString()).startsWith("2026-11-17T09:00");
        assertThat(view.json().get("intervals").get(1).get("start").asString()).startsWith("2026-11-23T07:00");

        assertThat(fetch("/api/v1/doctors/" + id + "/schedule?from=2026-11-01&to=2026-12-31", patient).status())
                .as("okno ponad 31 dni").isEqualTo(400);
        assertThat(call("DELETE", "/api/v1/admin/employees/" + id + "/schedule-exceptions/"
                + absence.json().get("id").asString(), admin, null).status()).isEqualTo(204);
    }

    @Test
    void doctorsListContainsOnlyActiveDoctorsAndDeactivationDisablesAccount() {
        UUID tenant = newTenant();
        String admin = tokenFor(tenant, Role.TENANT_ADMIN);
        String patient = tokenFor(tenant, Role.PATIENT);
        String doctor = employee(admin, "DOCTOR", "Anna", "Zet");
        String gone = employee(admin, "DOCTOR", "Olga", "Ex");
        employee(admin, "RECEPTIONIST", "Rita", "Rej");
        employee(admin, "MEDICAL_STAFF", "Pola", "Pie");

        assertThat(fetch("/api/v1/doctors", patient).json()).hasSize(2);
        assertThat(call("POST", "/api/v1/admin/employees/" + gone + "/deactivate", admin, null).status()).isEqualTo(204);
        Resp doctors = fetch("/api/v1/doctors", patient);
        assertThat(doctors.json()).hasSize(1);
        assertThat(doctors.json().get(0).get("id").asString()).isEqualTo(doctor);
        assertThat(fetch("/api/v1/doctors/" + employee(admin, "RECEPTIONIST", "R", "R") + "/schedule?from=2026-11-16&to=2026-11-16",
                patient).status()).as("rejestrator nie jest lekarzem").isEqualTo(404);
    }

    @Test
    void rolesAndTenantIsolation() {
        UUID a = newTenant();
        UUID b = newTenant();
        String adminA = tokenFor(a, Role.TENANT_ADMIN);
        String adminB = tokenFor(b, Role.TENANT_ADMIN);
        String id = employee(adminA, "DOCTOR", "Tajny", "Lekarz");

        assertThat(fetch("/api/v1/admin/employees", tokenFor(a, Role.RECEPTIONIST)).status()).isEqualTo(403);
        assertThat(fetch("/api/v1/admin/employees", null).status()).isEqualTo(401);
        assertThat(fetch("/api/v1/admin/employees", adminB).json()).isEmpty();
        assertThat(call("PUT", "/api/v1/admin/employees/" + id, adminB,
                "{\"firstName\":\"X\",\"lastName\":\"Y\"}").status()).isEqualTo(404);
        assertThat(call("POST", "/api/v1/admin/employees/" + id + "/deactivate", adminB, null).status()).isEqualTo(404);
        assertThat(call("PUT", "/api/v1/admin/employees/" + id + "/schedule", adminB,
                week(entry("MONDAY", "08:00", "12:00", "2026-01-01"))).status()).isEqualTo(404);
        assertThat(fetch("/api/v1/doctors", tokenFor(b, Role.PATIENT)).json()).isEmpty();
    }
}
