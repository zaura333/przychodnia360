package pl.przychodnia360.platform;

import java.time.Duration;
import java.time.LocalDate;
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
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.application.PasswordHasher;
import pl.przychodnia360.identity.application.RegistrationService;
import pl.przychodnia360.identity.application.RegistrationService.Registration;
import pl.przychodnia360.identity.application.UserRepository;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.patients.api.PatientRegistry;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.ServiceUnavailableException;
import pl.przychodnia360.shared.SubjectIds;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.tenant.api.TenantFacade;
import pl.przychodnia360.tenant.application.TenantService;

/**
 * Część seedu demo należąca do iam (profile local i demo): dwie placówki, pacjenci z kontami (saga rejestracji do
 * patient-service), konta pracowników z hasłem i administrator placówki. Dane fikcyjne (NFR-07),
 * hasło z {@code seed.password}. Konta pracowników mają deterministyczny identyfikator pracownika
 * ({@link SubjectIds#employee}), więc seed scheduling zakłada pracowników sagą, która trafia w te same konta; tak samo
 * identyfikatory pacjentów ({@link SubjectIds#patient}). Administrator jest zakładany na końcu: udane logowanie
 * administratora oznacza dla seedu scheduling, że część iam jest kompletna. Istniejąca placówka jest pomijana.
 */
@Component
@Profile({"local", "demo"})
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true", matchIfMissing = true)
class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    /** Placówki seedu (wspólne z seedem scheduling: slug, nazwa i loginy pracowników). */
    record Clinic(String slug, String name) {
    }

    private record Staff(String login, Role role) {
    }

    private static final Staff[] STAFF = {
        new Staff("lekarz1", Role.DOCTOR), new Staff("lekarz2", Role.DOCTOR),
        new Staff("pielegniarka", Role.MEDICAL_STAFF), new Staff("rejestracja", Role.RECEPTIONIST)};

    private final TenantService tenantService;
    private final TenantFacade tenants;
    private final RegistrationService registration;
    private final PatientRegistry patients;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TransactionTemplate tx;
    private final String password;
    private final int maxAttempts;
    private final Duration retryDelay;

    SeedRunner(TenantService tenantService, TenantFacade tenants, RegistrationService registration,
            PatientRegistry patients, UserRepository users, PasswordHasher hasher, TransactionTemplate tx,
            @Value("${seed.password:}") String password,
            @Value("${seed.dependencies.max-attempts:40}") int maxAttempts,
            @Value("${seed.dependencies.retry-delay:PT3S}") Duration retryDelay) {
        this.tenantService = tenantService;
        this.tenants = tenants;
        this.registration = registration;
        this.patients = patients;
        this.users = users;
        this.hasher = hasher;
        this.tx = tx;
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
        awaitPatientService();
        seed(new Clinic("klinika-a", "Klinika Zdrowie (dane demo)"));
        seed(new Clinic("klinika-b", "Przychodnia Centrum (dane demo)"));
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
        String domain = clinic.slug() + ".example.test";
        String[][] people = {{"Jan", "Kowalski"}, {"Ewa", "Malinowska"}, {"Adam", "Wójcik"}};
        for (int i = 0; i < people.length; i++) {
            registration.registerPatient(new Registration(clinic.slug(), "pacjent" + (i + 1) + "@" + domain, password,
                    people[i][0], people[i][1], LocalDate.of(1980 + i * 7, 3 + i, 10 + i)));
        }
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(tenantId))) {
            String hash = hasher.hash(password);
            for (Staff staff : STAFF) {
                String email = staff.login() + "@" + domain;
                User user = User.create(Ids.newId(), tenantId, email, staff.role(), hash);
                user.linkEmployee(SubjectIds.employee(tenantId, email));
                tx.executeWithoutResult(s -> users.insert(user));
            }
            User admin = User.create(Ids.newId(), tenantId, "admin@" + domain, Role.TENANT_ADMIN, hash);
            tx.executeWithoutResult(s -> users.insert(admin));
        }
        log.info("Seed: placówka {} utworzona w iam (konta: admin@{} i inne, hasło z seed.password)", clinic.slug(),
                domain);
    }

    /** Saga rejestracji woła patient-service: zanim powstanie placówka, usługa musi odpowiadać (zimny start). */
    private void awaitPatientService() {
        for (int attempt = 1;; attempt++) {
            try (TenantContext.Handle ignored = TenantContext.open(new TenantId(UUID.randomUUID()))) {
                patients.find(UUID.randomUUID());
                return;
            } catch (ServiceUnavailableException e) {
                if (attempt >= maxAttempts) {
                    throw new IllegalStateException("Seed przerwany przed utworzeniem danych: usługa patient nie odpowiada "
                            + "po " + maxAttempts + " próbach (sprawdź jej logi i P360_PATIENT_URL)", e);
                }
                log.info("Seed: czekam na usługę patient (próba {}/{}): {}", attempt, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(retryDelay.toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Seed przerwany podczas oczekiwania na usługi", interrupted);
                }
            }
        }
    }
}
