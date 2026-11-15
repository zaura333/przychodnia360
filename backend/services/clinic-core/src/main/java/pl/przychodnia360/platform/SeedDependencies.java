package pl.przychodnia360.platform;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.przychodnia360.catalog.api.CatalogFacade;
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.shared.ServiceUnavailableException;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;

/**
 * Czeka, aż usługi catalog i patient odpowiadają na wywołania z tokenem usługowym, ZANIM seed utworzy jakikolwiek stan.
 * Pierwsze wywołanie zdalne jest zimne (start JIT przy 0,5 vCPU, usługa pobiera JWKS z iam, który w tym czasie sam się
 * rozkręca), więc mieści się w limicie odczytu dopiero po rozgrzaniu; bez czekania seed pada w połowie, zostawia
 * założoną placówkę bez katalogu i pacjentów, a kolejny start pomija ją jako już istniejącą.
 * Sonda działa w placówce seedu (token usługowy iam wymaga istniejącej placówki), pyta o losowe identyfikatory (nic
 * nie zakłada, nie ma wyniku) i ponawia próby ze stałą przerwą.
 */
@Component
class SeedDependencies {

    private static final Logger log = LoggerFactory.getLogger(SeedDependencies.class);

    private final CatalogFacade catalog;
    private final PatientFacade patients;
    private final int maxAttempts;
    private final Duration retryDelay;

    SeedDependencies(CatalogFacade catalog, PatientFacade patients,
            @Value("${seed.dependencies.max-attempts:40}") int maxAttempts,
            @Value("${seed.dependencies.retry-delay:PT3S}") Duration retryDelay) {
        this.catalog = catalog;
        this.patients = patients;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    void await(UUID tenantId) {
        for (int attempt = 1;; attempt++) {
            try (TenantContext.Handle ignored = TenantContext.open(new TenantId(tenantId))) {
                catalog.service(UUID.randomUUID());
                patients.find(UUID.randomUUID());
                if (attempt > 1) {
                    log.info("Seed: usługi catalog i patient gotowe po {} próbach", attempt);
                }
                return;
            } catch (ServiceUnavailableException e) {
                if (attempt >= maxAttempts) {
                    throw new IllegalStateException("Seed przerwany przed utworzeniem danych: usługi catalog i patient nie "
                            + "odpowiadają po " + maxAttempts + " próbach (sprawdź ich logi i adresy P360_CATALOG_URL, "
                            + "P360_PATIENT_URL)", e);
                }
                log.info("Seed: czekam na usługi catalog i patient (próba {}/{}): {}", attempt, maxAttempts, e.getMessage());
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
