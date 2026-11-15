package pl.przychodnia360.platform;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pl.przychodnia360.appointments.application.BookingService;
import pl.przychodnia360.appointments.application.BookingService.BookCommand;
import pl.przychodnia360.common.http.RemoteServiceClient;
import pl.przychodnia360.common.security.AccessTokenVerifier;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.common.time.ClinicTime;
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.shared.ServiceUnavailableException;
import pl.przychodnia360.shared.SubjectIds;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.staff.api.EmployeeType;
import pl.przychodnia360.staff.application.EmployeeService;
import pl.przychodnia360.staff.application.EmployeeService.CreateEmployee;
import pl.przychodnia360.staff.application.ScheduleService;
import pl.przychodnia360.staff.application.ScheduleService.WeeklyEntry;
import pl.przychodnia360.staff.domain.Employee;

/**
 * Część seedu demo należąca do scheduling (profile local i demo): katalog (przez catalog-service), pracownicy, grafiki, pacjent bez konta i
 * kilka wizyt w placówkach założonych przez seed iam. Seed loguje się w iam jako administrator placówki (hasło z
 * {@code seed.password}) i działa jego tokenem, jak administrator w interfejsie; udane logowanie oznacza, że część iam
 * (konta pracowników i pacjenci) jest gotowa. Pracownicy powstają sagą, która trafia w konta przygotowane przez iam
 * (deterministyczny identyfikator pracownika, {@link SubjectIds}). Placówka, w której są już pracownicy, jest pomijana.
 */
@Component
@Profile({"local", "demo"})
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true", matchIfMissing = true)
class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private record Clinic(String slug, String firstDoctor, String secondDoctor) {
    }

    private record LoginResponse(String accessToken) {
    }

    private final SeedCatalog catalog;
    private final SeedDependencies dependencies;
    private final RemoteServiceClient iam;
    private final AccessTokenVerifier verifier;
    private final EmployeeService employees;
    private final ScheduleService schedules;
    private final BookingService booking;
    private final PatientFacade patients;
    private final java.time.Clock clock;
    private final String password;
    private final int maxAttempts;
    private final Duration retryDelay;

    SeedRunner(SeedCatalog catalog, SeedDependencies dependencies, @Qualifier("iamClient") RemoteServiceClient iam,
            AccessTokenVerifier verifier, EmployeeService employees, ScheduleService schedules,
            BookingService booking, PatientFacade patients, java.time.Clock clock, @Value("${seed.password:}") String password,
            @Value("${seed.dependencies.max-attempts:40}") int maxAttempts,
            @Value("${seed.dependencies.retry-delay:PT3S}") Duration retryDelay) {
        this.catalog = catalog;
        this.dependencies = dependencies;
        this.iam = iam;
        this.verifier = verifier;
        this.employees = employees;
        this.schedules = schedules;
        this.booking = booking;
        this.patients = patients;
        this.clock = clock;
        this.password = password;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.warn("Seed pominięty: brak seed.password");
            return;
        }
        seed(new Clinic("klinika-a", "Anna Kowalska", "Piotr Nowak"));
        seed(new Clinic("klinika-b", "Maria Wiśniewska", "Tomasz Zieliński"));
    }

    private void seed(Clinic clinic) {
        String domain = clinic.slug() + ".example.test";
        String token = loginAsAdmin(clinic.slug(), "admin@" + domain);
        AuthenticatedUser admin = verifier.verify(token)
                .orElseThrow(() -> new IllegalStateException("Seed: token administratora z iam nie przechodzi weryfikacji"));
        UUID tenantId = admin.tenantId();
        // najpierw gotowość usług catalog i patient (bez tworzenia stanu), potem dane
        dependencies.await(tenantId);
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(tenantId))) {
            if (exists(SubjectIds.employee(tenantId, "lekarz1@" + domain))) {
                log.info("Seed: placówka {} ma już pracowników, pomijam", clinic.slug());
                return;
            }
            // seed działa jako zalogowany administrator placówki: jego token przekazuje klient katalogu
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(admin, token, List.of()));

            UUID consultations = catalog.category("Konsultacje");
            UUID diagnostics = catalog.category("Diagnostyka");
            UUID consult = catalog.service(consultations, "Konsultacja lekarska", "Konsultacja ogólna",
                    new BigDecimal("150.00"), 30, "DOCTOR");
            catalog.service(consultations, "Konsultacja kontrolna", "Wizyta kontrolna",
                    new BigDecimal("100.00"), 15, "DOCTOR");
            catalog.service(diagnostics, "USG", "Badanie ultrasonograficzne",
                    new BigDecimal("200.00"), 45, "DOCTOR");
            catalog.service(diagnostics, "Pobranie krwi", "Pobranie krwi żylnej",
                    new BigDecimal("40.00"), 15, "MEDICAL_STAFF");

            Employee first = employee(domain, "lekarz1", EmployeeType.DOCTOR, clinic.firstDoctor(), "Interna");
            Employee second = employee(domain, "lekarz2", EmployeeType.DOCTOR, clinic.secondDoctor(), "Pediatria");
            Employee nurse = employee(domain, "pielegniarka", EmployeeType.MEDICAL_STAFF, "Ewa Lis", null);
            Employee receptionist = employee(domain, "rejestracja", EmployeeType.RECEPTIONIST, "Rita Rejestr", null);
            weekdays(first.id(), "08:00", "16:00");
            weekdays(second.id(), "12:00", "20:00");
            weekdays(nurse.id(), "07:00", "15:00");

            // pacjent bez konta (rekord w patient-service, idempotentnie), dla niego są wizyty seedu
            UUID patientId = SubjectIds.patient(tenantId, "bez-konta@" + domain);
            patients.ensure(patientId, "Pacjent", "Bez Konta", LocalDate.of(1975, 6, 1), null, null);
            AuthenticatedUser reception = new AuthenticatedUser(receptionist.userId(), tenantId, Role.RECEPTIONIST,
                    receptionist.id(), null);
            LocalDate day = nextWorkday(clock.instant().atZone(ClinicTime.ZONE).toLocalDate().plusDays(1));
            for (int hour = 9; hour <= 10; hour++) {
                booking.book(new BookCommand(consult, first.id(), ClinicTime.toInstant(day, LocalTime.of(hour, 0)),
                        patientId), reception);
            }
            log.info("Seed: placówka {} uzupełniona w scheduling (katalog, pracownicy, grafiki, wizyty)", clinic.slug());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Logowanie administratora placówki w iam; ponawiane, dopóki seed iam nie skończy tej placówki. */
    private String loginAsAdmin(String slug, String email) {
        for (int attempt = 1;; attempt++) {
            try {
                LoginResponse response = iam.post("/api/v1/auth/login",
                        Map.of("tenantSlug", slug, "email", email, "password", password), LoginResponse.class);
                return response.accessToken();
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts) {
                    throw new IllegalStateException("Seed przerwany: nie udało się zalogować jako " + email + " w iam po "
                            + maxAttempts + " próbach (seed iam nie skończył albo inne seed.password)", e);
                }
                log.info("Seed: czekam na konto {} w iam (próba {}/{}): {}", email, attempt, maxAttempts, e.getMessage());
                sleep();
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Seed przerwany podczas oczekiwania na iam");
        }
    }

    private boolean exists(UUID employeeId) {
        try {
            employees.get(employeeId);
            return true;
        } catch (pl.przychodnia360.shared.NotFoundException e) {
            return false;
        }
    }

    private Employee employee(String domain, String login, EmployeeType type, String fullName, String specialization) {
        String[] name = fullName.split(" ", 2);
        return employees.create(new CreateEmployee(login + "@" + domain, type, name[0], name[1], specialization));
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
