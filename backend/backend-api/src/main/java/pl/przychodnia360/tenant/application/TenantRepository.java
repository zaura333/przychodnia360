package pl.przychodnia360.tenant.application;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {

    Optional<UUID> resolveActiveSlug(String slug);

    Optional<String> findName(UUID tenantId);
}
