package pl.przychodnia360.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.appointments.domain.Appointment;
import pl.przychodnia360.appointments.domain.AppointmentStatus;
import pl.przychodnia360.appointments.domain.BookingPolicy;
import pl.przychodnia360.catalog.api.CatalogFacade.ServiceInfo;
import pl.przychodnia360.catalog.api.PerformerType;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.UnprocessableException;
import pl.przychodnia360.staff.api.EmployeeType;
import pl.przychodnia360.staff.api.StaffFacade.EmployeeInfo;
import pl.przychodnia360.staff.api.TimeSlot;

class BookingPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private static TimeSlot slot(String start, String end) {
        return new TimeSlot(Instant.parse(start), Instant.parse(end));
    }

    private static EmployeeInfo employee(EmployeeType type, boolean active) {
        return new EmployeeInfo(UUID.randomUUID(), UUID.randomUUID(), type, "A", "B", null, active);
    }

    private static ServiceInfo service(PerformerType type, boolean active) {
        return new ServiceInfo(UUID.randomUUID(), "S", BigDecimal.TEN, 30, type, active);
    }

    @Test
    void slotMustBeInTheFuture() {
        assertThatThrownBy(() -> BookingPolicy.assertInFuture(NOW, NOW)).isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> BookingPolicy.assertInFuture(NOW.minusSeconds(1), NOW))
                .isInstanceOf(UnprocessableException.class);
        assertThatCode(() -> BookingPolicy.assertInFuture(NOW.plusSeconds(1), NOW)).doesNotThrowAnyException();
    }

    @Test
    void slotMustFitEntirelyInsideOneShift() {
        List<TimeSlot> shifts = List.of(slot("2026-11-16T07:00:00Z", "2026-11-16T11:00:00Z"));
        assertThatCode(() -> BookingPolicy.assertFitsInShift(slot("2026-11-16T07:00:00Z", "2026-11-16T07:30:00Z"), shifts))
                .doesNotThrowAnyException();
        assertThatCode(() -> BookingPolicy.assertFitsInShift(slot("2026-11-16T10:30:00Z", "2026-11-16T11:00:00Z"), shifts))
                .as("koniec dokładnie na końcu dyżuru").doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingPolicy.assertFitsInShift(slot("2026-11-16T10:45:00Z", "2026-11-16T11:15:00Z"), shifts))
                .isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> BookingPolicy.assertFitsInShift(slot("2026-11-16T12:00:00Z", "2026-11-16T12:30:00Z"), List.of()))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void performerMustMatchServiceAndBeActive() {
        assertThatCode(() -> BookingPolicy.assertPerformerMatches(employee(EmployeeType.DOCTOR, true),
                service(PerformerType.DOCTOR, true))).doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingPolicy.assertPerformerMatches(employee(EmployeeType.MEDICAL_STAFF, true),
                service(PerformerType.DOCTOR, true))).isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> BookingPolicy.assertPerformerMatches(employee(EmployeeType.RECEPTIONIST, true),
                service(PerformerType.DOCTOR, true))).isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> BookingPolicy.assertPerformerMatches(employee(EmployeeType.DOCTOR, false),
                service(PerformerType.DOCTOR, true))).isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> BookingPolicy.assertPerformerMatches(employee(EmployeeType.DOCTOR, true),
                service(PerformerType.DOCTOR, false))).isInstanceOf(UnprocessableException.class);
    }

    @Test
    void startMustBeOnTheSlotGrid() {
        assertThatCode(() -> BookingPolicy.assertOnGrid(Instant.parse("2026-11-16T07:15:00Z"), 15)).doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingPolicy.assertOnGrid(Instant.parse("2026-11-16T07:10:00Z"), 15))
                .isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> BookingPolicy.assertOnGrid(Instant.parse("2026-11-16T07:15:30Z"), 15))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void cancelAndRescheduleRulesOnTheAggregate() {
        UUID user = UUID.randomUUID();
        Appointment future = new Appointment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                slot("2026-11-16T07:00:00Z", "2026-11-16T07:30:00Z"), AppointmentStatus.SCHEDULED, user, null, null, 0);

        Appointment cancelled = future.cancel(user, NOW);
        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled.cancelledByUserId()).isEqualTo(user);
        assertThatThrownBy(() -> cancelled.cancel(user, NOW)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> future.cancel(user, Instant.parse("2026-11-16T07:00:00Z")))
                .as("wizyta rozpoczęta").isInstanceOf(UnprocessableException.class);

        Appointment moved = future.rescheduleTo(Instant.parse("2026-11-17T09:00:00Z"), NOW);
        assertThat(moved.slot().duration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(moved.startAt()).isEqualTo(Instant.parse("2026-11-17T09:00:00Z"));
        assertThatThrownBy(() -> cancelled.rescheduleTo(Instant.parse("2026-11-17T09:00:00Z"), NOW))
                .isInstanceOf(UnprocessableException.class);
    }
}
