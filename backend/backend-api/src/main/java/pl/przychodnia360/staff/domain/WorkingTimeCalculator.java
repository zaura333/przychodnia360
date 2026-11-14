package pl.przychodnia360.staff.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import pl.przychodnia360.common.time.ClinicTime;
import pl.przychodnia360.staff.api.TimeSlot;

/**
 * Wylicza przedziały pracy: grafik tygodniowy minus nieobecności plus dodatkowe zmiany, przeliczone na czas
 * bezwzględny przez {@link ClinicTime}, posortowane, a przedziały nakładające się lub stykające scalone.
 */
public final class WorkingTimeCalculator {

    private WorkingTimeCalculator() {
    }

    public static List<TimeSlot> compute(List<WorkSchedule> weekly, List<ScheduleException> exceptions,
            LocalDate from, LocalDate to) {
        List<TimeSlot> raw = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            final LocalDate date = day;
            boolean absent = exceptions.stream()
                    .anyMatch(e -> e.date().equals(date) && e.type() == ScheduleException.Type.ABSENCE);
            if (!absent) {
                weekly.stream().filter(w -> w.appliesOn(date)).forEach(w -> raw.add(
                        new TimeSlot(ClinicTime.toInstant(date, w.startTime()), ClinicTime.toInstant(date, w.endTime()))));
            }
            exceptions.stream()
                    .filter(e -> e.date().equals(date) && e.type() == ScheduleException.Type.EXTRA_SHIFT)
                    .forEach(e -> raw.add(new TimeSlot(ClinicTime.toInstant(date, e.startTime()),
                            ClinicTime.toInstant(date, e.endTime()))));
        }
        return merge(raw);
    }

    static List<TimeSlot> merge(List<TimeSlot> slots) {
        List<TimeSlot> sorted = slots.stream().sorted(Comparator.comparing(TimeSlot::start)).toList();
        List<TimeSlot> merged = new ArrayList<>();
        for (TimeSlot slot : sorted) {
            if (!merged.isEmpty() && !slot.start().isAfter(merged.getLast().end())) {
                TimeSlot last = merged.removeLast();
                merged.add(new TimeSlot(last.start(), slot.end().isAfter(last.end()) ? slot.end() : last.end()));
            } else {
                merged.add(slot);
            }
        }
        return merged;
    }
}
