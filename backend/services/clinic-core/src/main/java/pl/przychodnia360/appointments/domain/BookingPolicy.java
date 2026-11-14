package pl.przychodnia360.appointments.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import pl.przychodnia360.catalog.api.CatalogFacade.ServiceInfo;
import pl.przychodnia360.shared.UnprocessableException;
import pl.przychodnia360.staff.api.StaffFacade.EmployeeInfo;
import pl.przychodnia360.staff.api.TimeSlot;

/** Reguły rezerwacji niezależne od bazy: przyszłość, dyżur, zgodność wykonawcy z usługą, siatka slotów. */
public final class BookingPolicy {

    private BookingPolicy() {
    }

    public static void assertInFuture(Instant start, Instant now) {
        if (!start.isAfter(now)) {
            throw new UnprocessableException("slot-in-past", "Termin wizyty musi być w przyszłości.");
        }
    }

    public static void assertFitsInShift(TimeSlot slot, List<TimeSlot> workingIntervals) {
        if (workingIntervals.stream().noneMatch(w -> w.contains(slot))) {
            throw new UnprocessableException("outside-working-hours", "Termin jest poza godzinami pracy wykonawcy.");
        }
    }

    public static void assertPerformerMatches(EmployeeInfo employee, ServiceInfo service) {
        if (!service.active()) {
            throw new UnprocessableException("service-inactive", "Usługa nie jest już dostępna.");
        }
        if (!employee.active()) {
            throw new UnprocessableException("performer-inactive", "Wykonawca nie jest już dostępny.");
        }
        if (!employee.type().canPerform(service.performerType())) {
            throw new UnprocessableException("performer-mismatch", "Ten wykonawca nie może wykonać wybranej usługi.");
        }
    }

    /** Początek wizyty musi leżeć na siatce slotów (pełne minuty, wielokrotność kroku). Krok dzieli godzinę. */
    public static void assertOnGrid(Instant start, int stepMinutes) {
        var utc = start.atOffset(ZoneOffset.UTC);
        if (utc.getSecond() != 0 || utc.getNano() != 0 || utc.getMinute() % stepMinutes != 0) {
            throw new UnprocessableException("slot-not-on-grid",
                    "Początek wizyty musi być wielokrotnością " + stepMinutes + " minut.");
        }
    }
}
