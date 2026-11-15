package pl.przychodnia360.platform;

import java.math.BigDecimal;
import java.util.UUID;
import pl.przychodnia360.catalog.api.PerformerType;

/**
 * Zakładanie katalogu w seedzie: lokalnie (moduł w tym procesie) albo przez REST do catalog-service (S1, tryb zdalny),
 * żeby dane trafiły do bazy właściciela. Działa w kontekście zalogowanego administratora placówki seedu.
 */
interface SeedCatalog {

    UUID category(String name);

    UUID service(UUID categoryId, String name, String description, BigDecimal price, int durationMinutes,
            PerformerType performerType);
}
