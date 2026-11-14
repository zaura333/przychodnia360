package pl.przychodnia360.appointments;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.common.time.ClinicTime;
import pl.przychodnia360.identity.api.Role;

/** Wspólne dane: placówka, usługa 30 min, lekarz z dyżurem 8-16 przez cały tydzień, pacjent i rejestrator. */
abstract class AbstractAppointmentTest extends AbstractApiTest {

    protected UUID tenant;
    protected String admin;
    protected String reception;
    protected String doctorId;
    protected String doctorUserId;
    protected String serviceId;
    protected PatientAccount patient;
    /** Poniedziałek co najmniej trzy dni w przyszłości (dyżur poniedziałek-niedziela, więc dowolny dzień pasuje). */
    protected LocalDate day;

    @BeforeEach
    void setUp() {
        tenant = newTenant();
        admin = tokenFor(tenant, Role.TENANT_ADMIN);
        reception = tokenFor(tenant, Role.RECEPTIONIST);
        patient = newPatientAccount(tenant, "Ewa", "Pacjentka");
        day = LocalDate.now().plusDays(3);

        String category = call("POST", "/api/v1/admin/service-categories", admin, "{\"name\":\"Kons\"}").json().get("id").asString();
        serviceId = call("POST", "/api/v1/admin/services", admin, "{\"categoryId\":\"" + category
                + "\",\"name\":\"Konsultacja\",\"price\":100,\"durationMinutes\":30,\"performerType\":\"DOCTOR\"}")
                .json().get("id").asString();
        Resp doctor = call("POST", "/api/v1/admin/employees", admin, "{\"email\":\"lek." + unique()
                + "@example.test\",\"type\":\"DOCTOR\",\"firstName\":\"Adam\",\"lastName\":\"Lekarz\"}");
        doctorId = doctor.json().get("id").asString();
        doctorUserId = doctor.json().get("userId").asString();
        StringBuilder entries = new StringBuilder();
        for (DayOfWeek d : DayOfWeek.values()) {
            entries.append(entries.isEmpty() ? "" : ",").append("{\"dayOfWeek\":\"").append(d)
                    .append("\",\"startTime\":\"08:00\",\"endTime\":\"16:00\",\"validFrom\":\"2020-01-01\"}");
        }
        assertThat(call("PUT", "/api/v1/admin/employees/" + doctorId + "/schedule", admin,
                "{\"entries\":[" + entries + "]}").status()).isEqualTo(200);
    }

    protected String at(String time) {
        return ClinicTime.toInstant(day, LocalTime.parse(time)).toString();
    }

    protected String bookBody(String time, String patientId) {
        return "{\"serviceId\":\"" + serviceId + "\",\"employeeId\":\"" + doctorId + "\",\"startAt\":\"" + at(time) + "\""
                + (patientId == null ? "" : ",\"patientId\":\"" + patientId + "\"") + "}";
    }

    protected Resp bookAsPatient(String time) {
        return call("POST", "/api/v1/appointments", patient.token(), bookBody(time, null));
    }
}
