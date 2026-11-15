package pl.przychodnia360.platform;

import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.catalog.application.CatalogRepository;
import pl.przychodnia360.catalog.domain.MedicalService;
import pl.przychodnia360.catalog.domain.ServiceCategory;
import pl.przychodnia360.shared.DemoIds;
import pl.przychodnia360.shared.PerformerType;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;

/**
 * Katalog demonstracyjny (profile local i demo) dla placówek zakładanych przez seed iam-service; identyfikatory
 * z {@link DemoIds}, więc scheduling-service może od razu rezerwować usługę „Konsultacja lekarska”. Dane fikcyjne
 * (NFR-07); placówka z istniejącym katalogiem jest pomijana.
 */
@Component
@Profile({"local", "demo"})
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true", matchIfMissing = true)
class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final CatalogRepository repository;
    private final TransactionTemplate tx;

    SeedRunner(CatalogRepository repository, TransactionTemplate tx) {
        this.repository = repository;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("klinika-a");
        seed("klinika-b");
    }

    private void seed(String slug) {
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(DemoIds.tenant(slug)))) {
            tx.executeWithoutResult(s -> {
                UUID consultations = DemoIds.category(slug, "konsultacje");
                if (repository.findCategory(consultations).isPresent()) {
                    log.info("Seed: katalog placówki {} już istnieje, pomijam", slug);
                    return;
                }
                UUID diagnostics = DemoIds.category(slug, "diagnostyka");
                repository.insertCategory(new ServiceCategory(consultations, "Konsultacje", 0));
                repository.insertCategory(new ServiceCategory(diagnostics, "Diagnostyka", 0));
                service(slug, "konsultacja", consultations, "Konsultacja lekarska", "Konsultacja ogólna", "150.00", 30,
                        PerformerType.DOCTOR);
                service(slug, "kontrola", consultations, "Konsultacja kontrolna", "Wizyta kontrolna", "100.00", 15,
                        PerformerType.DOCTOR);
                service(slug, "usg", diagnostics, "USG", "Badanie ultrasonograficzne", "200.00", 45,
                        PerformerType.DOCTOR);
                service(slug, "pobranie-krwi", diagnostics, "Pobranie krwi", "Pobranie krwi żylnej", "40.00", 15,
                        PerformerType.MEDICAL_STAFF);
                log.info("Seed: katalog placówki {} utworzony", slug);
            });
        }
    }

    private void service(String slug, String key, UUID category, String name, String description, String price,
            int minutes, PerformerType performer) {
        repository.insertService(new MedicalService(DemoIds.service(slug, key), category, name, description,
                new BigDecimal(price), minutes, performer, true, 0));
    }
}
