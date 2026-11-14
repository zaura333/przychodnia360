package pl.przychodnia360.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.common.time.ClinicTime;
import pl.przychodnia360.staff.api.TimeSlot;
import pl.przychodnia360.staff.domain.ScheduleException;
import pl.przychodnia360.staff.domain.ScheduleException.Type;
import pl.przychodnia360.staff.domain.WorkSchedule;
import pl.przychodnia360.staff.domain.WorkingTimeCalculator;

class WorkingTimeCalculatorTest {

    private static final UUID EMP = UUID.randomUUID();

    private static WorkSchedule weekly(DayOfWeek day, String from, String to, String validFrom, String validTo) {
        return new WorkSchedule(UUID.randomUUID(), EMP, day, LocalTime.parse(from), LocalTime.parse(to),
                LocalDate.parse(validFrom), validTo == null ? null : LocalDate.parse(validTo));
    }

    private static ScheduleException exception(String date, Type type, String from, String to) {
        return new ScheduleException(UUID.randomUUID(), EMP, LocalDate.parse(date), type,
                from == null ? null : LocalTime.parse(from), to == null ? null : LocalTime.parse(to));
    }

    @Test
    void weeklyScheduleProducesIntervalsOnMatchingDaysOnly() {
        List<WorkSchedule> weekly = List.of(weekly(DayOfWeek.MONDAY, "08:00", "12:00", "2026-01-01", null));
        // 2026-11-16 to poniedziałek (czas zimowy, UTC+1)
        List<TimeSlot> slots = WorkingTimeCalculator.compute(weekly, List.of(), LocalDate.parse("2026-11-16"),
                LocalDate.parse("2026-11-22"));
        assertThat(slots).containsExactly(new TimeSlot(Instant.parse("2026-11-16T07:00:00Z"),
                Instant.parse("2026-11-16T11:00:00Z")));
    }

    @Test
    void validityPeriodLimitsTheSchedule() {
        List<WorkSchedule> weekly = List.of(weekly(DayOfWeek.MONDAY, "08:00", "12:00", "2026-11-23", "2026-11-23"));
        assertThat(WorkingTimeCalculator.compute(weekly, List.of(), LocalDate.parse("2026-11-16"),
                LocalDate.parse("2026-11-30"))).hasSize(1);
    }

    @Test
    void absenceRemovesTheDayAndExtraShiftAddsInterval() {
        List<WorkSchedule> weekly = List.of(weekly(DayOfWeek.MONDAY, "08:00", "12:00", "2026-01-01", null));
        List<ScheduleException> exceptions = List.of(
                exception("2026-11-16", Type.ABSENCE, null, null),
                exception("2026-11-21", Type.EXTRA_SHIFT, "09:00", "13:00"));
        List<TimeSlot> slots = WorkingTimeCalculator.compute(weekly, exceptions, LocalDate.parse("2026-11-16"),
                LocalDate.parse("2026-11-22"));
        assertThat(slots).containsExactly(new TimeSlot(Instant.parse("2026-11-21T08:00:00Z"),
                Instant.parse("2026-11-21T12:00:00Z")));
    }

    @Test
    void overlappingAndAdjacentIntervalsAreMerged() {
        List<WorkSchedule> weekly = List.of(weekly(DayOfWeek.MONDAY, "08:00", "12:00", "2026-01-01", null));
        List<ScheduleException> exceptions = List.of(exception("2026-11-16", Type.EXTRA_SHIFT, "12:00", "14:00"),
                exception("2026-11-16", Type.EXTRA_SHIFT, "13:00", "15:00"));
        assertThat(WorkingTimeCalculator.compute(weekly, exceptions, LocalDate.parse("2026-11-16"),
                LocalDate.parse("2026-11-16"))).containsExactly(new TimeSlot(Instant.parse("2026-11-16T07:00:00Z"),
                Instant.parse("2026-11-16T14:00:00Z")));
    }

    @Test
    void localWorkingHoursStayTheSameAcrossDaylightSavingChange() {
        // 2026-03-29 (niedziela): przejście na czas letni; 2026-03-30 (poniedziałek) już UTC+2
        List<WorkSchedule> weekly = List.of(
                weekly(DayOfWeek.SUNDAY, "08:00", "16:00", "2026-01-01", null),
                weekly(DayOfWeek.MONDAY, "08:00", "16:00", "2026-01-01", null));
        List<TimeSlot> slots = WorkingTimeCalculator.compute(weekly, List.of(), LocalDate.parse("2026-03-28"),
                LocalDate.parse("2026-03-30"));
        assertThat(slots).containsExactly(
                new TimeSlot(Instant.parse("2026-03-29T06:00:00Z"), Instant.parse("2026-03-29T14:00:00Z")),
                new TimeSlot(Instant.parse("2026-03-30T06:00:00Z"), Instant.parse("2026-03-30T14:00:00Z")));
    }

    @Test
    void clinicTimeHandlesGapAndOverlapAndDayLength() {
        // luka: 02:30 nie istnieje 2026-03-29, przesuwa się do 03:30 (01:30Z)
        assertThat(ClinicTime.toInstant(LocalDate.parse("2026-03-29"), LocalTime.parse("02:30")))
                .isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
        // powtórzona godzina 02:30 dnia 2026-10-25 oznacza wcześniejsze wystąpienie (00:30Z)
        assertThat(ClinicTime.toInstant(LocalDate.parse("2026-10-25"), LocalTime.parse("02:30")))
                .isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
        assertThat(Duration.between(ClinicTime.startOfDay(LocalDate.parse("2026-03-29")),
                ClinicTime.endOfDay(LocalDate.parse("2026-03-29")))).isEqualTo(Duration.ofHours(23));
        assertThat(Duration.between(ClinicTime.startOfDay(LocalDate.parse("2026-10-25")),
                ClinicTime.endOfDay(LocalDate.parse("2026-10-25")))).isEqualTo(Duration.ofHours(25));
    }
}
