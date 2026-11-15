package pl.przychodnia360.platform;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.appointments.application.AppointmentRepository;
import pl.przychodnia360.appointments.domain.Appointment;
import pl.przychodnia360.appointments.domain.AppointmentStatus;
import pl.przychodnia360.common.time.ClinicTime;
import pl.przychodnia360.shared.DemoIds;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.staff.api.EmployeeType;
import pl.przychodnia360.staff.api.TimeSlot;
import pl.przychodnia360.staff.application.ScheduleService;
import pl.przychodnia360.staff.application.ScheduleService.WeeklyEntry;
import pl.przychodnia360.staff.application.StaffRepository;
import pl.przychodnia360.staff.domain.Employee;

/**
 * Pracownicy, grafiki i kilka wizyt demonstracyjnych (profile local i demo). Konta pracowników i pacjentów zakłada
 * iam-service, katalog catalog-service; identyfikatory z {@link DemoIds}, więc seed nie wywołuje innych usług.
 * Dane fikcyjne (NFR-07); placówka z istniejącymi pracownikami jest pomijana.
 */
@Component
@Profile({"local", "demo"})
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true", matchIfMissing = true)
class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private record Clinic(String slug, String firstDoctor, String secondDoctor) {
    }

    private final StaffRepository staff;
    private final ScheduleService schedules;
    private final AppointmentRepository appointments;
    private final TransactionTemplate tx;
    private final Clock clock;

    SeedRunner(StaffRepository staff, ScheduleService schedules, AppointmentRepository appointments,
            TransactionTemplate tx, Clock clock) {
        this.staff = staff;
        this.schedules = schedules;
        this.appointments = appointments;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(new Clinic("klinika-a", "Anna Kowalska", "Piotr Nowak"));
        seed(new Clinic("klinika-b", "Maria Wiśniewska", "Tomasz Zieliński"));
    }

    private void seed(Clinic clinic) {
        String slug = clinic.slug();
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(DemoIds.tenant(slug)))) {
            if (tx.execute(s -> staff.findEmployee(DemoIds.employee(slug, "lekarz1")).isPresent())) {
                log.info("Seed: pracownicy placówki {} już istnieją, pomijam", slug);
                return;
            }
            tx.executeWithoutResult(s -> {
                employee(slug, "lekarz1", EmployeeType.DOCTOR, clinic.firstDoctor(), "Interna");
                employee(slug, "lekarz2", EmployeeType.DOCTOR, clinic.secondDoctor(), "Pediatria");
                employee(slug, "pielegniarka", EmployeeType.MEDICAL_STAFF, "Ewa Lis", null);
                employee(slug, "rejestracja", EmployeeType.RECEPTIONIST, "Rita Rejestr", null);
            });
            weekdays(DemoIds.employee(slug, "lekarz1"), "08:00", "16:00");
            weekdays(DemoIds.employee(slug, "lekarz2"), "12:00", "20:00");
            weekdays(DemoIds.employee(slug, "pielegniarka"), "07:00", "15:00");

            // Dwie wizyty pacjenta bez konta u pierwszego lekarza (usługa „Konsultacja lekarska”, 30 minut)
            LocalDate day = nextWorkday(clock.instant().atZone(ClinicTime.ZONE).toLocalDate().plusDays(1));
            tx.executeWithoutResult(s -> {
                for (int hour = 9; hour <= 10; hour++) {
                    Instant start = ClinicTime.toInstant(day, LocalTime.of(hour, 0));
                    appointments.insert(new Appointment(DemoIds.appointment(slug, day + "T" + hour),
                            DemoIds.patient(slug, "bez-konta"), DemoIds.employee(slug, "lekarz1"),
                            DemoIds.service(slug, "konsultacja"), new TimeSlot(start, start.plusSeconds(30 * 60)),
                            AppointmentStatus.SCHEDULED, DemoIds.user(slug, "rejestracja"), null, null, 0));
                }
            });
            log.info("Seed: pracownicy, grafiki i wizyty placówki {} utworzone", slug);
        }
    }

    private void employee(String slug, String login, EmployeeType type, String fullName, String specialization) {
        String[] name = fullName.split(" ", 2);
        staff.insertEmployee(new Employee(DemoIds.employee(slug, login), DemoIds.user(slug, login), type, name[0],
                name[1], specialization, true, 0));
    }

    private void weekdays(UUID employeeId, String from, String to) {
        List<WeeklyEntry> entries = new ArrayList<>();
        for (DayOfWeek day : new DayOfWeek[] {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            entries.add(new WeeklyEntry(day, LocalTime.parse(from), LocalTime.parse(to), LocalDate.of(2020, 1, 1), null));
        }
        schedules.defineWeekly(employeeId, entries);
    }

    private static LocalDate nextWorkday(LocalDate date) {
        LocalDate d = date;
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }
}
