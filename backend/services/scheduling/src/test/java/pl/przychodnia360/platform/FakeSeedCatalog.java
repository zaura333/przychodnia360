package pl.przychodnia360.platform;

import java.math.BigDecimal;
import java.util.UUID;
import pl.przychodnia360.FakeCatalog;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.TenantContext;

/** Atrapa {@code SeedCatalog} w testach: seed zakłada usługi w {@link FakeCatalog} zamiast wołać catalog-service. */
public class FakeSeedCatalog implements SeedCatalog {

    private final FakeCatalog catalog;

    public FakeSeedCatalog(FakeCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public UUID category(String name) {
        return Ids.newId();
    }

    @Override
    public UUID service(UUID categoryId, String name, String description, BigDecimal price, int durationMinutes,
            String performerType) {
        return catalog.add(TenantContext.requireTenant().value(), name, price, durationMinutes, performerType);
    }
}
