package pl.przychodnia360.platform;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Zakładanie katalogu w seedzie przez REST do catalog-service (S1), żeby dane trafiły do bazy właściciela. Działa w
 * kontekście zalogowanego administratora placówki seedu. W testach podstawiana jest atrapa.
 */
interface SeedCatalog {

    UUID category(String name);

    UUID service(UUID categoryId, String name, String description, BigDecimal price, int durationMinutes,
            String performerType);
}
