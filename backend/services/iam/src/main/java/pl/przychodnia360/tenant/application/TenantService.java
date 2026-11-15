package pl.przychodnia360.tenant.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.tenant.api.TenantFacade;

@Service
public class TenantService implements TenantFacade {

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

    @Override
    public Optional<TenantInfo> info(UUID tenantId) {
        return repository.findInfo(tenantId);
    }

    /** Operacje administratora platformy na placówkach (FR-43). */
    public List<TenantInfo> list() {
        return repository.findAll();
    }

    public TenantInfo create(String slug, String name) {
        TenantInfo tenant = new TenantInfo(Ids.newId(), slug, name.trim(), true);
        repository.insert(tenant);
        return tenant;
    }

    /** Zmiana nazwy i aktywności; null zostawia wartość bez zmian. */
    public TenantInfo update(UUID id, String name, Boolean active) {
        TenantInfo current = repository.findInfo(id).orElseThrow(() -> new NotFoundException("Nie znaleziono placówki."));
        TenantInfo updated = new TenantInfo(id, current.slug(), name == null ? current.name() : name.trim(),
                active == null ? current.active() : active);
        repository.update(updated);
        return updated;
    }
}
