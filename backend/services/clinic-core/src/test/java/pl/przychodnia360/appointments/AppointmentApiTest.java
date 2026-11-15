package pl.przychodnia360.appointments;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.common.security.Role;

class AppointmentApiTest extends AbstractAppointmentTest {

    @Test
    void patientBooksAndSeesOwnAppointmentWithNames() {
        Resp r = bookAsPatient("09:00");
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        assertThat(r.json().get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(r.json().get("serviceName").asString()).isEqualTo("Konsultacja");
        assertThat(r.json().get("employeeName").asString()).isEqualTo("Adam Lekarz");

        Resp mine = fetch("/api/v1/me/appointments", patient.token());
        assertThat(mine.json()).hasSize(1);
        assertThat(mine.json().get(0).has("patientId")).as("widok pacjenta bez danych pacjentów").isFalse();
    }

    @Test
    void receptionistBooksForPatientAndMustNameThePatient() {
        assertThat(call("POST", "/api/v1/appointments", reception, bookBody("09:00", patient.patientId().toString())).status())
                .isEqualTo(201);
        assertThat(call("POST", "/api/v1/appointments", reception, bookBody("10:00", null)).status()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/appointments", reception, bookBody("10:00", UUID.randomUUID().toString())).status())
                .as("nieistniejący pacjent").isEqualTo(404);
    }

    @Test
    void patientCannotBookForSomeoneElse() {
        PatientAccount other = newPatientAccount(tenant, "Inny", "Pacjent");
        Resp r = call("POST", "/api/v1/appointments", patient.token(), bookBody("09:00", other.patientId().toString()));
        assertThat(r.status()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/appointments", patient.token(), bookBody("09:00", patient.patientId().toString())).status())
                .isEqualTo(201);
    }

    @Test
    void conflictsAreRejectedButAdjacentSlotsAreAllowed() {
        assertThat(bookAsPatient("09:00").status()).isEqualTo(201);
        PatientAccount other = newPatientAccount(tenant, "Anna", "Druga");

        Resp sameSlot = call("POST", "/api/v1/appointments", other.token(), bookBody("09:00", null));
        assertThat(sameSlot.status()).isEqualTo(409);
        assertThat(sameSlot.body()).contains("slot-taken");
        assertThat(call("POST", "/api/v1/appointments", other.token(), bookBody("09:15", null)).status())
                .as("częściowe nakładanie").isEqualTo(409);
        assertThat(call("POST", "/api/v1/appointments", other.token(), bookBody("09:30", null)).status())
                .as("styk końcem i początkiem").isEqualTo(201);
        assertThat(call("POST", "/api/v1/appointments", other.token(), bookBody("08:30", null)).status())
                .as("styk początkiem i końcem").isEqualTo(201);
    }

    @Test
    void patientCannotHaveTwoOverlappingAppointmentsWithDifferentDoctors() {
        Resp second = call("POST", "/api/v1/admin/employees", admin, "{\"email\":\"lek2." + unique()
                + "@example.test\",\"type\":\"DOCTOR\",\"firstName\":\"Bartek\",\"lastName\":\"Drugi\"}");
        String secondId = second.json().get("id").asString();
        call("PUT", "/api/v1/admin/employees/" + secondId + "/schedule", admin, "{\"entries\":[{\"dayOfWeek\":\""
                + day.getDayOfWeek() + "\",\"startTime\":\"08:00\",\"endTime\":\"16:00\",\"validFrom\":\"2020-01-01\"}]}");

        assertThat(bookAsPatient("09:00").status()).isEqualTo(201);
        Resp overlap = call("POST", "/api/v1/appointments", patient.token(), "{\"serviceId\":\"" + serviceId
                + "\",\"employeeId\":\"" + secondId + "\",\"startAt\":\"" + at("09:15") + "\"}");
        assertThat(overlap.status()).isEqualTo(409);
        assertThat(overlap.body()).contains("patient-overlap");
    }

    @Test
    void businessRulesReturn422() {
        assertThat(call("POST", "/api/v1/appointments", patient.token(), "{\"serviceId\":\"" + serviceId
                + "\",\"employeeId\":\"" + doctorId + "\",\"startAt\":\"" + Instant.now().minusSeconds(86400) + "\"}").status())
                .as("w przeszłości").isEqualTo(422);
        assertThat(bookAsPatient("07:30").status()).as("przed dyżurem").isEqualTo(422);
        assertThat(bookAsPatient("15:45").status()).as("wizyta wychodzi poza dyżur").isEqualTo(422);
        assertThat(bookAsPatient("09:10").status()).as("poza siatką").isEqualTo(422);
        // lekarz nie wykona usługi personelu medycznego
        String nurseService = fakeCatalog.add(tenant, "Pobranie", new java.math.BigDecimal("10"), 15, "MEDICAL_STAFF").toString();
        assertThat(call("POST", "/api/v1/appointments", patient.token(), "{\"serviceId\":\"" + nurseService
                + "\",\"employeeId\":\"" + doctorId + "\",\"startAt\":\"" + at("09:00") + "\"}").status())
                .as("wykonawca niezgodny z usługą").isEqualTo(422);
        // nieaktywna usługa
        fakeCatalog.deactivate(tenant, java.util.UUID.fromString(serviceId));
        assertThat(bookAsPatient("09:00").status()).as("usługa nieaktywna").isEqualTo(422);
    }

    @Test
    void receptionistReschedulesAndCancelsFreeingTheSlot() {
        String id = call("POST", "/api/v1/appointments", reception, bookBody("09:00", patient.patientId().toString()))
                .json().get("id").asString();

        Resp moved = call("POST", "/api/v1/appointments/" + id + "/reschedule", reception, "{\"startAt\":\"" + at("11:00") + "\"}");
        assertThat(moved.status()).as(moved.body()).isEqualTo(200);
        assertThat(moved.json().get("startAt").asString()).startsWith(at("11:00").substring(0, 16));
        assertThat(call("POST", "/api/v1/appointments/" + id + "/reschedule", reception, "{\"startAt\":\"" + at("20:00") + "\"}").status())
                .as("poza dyżurem").isEqualTo(422);

        assertThat(call("POST", "/api/v1/appointments/" + id + "/cancel", reception, null).status()).isEqualTo(204);
        assertThat(call("POST", "/api/v1/appointments/" + id + "/cancel", reception, null).status())
                .as("ponowne odwołanie").isEqualTo(409);
        assertThat(call("POST", "/api/v1/appointments/" + id + "/reschedule", reception, "{\"startAt\":\"" + at("12:00") + "\"}").status())
                .isEqualTo(422);
        assertThat(bookAsPatient("11:00").status()).as("slot zwolniony po odwołaniu").isEqualTo(201);
    }

    @Test
    void rescheduleOntoTakenSlotIsConflict() {
        String id = call("POST", "/api/v1/appointments", reception, bookBody("09:00", patient.patientId().toString()))
                .json().get("id").asString();
        PatientAccount other = newPatientAccount(tenant, "Trzeci", "Pacjent");
        call("POST", "/api/v1/appointments", other.token(), bookBody("10:00", null));
        assertThat(call("POST", "/api/v1/appointments/" + id + "/reschedule", reception, "{\"startAt\":\"" + at("10:00") + "\"}").status())
                .isEqualTo(409);
    }

    @Test
    void onlyReceptionistCancelsAndReschedules() {
        String id = bookAsPatient("09:00").json().get("id").asString();
        assertThat(call("POST", "/api/v1/appointments/" + id + "/cancel", patient.token(), null).status()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/appointments/" + id + "/cancel", admin, null).status()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/appointments/" + id + "/reschedule", patient.token(), "{\"startAt\":\"" + at("10:00") + "\"}").status())
                .isEqualTo(403);
    }

    @Test
    void freeSlotsExcludeBookedAndPastAndFollowStepAndDuration() {
        bookAsPatient("09:00");
        Resp slots = fetch("/api/v1/employees/" + doctorId + "/slots?serviceId=" + serviceId + "&from=" + day + "&to=" + day,
                patient.token());
        assertThat(slots.status()).isEqualTo(200);
        // wolne: [8:00,9:00) daje starty 8:00, 8:15, 8:30 (3) oraz [9:30,16:00) daje starty 9:30..15:30 (25)
        assertThat(slots.json()).hasSize(28);
        java.util.List<String> starts = new java.util.ArrayList<>();
        slots.json().forEach(n -> starts.add(n.get("start").asString()));
        assertThat(starts).doesNotContain(at("09:00"), at("09:15"), at("08:45")).contains(at("09:30"), at("08:30"));
        assertThat(fetch("/api/v1/employees/" + doctorId + "/slots?serviceId=" + serviceId + "&from=" + day + "&to=" + day.plusDays(40),
                patient.token()).status()).as("okno za duże").isEqualTo(400);
    }

    @Test
    void receptionScheduleListsStaffViewsWithPatientNamesAndFilters() {
        call("POST", "/api/v1/appointments", reception, bookBody("09:00", patient.patientId().toString()));
        String id = call("POST", "/api/v1/appointments", reception, bookBody("10:00", patient.patientId().toString()))
                .json().get("id").asString();
        call("POST", "/api/v1/appointments/" + id + "/cancel", reception, null);

        Resp all = fetch("/api/v1/reception/appointments?from=" + day + "&to=" + day, reception);
        assertThat(all.json()).hasSize(2);
        assertThat(all.json().get(0).get("patientName").asString()).isEqualTo("Ewa Pacjentka");
        assertThat(fetch("/api/v1/reception/appointments?from=" + day + "&to=" + day + "&status=SCHEDULED", reception).json()).hasSize(1);
        assertThat(fetch("/api/v1/reception/appointments?employeeId=" + UUID.randomUUID() + "&from=" + day + "&to=" + day, admin)
                .json()).as("administrator ma odczyt").isEmpty();
        assertThat(fetch("/api/v1/reception/appointments?from=" + day + "&to=" + day, patient.token()).status()).isEqualTo(403);
    }

    @Test
    void doctorSeesOwnScheduleWithAppointments() {
        call("POST", "/api/v1/appointments", reception, bookBody("09:00", patient.patientId().toString()));
        String doctorToken = loginWithNewPassword(tenant, doctorUserId);

        Resp mine = fetch("/api/v1/me/schedule?from=" + day + "&to=" + day, doctorToken);
        assertThat(mine.status()).as(mine.body()).isEqualTo(200);
        assertThat(mine.json().get("intervals")).hasSize(1);
        assertThat(mine.json().get("appointments")).hasSize(1);
        assertThat(mine.json().get("appointments").get(0).get("patientName").asString()).isEqualTo("Ewa Pacjentka");
        assertThat(fetch("/api/v1/me/schedule?from=" + day + "&to=" + day, patient.token()).status()).isEqualTo(403);
    }

    @Test
    void deactivatingDoctorWithFutureAppointmentsIsBlocked() {
        bookAsPatient("09:00");
        Resp r = call("POST", "/api/v1/admin/employees/" + doctorId + "/deactivate", admin, null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.body()).contains("employee-has-future-appointments");
    }

    @Test
    void appointmentsAreIsolatedBetweenTenants() {
        String id = bookAsPatient("09:00").json().get("id").asString();
        UUID other = newTenant();
        String otherReception = tokenFor(other, Role.RECEPTIONIST);
        assertThat(call("POST", "/api/v1/appointments/" + id + "/cancel", otherReception, null).status()).isEqualTo(404);
        assertThat(call("POST", "/api/v1/appointments/" + id + "/reschedule", otherReception, "{\"startAt\":\"" + at("10:00") + "\"}").status())
                .isEqualTo(404);
        assertThat(fetch("/api/v1/reception/appointments?from=" + day + "&to=" + day, otherReception).json()).isEmpty();
        assertThat(call("POST", "/api/v1/appointments", otherReception, bookBody("09:00", patient.patientId().toString())).status())
                .as("cudzy pacjent, usługa i wykonawca").isEqualTo(404);
    }
}
