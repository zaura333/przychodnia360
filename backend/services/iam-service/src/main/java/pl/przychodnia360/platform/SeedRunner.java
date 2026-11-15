package pl.przychodnia360.platform;

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
import pl.przychodnia360.identity.api.IdentityFacade;
import pl.przychodnia360.identity.application.PasswordHasher;
import pl.przychodnia360.identity.application.RegistrationService;
import pl.przychodnia360.identity.application.RegistrationService.Registration;
import pl.przychodnia360.identity.application.UserRepository;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.patients.application.PatientRepository;
import pl.przychodnia360.patients.domain.Patient;
import pl.przychodnia360.shared.DemoIds;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.tenant.api.TenantFacade;
import pl.przychodnia360.tenant.api.TenantFacade.TenantInfo;
import pl.przychodnia360.tenant.application.TenantRepository;

/**
 * Dane demonstracyjne iam-service (profile local i demo): placówki, konta (administrator, pracownicy, pacjenci) i
 * rekordy pacjentów. Katalog seeduje catalog-service, a pracowników, grafiki i wizyty scheduling-service; zgodność
 * identyfikatorów zapewnia {@link DemoIds}. Wszystkie dane są fikcyjne (NFR-07), adresy w domenie example.test, a
 * hasło wszystkich kont pochodzi z {@code seed.password}. Istniejąca placówka jest pomijana.
 */
@Component
@Profile({"local", "demo"})
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true", matchIfMissing = true)
class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private record Clinic(String slug, String name) {
    }

    /** Konta pracowników: login, rola. Rekordy pracowników zakłada scheduling-service z tymi samymi UUID. */
    private static final String[][] STAFF = {
        {"lekarz1", "DOCTOR"}, {"lekarz2", "DOCTOR"}, {"pielegniarka", "MEDICAL_STAFF"}, {"rejestracja", "RECEPTIONIST"},
    };

    private final TenantRepository tenantRepository;
    private final TenantFacade tenants;
    private final RegistrationService registration;
    private final PatientRepository patients;
    private final IdentityFacade identity;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TransactionTemplate tx;
    private final String password;

    SeedRunner(TenantRepository tenantRepository, TenantFacade tenants, RegistrationService registration,
            PatientRepository patients, IdentityFacade identity, UserRepository users, PasswordHasher hasher,
            TransactionTemplate tx, @Value("${seed.password:}") String password) {
        this.tenantRepository = tenantRepository;
        this.tenants = tenants;
        this.registration = registration;
        this.patients = patients;
        this.identity = identity;
        this.users = users;
        this.hasher = hasher;
        this.tx = tx;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.warn("Seed pominięty: brak seed.password");
            return;
        }
        seed(new Clinic("klinika-a", "Klinika Zdrowie (dane demo)"));
        seed(new Clinic("klinika-b", "Przychodnia Centrum (dane demo)"));
    }

    private void seed(Clinic clinic) {
        if (tenants.resolveActiveSlug(clinic.slug()).isPresent()) {
            log.info("Seed: placówka {} już istnieje, pomijam", clinic.slug());
            return;
        }
        UUID tenantId = DemoIds.tenant(clinic.slug());
        try (TenantContext.Handle ignored = TenantContext.openWithoutTenant()) {
            tx.executeWithoutResult(s -> tenantRepository.insert(new TenantInfo(tenantId, clinic.slug(), clinic.name(), true)));
        }
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(tenantId))) {
            String domain = clinic.slug() + ".example.test";
            String hash = hasher.hash(password);
            tx.executeWithoutResult(s -> {
                users.insert(User.create(DemoIds.user(clinic.slug(), "admin"), tenantId, "admin@" + domain,
                        Role.TENANT_ADMIN, hash));
                for (String[] member : STAFF) {
                    UUID userId = DemoIds.user(clinic.slug(), member[0]);
                    users.insert(User.create(userId, tenantId, member[0] + "@" + domain, Role.valueOf(member[1]), hash));
                    identity.linkEmployee(userId, DemoIds.employee(clinic.slug(), member[0]));
                }
            });

            String[][] people = {{"Jan", "Kowalski"}, {"Ewa", "Malinowska"}, {"Adam", "Wójcik"}};
            for (int i = 0; i < people.length; i++) {
                registration.registerPatient(new Registration(clinic.slug(), "pacjent" + (i + 1) + "@" + domain,
                        password, people[i][0], people[i][1], LocalDate.of(1980 + i * 7, 3 + i, 10 + i)));
            }
            tx.executeWithoutResult(s -> patients.insert(new Patient(DemoIds.patient(clinic.slug(), "bez-konta"), null,
                    "Pacjent", "Bez Konta", LocalDate.of(1975, 6, 1), null, 0)));
            log.info("Seed: placówka {} utworzona (konta: admin@{} i inne, hasło z seed.password)", clinic.slug(), domain);
        }
    }
}
