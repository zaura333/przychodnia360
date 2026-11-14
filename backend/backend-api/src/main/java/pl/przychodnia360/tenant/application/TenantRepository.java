package pl.przychodnia360.tenant.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pl.przychodnia360.tenant.api.TenantFacade.TenantInfo;

public interface TenantRepository {

    Optional<UUID> resolveActiveSlug(String slug);

    Optional<String> findName(UUID tenantId);

    Optional<TenantInfo> findInfo(UUID tenantId);

    List<TenantInfo> findAll();

    /** Wstawia placówkę; zajęty slug to ConflictException (slug-taken). */
    void insert(TenantInfo tenant);

    void update(TenantInfo tenant);
}
