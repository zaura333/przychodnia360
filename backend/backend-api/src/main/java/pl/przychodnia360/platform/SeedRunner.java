package pl.przychodnia360.platform;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.appointments.application.BookingService;
import pl.przychodnia360.appointments.application.BookingService.BookCommand;
import pl.przychodnia360.catalog.api.PerformerType;
import pl.przychodnia360.catalog.application.ServiceCatalogService;
import pl.przychodnia360.catalog.application.ServiceCatalogService.ServiceCommand;
import pl.przychodnia360.common.time.ClinicTime;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.application.PasswordHasher;
import pl.przychodnia360.identity.application.RegistrationService;
import pl.przychodnia360.identity.application.RegistrationService.Registration;
import pl.przychodnia360.identity.application.UserRepository;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.staff.api.EmployeeType;
import pl.przychodnia360.staff.application.EmployeeService;
import pl.przychodnia360.staff.application.EmployeeService.CreateEmployee;
import pl.przychodnia360.staff.application.ScheduleService;
import pl.przychodnia360.staff.application.ScheduleService.WeeklyEntry;
import pl.przychodnia360.staff.domain.Employee;
import pl.przychodnia360.tenant.api.TenantFacade;
import pl.przychodnia360.tenant.application.TenantService;

/**
 * Dane demonstracyjne (profile local i demo): dwie placówki z administratorem, lekarzami, personelem, rejestratorem,
 * pacjentami, katalogiem, grafikami i kilkoma wizytami. Wszystkie dane są fikcyjne (NFR-07), adresy w domenie
 * example.test, a hasło wszystkich kont pochodzi z konfiguracji {@code seed.password}. Uruchamia się idempotentnie:
 * istniejąca placówka jest pomijana.
 */
@Component
@Profile({"local", "demo"})
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true", matchIfMissing = true)
class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private record Clinic(String slug, String name, String firstDoctor, String secondDoctor) {
    }

    private final TenantService tenantService;
    private final TenantFacade tenants;
    private final ServiceCatalogService catalog;
    private final EmployeeService employees;
    private final ScheduleService schedules;
    private final RegistrationService registration;
    private final PatientFacade patients;
    private final BookingService booking;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String password;

    SeedRunner(TenantService tenantService, TenantFacade tenants, ServiceCatalogService catalog,
            EmployeeService employees, ScheduleService schedules, RegistrationService registration,
            PatientFacade patients, BookingService booking, UserRepository users, PasswordHasher hasher,
            TransactionTemplate tx, Clock clock, @Value("${seed.password:}") String password) {
        this.tenantService = tenantService;
        this.tenants = tenants;
        this.catalog = catalog;
        this.employees = employees;
        this.schedules = schedules;
        this.registration = registration;
        this.patients = patients;
        this.booking = booking;
        this.users = users;
        this.hasher = hasher;
        this.tx = tx;
        this.clock = clock;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.warn("Seed pominięty: brak seed.password");
            return;
        }
        seed(new Clinic("klinika-a", "Klinika Zdrowie (dane demo)", "Anna Kowalska", "Piotr Nowak"));
        seed(new Clinic("klinika-b", "Przychodnia Centrum (dane demo)", "Maria Wiśniewska", "Tomasz Zieliński"));
    }

    private void seed(Clinic clinic) {
        if (tenants.resolveActiveSlug(clinic.slug()).isPresent()) {
            log.info("Seed: placówka {} już istnieje, pomijam", clinic.slug());
            return;
        }
        UUID tenantId;
        try (TenantContext.Handle ignored = TenantContext.openWithoutTenant()) {
            tenantId = tenantService.create(clinic.slug(), clinic.name()).id();
        }
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(tenantId))) {
            String domain = clinic.slug() + ".example.test";
            tx.executeWithoutResult(s -> users.insert(
                    User.create(Ids.newId(), tenantId, "admin@" + domain, Role.TENANT_ADMIN, hasher.hash(password))));

            UUID consultations = catalog.createCategory("Konsultacje").id();
            UUID diagnostics = catalog.createCategory("Diagnostyka").id();
            UUID consult = catalog.create(new ServiceCommand(consultations, "Konsultacja lekarska", "Konsultacja ogólna",
                    new BigDecimal("150.00"), 30, PerformerType.DOCTOR)).id();
            catalog.create(new ServiceCommand(consultations, "Konsultacja kontrolna", "Wizyta kontrolna",
                    new BigDecimal("100.00"), 15, PerformerType.DOCTOR));
            catalog.create(new ServiceCommand(diagnostics, "USG", "Badanie ultrasonograficzne",
                    new BigDecimal("200.00"), 45, PerformerType.DOCTOR));
            catalog.create(new ServiceCommand(diagnostics, "Pobranie krwi", "Pobranie krwi żylnej",
                    new BigDecimal("40.00"), 15, PerformerType.MEDICAL_STAFF));

            Employee first = employee(domain, "lekarz1", EmployeeType.DOCTOR, clinic.firstDoctor(), "Interna");
            Employee second = employee(domain, "lekarz2", EmployeeType.DOCTOR, clinic.secondDoctor(), "Pediatria");
            Employee nurse = employee(domain, "pielegniarka", EmployeeType.MEDICAL_STAFF, "Ewa Lis", null);
            Employee receptionist = employee(domain, "rejestracja", EmployeeType.RECEPTIONIST, "Rita Rejestr", null);
            weekdays(first.id(), "08:00", "16:00");
            weekdays(second.id(), "12:00", "20:00");
            weekdays(nurse.id(), "07:00", "15:00");

            List<UUID> patientIds = new ArrayList<>();
            String[][] people = {{"Jan", "Kowalski"}, {"Ewa", "Malinowska"}, {"Adam", "Wójcik"}};
            for (int i = 0; i < people.length; i++) {
                String email = "pacjent" + (i + 1) + "@" + domain;
                registration.registerPatient(new Registration(clinic.slug(), email, password, people[i][0],
                        people[i][1], LocalDate.of(1980 + i * 7, 3 + i, 10 + i)));
            }
            tx.executeWithoutResult(s -> patientIds.add(patients.create("Pacjent", "Bez Konta", LocalDate.of(1975, 6, 1), null)));

            AuthenticatedUser reception = new AuthenticatedUser(receptionist.userId(), tenantId, Role.RECEPTIONIST,
                    receptionist.id(), null);
            LocalDate day = nextWorkday(clock.instant().atZone(ClinicTime.ZONE).toLocalDate().plusDays(1));
            for (int hour = 9; hour <= 10; hour++) {
                booking.book(new BookCommand(consult, first.id(), ClinicTime.toInstant(day, LocalTime.of(hour, 0)),
                        patientIds.getFirst()), reception);
            }
            log.info("Seed: placówka {} utworzona (konta: admin@{} i inne, hasło z seed.password)", clinic.slug(), domain);
        }
    }

    private Employee employee(String domain, String login, EmployeeType type, String fullName, String specialization) {
        String[] name = fullName.split(" ", 2);
        Employee employee = employees.create(new CreateEmployee(login + "@" + domain, type, name[0], name[1],
                specialization));
        tx.executeWithoutResult(s -> users.findById(employee.userId()).ifPresent(u -> {
            u.changePassword(hasher.hash(password));
            users.update(u);
        }));
        return employee;
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
