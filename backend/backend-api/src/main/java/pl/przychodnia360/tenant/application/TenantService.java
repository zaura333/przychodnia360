package pl.przychodnia360.tenant.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.przychodnia360.tenant.api.TenantFacade;

@Service
class TenantService implements TenantFacade {

    private final TenantRepository repository;

    TenantService(TenantRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> resolveActiveSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return repository.resolveActiveSlug(slug);
    }

    @Override
    public Optional<String> name(UUID tenantId) {
        return repository.findName(tenantId);
    }
}
