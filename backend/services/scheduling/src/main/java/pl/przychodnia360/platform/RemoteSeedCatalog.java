package pl.przychodnia360.platform;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import pl.przychodnia360.common.http.RemoteServiceClient;

/** Seed katalogu przez REST do catalog-service; token administratora seedu jest przekazywany z kontekstu. */
@Component
class RemoteSeedCatalog implements SeedCatalog {

    private final RemoteServiceClient client;

    RemoteSeedCatalog(@Qualifier("catalogClient") RemoteServiceClient client) {
        this.client = client;
    }

    @Override
    @SuppressWarnings("unchecked")
    public UUID category(String name) {
        Map<String, Object> created = client.asCaller().post("/api/v1/admin/service-categories", Map.of("name", name), Map.class);
        return UUID.fromString((String) created.get("id"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public UUID service(UUID categoryId, String name, String description, BigDecimal price, int durationMinutes,
            String performerType) {
        Map<String, Object> created = client.asCaller().post("/api/v1/admin/services", Map.of("categoryId", categoryId.toString(),
                "name", name, "description", description, "price", price, "durationMinutes", durationMinutes,
                "performerType", performerType), Map.class);
        return UUID.fromString((String) created.get("id"));
    }
}
