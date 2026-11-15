package pl.przychodnia360.platform;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.przychodnia360.catalog.api.PerformerType;
import pl.przychodnia360.catalog.application.ServiceCatalogService;
import pl.przychodnia360.catalog.application.ServiceCatalogService.ServiceCommand;

/** Seed katalogu w module lokalnym (domyślnie). */
@Component
@ConditionalOnProperty(name = "p360.catalog.mode", havingValue = "local", matchIfMissing = true)
class LocalSeedCatalog implements SeedCatalog {

    private final ServiceCatalogService catalog;

    LocalSeedCatalog(ServiceCatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public UUID category(String name) {
        return catalog.createCategory(name).id();
    }

    @Override
    public UUID service(UUID categoryId, String name, String description, BigDecimal price, int durationMinutes,
            PerformerType performerType) {
        return catalog.create(new ServiceCommand(categoryId, name, description, price, durationMinutes, performerType)).id();
    }
}
